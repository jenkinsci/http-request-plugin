package jenkins.plugins.http_request;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContextBuilder;

import com.google.common.collect.Range;

import hudson.AbortException;
import hudson.CloseProofOutputStream;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.remoting.RemoteOutputStream;
import jenkins.security.MasterToSlaveCallable;

import jenkins.plugins.http_request.HttpRequest.DescriptorImpl;
import jenkins.plugins.http_request.auth.Authenticator;
import jenkins.plugins.http_request.util.HttpClientUtil;
import jenkins.plugins.http_request.util.HttpRequestNameValuePair;
import jenkins.plugins.http_request.util.RequestAction;

/**
 * @author Janario Oliveira
 */
public class HttpRequestExecution extends MasterToSlaveCallable<ResponseContentSupplier, RuntimeException> {

	private static final long serialVersionUID = -2066857816168989599L;
	private final String url;
	private final HttpMode httpMode;
	private final boolean ignoreSslErrors;

	private final String body;
	private final List<HttpRequestNameValuePair> headers;

	private final String validResponseCodes;
	private final String validResponseContent;
	private final FilePath outputFile;
	private final int timeout;
	private final boolean consoleLogResponseBody;

	private final Authenticator authenticator;

	private final OutputStream remoteLogger;
	private transient PrintStream localLogger;

