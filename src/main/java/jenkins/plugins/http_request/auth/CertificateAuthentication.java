package jenkins.plugins.http_request.auth;

import java.io.IOException;
import java.io.PrintStream;

import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;

import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;

public class CertificateAuthentication implements Authenticator {

	private final StandardCertificateCredentials credentials;

	public CertificateAuthentication(StandardCertificateCredentials credentials) {
		this.credentials = credentials;
	}

	@Override
	public String getKeyName() {
		return credentials.getId();
	}

	@Override
	public CloseableHttpClient authenticate(HttpClientBuilder clientBuilder,
											HttpContext context,
											HttpRequestBase requestBase,
											PrintStream logger) throws IOException {
		try {
			clientBuilder.setSSLContext(
					SSLContexts.custom().loadKeyMaterial(credentials.getKeyStore(),
							credentials.getPassword().getPlainText().toCharArray()).build());

			return clientBuilder.build();
		}catch(RuntimeException e){
			throw new RuntimeException();
		} 
		catch (Exception e) {
			throw new IOException(e);
		}
	}
}
