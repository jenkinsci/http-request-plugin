package jenkins.plugins.http_request;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.stream.IntStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;

import hudson.AbortException;
import hudson.CloseProofOutputStream;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.RemoteOutputStream;
import hudson.security.ACL;
import jenkins.security.MasterToSlaveCallable;

import jenkins.plugins.http_request.HttpRequest.DescriptorImpl;
import jenkins.plugins.http_request.HttpRequestStep.Execution;
import jenkins.plugins.http_request.auth.Authenticator;
import jenkins.plugins.http_request.auth.CertificateAuthentication;
import jenkins.plugins.http_request.auth.CredentialBasicAuthentication;
import jenkins.plugins.http_request.auth.CredentialNtlmAuthentication;
import jenkins.plugins.http_request.util.HttpClientUtil;
import jenkins.plugins.http_request.util.HttpRequestFormDataPart;
import jenkins.plugins.http_request.util.HttpRequestNameValuePair;
import jenkins.plugins.http_request.util.RequestAction;

/**
 * @author Janario Oliveira
 */
public class HttpRequestExecution
		extends MasterToSlaveCallable<ResponseContentSupplier, RuntimeException> {

	private static final long serialVersionUID = -2066857816168989599L;
	private final String url;
	private final HttpMode httpMode;
	private final boolean ignoreSslErrors;
	private final HttpHost httpProxy;
	private final StandardUsernamePasswordCredentials proxyCredentials;

	private final String body;
	private final List<HttpRequestNameValuePair> headers;
	private final List<HttpRequestFormDataPart> formData;

	private final FilePath uploadFile;
	private final String multipartName;
	private final boolean wrapAsMultipart;

	private final boolean useNtlm;
	private final boolean useSystemProperties;
	private final String validResponseCodes;
	private final String validResponseContent;
	private final FilePath outputFile;
	private final int timeout;
	private final boolean consoleLogResponseBody;
	private final ResponseHandle responseHandle;

	private final Authenticator authenticator;

	private final OutputStream remoteLogger;
	private transient PrintStream localLogger;

	static HttpRequestExecution from(HttpRequest http, EnvVars envVars, AbstractBuild<?, ?> build,
			TaskListener taskListener) {
		try {
			String url = http.resolveUrl(envVars, build, taskListener);
			String body = http.resolveBody(envVars, build, taskListener);
			List<HttpRequestNameValuePair> headers = http.resolveHeaders(envVars);

			FilePath outputFile = http.resolveOutputFile(envVars, build);
			FilePath uploadFile = http.resolveUploadFile(envVars, build);
			Item project = build.getProject();
			Run<?, ?> run = build;

			List<HttpRequestFormDataPart> formData = http.resolveFormDataParts(envVars, build);

			return new HttpRequestExecution(url, http.getHttpMode(), http.getIgnoreSslErrors(),
					http.getHttpProxy(), http.getProxyAuthentication(), body, headers,
					http.getTimeout(), uploadFile, http.getMultipartName(),
					http.getWrapAsMultipart(), http.getAuthentication(), http.isUseNtlm(),
					http.getUseSystemProperties(), formData,

					http.getValidResponseCodes(), http.getValidResponseContent(),
					http.getConsoleLogResponseBody(), outputFile, ResponseHandle.NONE,

					project, run, taskListener.getLogger());
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	static HttpRequestExecution from(HttpRequestStep step, TaskListener taskListener,
			Execution execution) throws IOException, InterruptedException {
		List<HttpRequestNameValuePair> headers = step.resolveHeaders();
		FilePath outputFile = execution.resolveOutputFile();
		FilePath uploadFile = execution.resolveUploadFile();
		List<HttpRequestFormDataPart> formData = execution.resolveFormDataParts();

		// TODO: resolveFormDataParts missing
		Item project = execution.getProject();
		Run<?, ?> run = execution.getContext().get(Run.class);

		return new HttpRequestExecution(step.getUrl(), step.getHttpMode(), step.isIgnoreSslErrors(),
				step.getHttpProxy(), step.getProxyAuthentication(), step.getRequestBody(), headers,
				step.getTimeout(), uploadFile, step.getMultipartName(), step.isWrapAsMultipart(),
				step.getAuthentication(), step.isUseNtlm(), step.getUseSystemProperties(), formData,

				step.getValidResponseCodes(), step.getValidResponseContent(),
				step.getConsoleLogResponseBody(), outputFile, step.getResponseHandle(), project,
				run, taskListener.getLogger());
	}

	private HttpRequestExecution(String url, HttpMode httpMode, boolean ignoreSslErrors,
			String httpProxy, String proxyAuthentication, String body,
			List<HttpRequestNameValuePair> headers, Integer timeout, FilePath uploadFile,
			String multipartName, boolean wrapAsMultipart, String authentication, boolean useNtlm,
			boolean useSystemProperties, List<HttpRequestFormDataPart> formData,

			String validResponseCodes, String validResponseContent, Boolean consoleLogResponseBody,
			FilePath outputFile, ResponseHandle responseHandle,

			Item project, Run<?, ?> run, PrintStream logger) {
		this.url = url;
		this.httpMode = httpMode;
		this.ignoreSslErrors = ignoreSslErrors;

		if (StringUtils.isNotBlank(httpProxy)) {
			this.httpProxy = HttpHost.create(httpProxy);
			if (StringUtils.isNotBlank(proxyAuthentication)) {

				StandardCredentials credential = CredentialsMatchers.firstOrNull(
						CredentialsProvider.lookupCredentials(StandardCredentials.class, project,
								ACL.SYSTEM, URIRequirementBuilder.fromUri(url).build()),
						CredentialsMatchers.withId(proxyAuthentication));

				CredentialsProvider.trackAll(run, credential);

				if (credential instanceof StandardUsernamePasswordCredentials) {
					// create snapshot of credentials because it needs to be serialized to the agent
					this.proxyCredentials = CredentialsProvider
							.snapshot((StandardUsernamePasswordCredentials) credential);
				} else {
					this.proxyCredentials = null;
					throw new IllegalStateException("Proxy authentication '" + proxyAuthentication
							+ "' doesn't exist anymore or is not a username/password credential type");
				}
			} else {
				this.proxyCredentials = null;
			}
		} else {
			this.httpProxy = null;
			this.proxyCredentials = null;
		}

		this.body = body;
		this.headers = headers;
		this.formData = formData;
		this.timeout = timeout != null ? timeout : -1;
		this.useNtlm = useNtlm;
		if (authentication != null && !authentication.isEmpty()) {
			Authenticator auth = HttpRequestGlobalConfig.get().getAuthentication(authentication);

			if (auth == null) {
				StandardCredentials credential = CredentialsMatchers.firstOrNull(
						CredentialsProvider.lookupCredentials(StandardCredentials.class, project,
								ACL.SYSTEM, URIRequirementBuilder.fromUri(url).build()),
						CredentialsMatchers.withId(authentication));

				CredentialsProvider.trackAll(run, credential);

				if (credential != null) {
					// create snapshot of credential because it needs to be serialized to the agent
					credential = CredentialsProvider.snapshot(credential);
					if (credential instanceof StandardUsernamePasswordCredentials) {
						if (this.useNtlm) {
							auth = new CredentialNtlmAuthentication(
									(StandardUsernamePasswordCredentials) credential);
						} else {
							auth = new CredentialBasicAuthentication(
									(StandardUsernamePasswordCredentials) credential);
						}
					}
					if (credential instanceof StandardCertificateCredentials) {
						auth = new CertificateAuthentication(
								(StandardCertificateCredentials) credential);
					}
				}
			}

			if (auth == null) {
				throw new IllegalStateException(
						"Authentication '" + authentication + "' doesn't exist anymore");
			}
			authenticator = auth;
		} else {
			authenticator = null;
		}

		this.uploadFile = uploadFile;
		this.multipartName = multipartName;
		this.wrapAsMultipart = wrapAsMultipart;
		this.useSystemProperties = useSystemProperties;
		this.validResponseCodes = validResponseCodes;
		this.validResponseContent = validResponseContent != null ? validResponseContent : "";
		this.consoleLogResponseBody = Boolean.TRUE.equals(consoleLogResponseBody);
		this.responseHandle = this.consoleLogResponseBody || !this.validResponseContent.isEmpty()
				? ResponseHandle.STRING : responseHandle;
		this.outputFile = outputFile;

		this.localLogger = logger;
		this.remoteLogger = new RemoteOutputStream(new CloseProofOutputStream(logger));
	}

	@Override
	public ResponseContentSupplier call() throws RuntimeException {
		logger().println("HttpMethod: " + httpMode);
		logger().println("URL: " + url);
		for (HttpRequestNameValuePair header : headers) {
			logger().print(header.getName() + ": ");
			logger().println(header.getMaskValue() ? "*****" : header.getValue());
		}

		try {
			return authAndRequest();
		} catch (IOException | InterruptedException | KeyStoreException | NoSuchAlgorithmException
				| KeyManagementException e) {
			throw new IllegalStateException(e);
		}
	}

	private PrintStream logger() {
		if (localLogger == null) {
			localLogger = new PrintStream(remoteLogger, true, StandardCharsets.UTF_8);
		}
		return localLogger;
	}

	private ResponseContentSupplier authAndRequest() throws IOException, InterruptedException,
			KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
		// only leave open if no error happen
		ResponseHandle responseHandle = ResponseHandle.NONE;
		CloseableHttpClient httpclient = null;
		try {
			HttpClientBuilder clientBuilder = HttpClientBuilder.create();

			if (useSystemProperties) {
				clientBuilder.useSystemProperties();
			}

			configureTimeoutAndSsl(clientBuilder);
			if (this.httpProxy != null) {
				clientBuilder.setProxy(this.httpProxy);
			}

			HttpClientUtil clientUtil = new HttpClientUtil();
			// Create the simple body, this is the most frequent operation. It will be overridden
			// later, if a more complex payload descriptor is set.
			HttpRequestBase httpRequestBase = clientUtil.createRequestBase(
					new RequestAction(new URL(url), httpMode, body, null, headers));

			if (formData != null && !formData.isEmpty() && httpMode == HttpMode.POST) {
				// multipart/form-data builder mode
				MultipartEntityBuilder builder = MultipartEntityBuilder.create();

				for (HttpRequestFormDataPart part : formData) {
					if (part.getFileName() == null || part.getFileName().isEmpty()) {
						ContentType textContentType =
								part.getContentType() == null || part.getContentType().isEmpty()
										? ContentType.TEXT_PLAIN
										: ContentType.create(part.getContentType());
						builder.addTextBody(part.getName(), part.getBody(), textContentType);
					} else {
						ContentType fileContentType =
								part.getContentType() == null || part.getContentType().isEmpty()
										? ContentType.APPLICATION_OCTET_STREAM
										: ContentType.create(part.getContentType());
						builder.addBinaryBody(part.getName(),
								new File(part.getResolvedUploadFile().getRemote()), fileContentType,
								part.getFileName());
					}
				}

				HttpEntity mimeBody = builder.build();
				((HttpEntityEnclosingRequestBase) httpRequestBase).setEntity(mimeBody);
			} else if (uploadFile != null
					&& (httpMode == HttpMode.POST || httpMode == HttpMode.PUT)) {
				// No form-data, but a singular uploadFile is set.
				ContentType contentType = ContentType.APPLICATION_OCTET_STREAM;
				for (HttpRequestNameValuePair header : headers) {
					if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(header.getName())) {
						contentType = ContentType.parse(header.getValue());
						break;
					}
				}

				HttpEntity entity;
				if (wrapAsMultipart) {
					// set as multipart/form-data entity for file upload
					MultipartEntityBuilder builder = MultipartEntityBuilder.create();
					builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);

					FileBody fileBody = new FileBody(new File(uploadFile.getRemote()), contentType);
					entity = builder.addPart(multipartName, fileBody).build();
				} else {
					// set as direct entity for file upload
					entity = new FileEntity(new File(uploadFile.getRemote()), contentType);
				}

				// File upload overrides requestBody.
				((HttpEntityEnclosingRequestBase) httpRequestBase).setEntity(entity);
				httpRequestBase.setHeader(entity.getContentType());
				httpRequestBase.setHeader(entity.getContentEncoding());
			}

			HttpContext context = new BasicHttpContext();
			httpclient = auth(clientBuilder, httpRequestBase, context);

			ResponseContentSupplier response =
					executeRequest(httpclient, clientUtil, httpRequestBase, context);
			processResponse(response);

			responseHandle = this.responseHandle;
			if (responseHandle == ResponseHandle.LEAVE_OPEN) {
				response.setHttpClient(httpclient);
			}
			return response;
		} finally {
			if (responseHandle != ResponseHandle.LEAVE_OPEN) {
				if (httpclient != null) {
					httpclient.close();
				}
			}
		}
	}

	private void configureTimeoutAndSsl(HttpClientBuilder clientBuilder)
			throws NoSuchAlgorithmException, KeyManagementException {
		// timeout
		if (timeout > 0) {
			int t = timeout * 1000;
			RequestConfig config = RequestConfig.custom().setSocketTimeout(t).setConnectTimeout(t)
					.setConnectionRequestTimeout(t).build();
			clientBuilder.setDefaultRequestConfig(config);
		}
		// Ignore SSL errors
		if (ignoreSslErrors) {
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, new TrustManager[] { new NoopTrustManager() },
					new java.security.SecureRandom());
			SSLConnectionSocketFactory sslsf =
					new SSLConnectionSocketFactory(sc, NoopHostnameVerifier.INSTANCE);
			clientBuilder.setSSLSocketFactory(sslsf);
		}
	}

	private CloseableHttpClient auth(HttpClientBuilder clientBuilder,
			HttpRequestBase httpRequestBase, HttpContext context)
			throws IOException, InterruptedException {

		if (proxyCredentials != null) {
			logger().println("Using proxy authentication: " + proxyCredentials.getId());
			if (authenticator instanceof CredentialBasicAuthentication) {
				((CredentialBasicAuthentication) authenticator).addCredentials(httpProxy,
						proxyCredentials);
			} else {
				new CredentialBasicAuthentication(proxyCredentials).prepare(clientBuilder, context,
						httpProxy);
			}
		}

		if (authenticator == null) {
			return clientBuilder.build();
		}

		logger().println("Using authentication: " + authenticator.getKeyName());
		return authenticator.authenticate(clientBuilder, context, httpRequestBase, logger());
	}

	private ResponseContentSupplier executeRequest(CloseableHttpClient httpclient,
			HttpClientUtil clientUtil, HttpRequestBase httpRequestBase, HttpContext context)
			throws IOException {
		ResponseContentSupplier responseContentSupplier;
		try {
			final HttpResponse response =
					clientUtil.execute(httpclient, context, httpRequestBase, logger());
			// The HttpEntity is consumed by the ResponseContentSupplier
			responseContentSupplier = new ResponseContentSupplier(responseHandle, response);
		} catch (UnknownHostException uhe) {
			logger().println(
					"Treating UnknownHostException(" + uhe.getMessage() + ") as 404 Not Found");
			responseContentSupplier =
					new ResponseContentSupplier("UnknownHostException as 404 Not Found", 404);
		} catch (SocketTimeoutException | ConnectException ce) {
			logger().println("Treating " + ce.getClass() + "(" + ce.getMessage()
					+ ") as 408 Request Timeout");
			responseContentSupplier = new ResponseContentSupplier(
					ce.getClass() + "(" + ce.getMessage() + ") as 408 Request Timeout", 408);
		}

		return responseContentSupplier;
	}

	private void responseCodeIsValid(ResponseContentSupplier response) throws AbortException {
		List<IntStream> ranges = DescriptorImpl.parseToRange(validResponseCodes);
		for (IntStream range : ranges) {
			if (range.anyMatch(status -> status == response.getStatus())) {
				logger().println("Success: Status code " + response.getStatus()
						+ " is in the accepted range: " + validResponseCodes);
				return;
			}
		}
		throw new AbortException("Fail: Status code " + response.getStatus()
				+ " is not in the accepted range: " + validResponseCodes + " while calling " + url);
	}

	private void processResponse(ResponseContentSupplier response)
			throws IOException, InterruptedException {
		// logs
		if (consoleLogResponseBody) {
			logger().println("Response: \n" + response.getContent());
		}

		// validate status code
		responseCodeIsValid(response);

		// validate content
		if (!validResponseContent.isEmpty()) {
			if (!response.getContent().contains(validResponseContent)) {
				throw new AbortException("Fail: Response doesn't contain expected content '"
						+ validResponseContent + "'" + " while calling " + url);
			}
		}

		// save file
		if (outputFile == null) {
			return;
		}
		logger().println("Saving response body to " + outputFile);

		InputStream in = response.getContentStream();
		if (in == null) {
			return;
		}
		OutputStream out = null;
		try {
			out = outputFile.write();
			IOUtils.copy(in, out);
		} finally {
			if (out != null) {
				out.close();
			}
			in.close();
		}
	}

	private static class NoopTrustManager extends X509ExtendedTrustManager {

		@Override
		public void checkClientTrusted(X509Certificate[] arg0, String arg1) {
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) {
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[0];
		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
		}
	}
}