	static HttpRequestExecution from(HttpRequest http,
									 EnvVars envVars, AbstractBuild<?, ?> build, TaskListener taskListener) {
		try {
			String url = http.resolveUrl(envVars, build, taskListener);
			String body = http.resolveBody(envVars, build, taskListener);
			List<HttpRequestNameValuePair> headers = http.resolveHeaders(envVars);

			FilePath outputFile = http.resolveOutputFile(envVars, build);

			return new HttpRequestExecution(
					url, http.getHttpMode(), http.getIgnoreSslErrors(),
					body, headers, http.getTimeout(),
					http.getAuthentication(),

					http.getValidResponseCodes(), http.getValidResponseContent(),
					http.getConsoleLogResponseBody(), outputFile,

					taskListener.getLogger());
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	static HttpRequestExecution from(HttpRequestStep step, TaskListener taskListener) {
		List<HttpRequestNameValuePair> headers = step.resolveHeaders();

		return new HttpRequestExecution(
				step.getUrl(), step.getHttpMode(), step.isIgnoreSslErrors(),
				step.getRequestBody(), headers, step.getTimeout(),
				step.getAuthentication(),

				step.getValidResponseCodes(), step.getValidResponseContent(),
				step.getConsoleLogResponseBody(), null,

				taskListener.getLogger());
	}

	private HttpRequestExecution(
			String url, HttpMode httpMode, boolean ignoreSslErrors,
			String body, List<HttpRequestNameValuePair> headers, Integer timeout,
			String authentication,

			String validResponseCodes, String validResponseContent,
			Boolean consoleLogResponseBody, FilePath outputFile,
			PrintStream logger
	) {
		this.url = url;
		this.httpMode = httpMode;
		this.ignoreSslErrors = ignoreSslErrors;

		this.body = body;
		this.headers = headers;
		this.timeout = timeout != null ? timeout : -1;
		if (authentication != null && !authentication.isEmpty()) {
			authenticator = HttpRequestGlobalConfig.get().getAuthentication(authentication);
			if (authenticator == null) {
				throw new IllegalStateException("Authentication '" + authentication + "' doesn't exist anymore");
			}
		} else {
			authenticator = null;
		}

		this.validResponseCodes = validResponseCodes;
		this.validResponseContent = validResponseContent != null ? validResponseContent : "";
		this.consoleLogResponseBody = Boolean.TRUE.equals(consoleLogResponseBody);
		this.outputFile = outputFile;

		this.localLogger = logger;
		this.remoteLogger = new RemoteOutputStream(new CloseProofOutputStream(logger));
	}

	@Override
	public ResponseContentSupplier call() throws RuntimeException {
		try {
			logger().println("HttpMethod: " + httpMode);
			logger().println("URL: " + url);
			for (HttpRequestNameValuePair header : headers) {
				logger().print(header.getName() + ": ");
				logger().println(header.getMaskValue() ? "*****" : header.getValue());
			}

			ResponseContentSupplier response = authAndRequest();
			responseCodeIsValid(response);
			contentIsValid(response);
			logResponseToFile(response);

			return response;
		} catch (IOException | InterruptedException |
				KeyStoreException | NoSuchAlgorithmException | KeyManagementException e) {
			throw new IllegalStateException(e);
		}
	}

	private PrintStream logger() {
		if (localLogger == null) {
			localLogger = new PrintStream(remoteLogger);
		}
		return localLogger;
	}

	private ResponseContentSupplier authAndRequest()
			throws IOException, InterruptedException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
		CloseableHttpClient httpclient = null;
		try {
			HttpClientBuilder clientBuilder = HttpClientBuilder.create().useSystemProperties();
			//timeout
			if (timeout > 0) {
				int t = timeout * 1000;
				RequestConfig config = RequestConfig.custom()
						.setSocketTimeout(t)
						.setConnectTimeout(t)
						.setConnectionRequestTimeout(t)
						.build();
				clientBuilder.setDefaultRequestConfig(config);
			}
			//ssl
			if (ignoreSslErrors) {
				SSLContextBuilder builder = SSLContextBuilder.create();
				builder.loadTrustMaterial(null, new TrustStrategy() {
					@Override
					public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
						return true;
					}
				});
				SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build(), NoopHostnameVerifier.INSTANCE);
				clientBuilder.setSSLSocketFactory(sslsf);
			}

			HttpClientUtil clientUtil = new HttpClientUtil();
			HttpRequestBase httpRequestBase = clientUtil.createRequestBase(new RequestAction(new URL(url), httpMode, body, null, headers));
			HttpContext context = new BasicHttpContext();

			httpclient = auth(clientBuilder, httpRequestBase, context);
			return executeRequest(httpclient, clientUtil, httpRequestBase, context);
		} finally {
			if (httpclient != null) {
				httpclient.close();
			}
		}
	}

	private CloseableHttpClient auth(
			HttpClientBuilder clientBuilder, HttpRequestBase httpRequestBase,
			HttpContext context) throws IOException, InterruptedException {
		if (authenticator == null) {
			return clientBuilder.build();
		}

		logger().println("Using authentication: " + authenticator.getKeyName());
		return authenticator.authenticate(clientBuilder, context, httpRequestBase, logger());
	}

	private ResponseContentSupplier executeRequest(
			CloseableHttpClient httpclient, HttpClientUtil clientUtil, HttpRequestBase httpRequestBase,
			HttpContext context) throws IOException, InterruptedException {
		ResponseContentSupplier responseContentSupplier;
		try {
			final HttpResponse response = clientUtil.execute(httpclient, context, httpRequestBase, logger());
			// The HttpEntity is consumed by the ResponseContentSupplier
			responseContentSupplier = new ResponseContentSupplier(response);
		} catch (UnknownHostException uhe) {
			logger().println("Treating UnknownHostException(" + uhe.getMessage() + ") as 404 Not Found");
			responseContentSupplier = new ResponseContentSupplier("UnknownHostException as 404 Not Found", 404);
		} catch (SocketTimeoutException | ConnectException ce) {
			logger().println("Treating " + ce.getClass() + "(" + ce.getMessage() + ") as 408 Request Timeout");
			responseContentSupplier = new ResponseContentSupplier(ce.getClass() + "(" + ce.getMessage() + ") as 408 Request Timeout", 408);
		}

		if (consoleLogResponseBody) {
			logger().println("Response: \n" + responseContentSupplier.getContent());
		}
		return responseContentSupplier;
	}

	private void contentIsValid(ResponseContentSupplier response) throws AbortException {
		if (validResponseContent.isEmpty()) {
			return;
		}

		String content = response.getContent();
		if (!content.contains(validResponseContent)) {
			throw new AbortException("Fail: Response doesn't contain expected content '" + validResponseContent + "'");
		}
	}

	private void responseCodeIsValid(ResponseContentSupplier response) throws AbortException {
		List<Range<Integer>> ranges = DescriptorImpl.parseToRange(validResponseCodes);
		for (Range<Integer> range : ranges) {
			if (range.contains(response.getStatus())) {
				logger().println("Success code from " + range);
				return;
			}
		}
		throw new AbortException("Fail: the returned code " + response.getStatus() + " is not in the accepted range: " + ranges);
	}

	private void logResponseToFile(ResponseContentSupplier response)
			throws IOException, InterruptedException {
		if (outputFile == null || response.getContent() == null) {
			return;
		}

		logger().println("Saving response body to " + outputFile);
		OutputStreamWriter write = null;
		try {
			write = new OutputStreamWriter(outputFile.write(), StandardCharsets.UTF_8);
			write.write(response.getContent());
		} finally {
			if (write != null) {
				write.close();
			}
		}
	}
}
