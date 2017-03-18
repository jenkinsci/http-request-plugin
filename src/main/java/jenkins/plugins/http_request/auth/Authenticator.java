package jenkins.plugins.http_request.auth;

import java.io.IOException;
import java.io.PrintStream;

import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;

/**
 * @author Janario Oliveira
 */
public interface Authenticator {

	String getKeyName();

	CloseableHttpClient authenticate(HttpClientBuilder clientBuilder, HttpContext context, HttpRequestBase requestBase,
					  PrintStream logger) throws IOException, InterruptedException;
}
