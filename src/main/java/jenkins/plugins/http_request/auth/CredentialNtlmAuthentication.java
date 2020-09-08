package jenkins.plugins.http_request.auth;

import java.io.IOException;
import java.io.PrintStream;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

/**
 * @author Daniel Torrescusa
 */
public class CredentialNtlmAuthentication implements Authenticator {

	private final StandardUsernamePasswordCredentials credential;
	private final String username;
	private final String domain;

	public CredentialNtlmAuthentication(StandardUsernamePasswordCredentials credential) {
		this.credential = credential;

		String[] split = credential.getUsername().split("\\\\");
		Integer pieces = split.length;
		
		if (pieces.equals(2))
		{
			this.username = split[1];
			this.domain = split[0];
		} 
		else if (pieces.equals(1))
		{
			this.username = split[0];
			this.domain = null;	
		}
		else {
			throw new IllegalStateException("Username contains more than one \\");
		}

	}

	@Override
	public String getKeyName() {
		return credential.getId();
	}

	@Override
	public CloseableHttpClient authenticate(HttpClientBuilder clientBuilder, HttpContext context, HttpRequestBase requestBase, PrintStream logger) {
		return auth(clientBuilder, context, requestBase,
				username, credential.getPassword().getPlainText(), domain);
	}

	static CloseableHttpClient auth(HttpClientBuilder clientBuilder, HttpContext context, HttpRequestBase requestBase,
									 String username, String password, String domain) {
		
		CredentialsProvider provider = new BasicCredentialsProvider();
		provider.setCredentials(
				new AuthScope(requestBase.getURI().getHost(), requestBase.getURI().getPort()),
				new NTCredentials(username, password, requestBase.getURI().getHost(), domain));
		clientBuilder.setDefaultCredentialsProvider(provider);
		
		return clientBuilder.build();
	}
}
