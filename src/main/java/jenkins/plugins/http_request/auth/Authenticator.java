package jenkins.plugins.http_request.auth;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;

/**
 * @author Janario Oliveira
 */
public interface Authenticator extends Serializable {

    String getKeyName();

    CloseableHttpClient authenticate(HttpClientBuilder clientBuilder, HttpClientContext context, HttpUriRequestBase requestBase,
                                     PrintStream logger) throws IOException, InterruptedException;
}
