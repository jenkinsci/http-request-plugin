package jenkins.plugins.http_request.auth;

import java.io.IOException;
import java.io.PrintStream;

import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;

/**
 * @author Janario Oliveira
 */
public interface Authenticator {

    String getKeyName();

    void authenticate(DefaultHttpClient client, HttpContext context, HttpRequestBase requestBase,
            PrintStream logger, Integer timeout) throws IOException, InterruptedException;
}
