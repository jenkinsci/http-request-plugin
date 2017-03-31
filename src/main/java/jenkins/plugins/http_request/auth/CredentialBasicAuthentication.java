package jenkins.plugins.http_request.auth;

import java.io.IOException;
import java.io.PrintStream;

import org.apache.http.auth.AuthScope;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

/**
 * @author Janario Oliveira
 */
public class CredentialBasicAuthentication implements Authenticator {
	private static final long serialVersionUID = 8034231374732499786L;

	private final StandardUsernamePasswordCredentials credential;

	public CredentialBasicAuthentication(StandardUsernamePasswordCredentials credential) {
		this.credential = credential;
	}

	@Override
	public String getKeyName() {
		return credential.getId();
	}

	@Override
	public CloseableHttpClient authenticate(HttpClientBuilder clientBuilder, HttpContext context, HttpRequestBase requestBase, PrintStream logger)
			throws IOException, InterruptedException {
		return auth(clientBuilder, context, requestBase,
				credential.getUsername(), credential.getPassword().getPlainText());
	}

	static CloseableHttpClient auth(HttpClientBuilder clientBuilder, HttpContext context, HttpRequestBase requestBase,
									 String username, String password) {
		CredentialsProvider provider = new BasicCredentialsProvider();
		provider.setCredentials(
				new AuthScope(requestBase.getURI().getHost(), requestBase.getURI().getPort()),
				new org.apache.http.auth.UsernamePasswordCredentials(username, password));
		clientBuilder.setDefaultCredentialsProvider(provider);

		AuthCache authCache = new BasicAuthCache();
		authCache.put(URIUtils.extractHost(requestBase.getURI()), new BasicScheme());
		context.setAttribute(HttpClientContext.AUTH_CACHE, authCache);

		return clientBuilder.build();
	}
}
