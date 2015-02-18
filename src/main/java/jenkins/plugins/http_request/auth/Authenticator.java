package jenkins.plugins.http_request.auth;

import java.io.IOException;
import java.io.PrintStream;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;

/**
 * @author Janario Oliveira
 */
public interface Authenticator {

    String getKeyName();

    void authenticate(DefaultHttpClient client, HttpRequestBase requestBase,
            PrintStream logger, int timeout) throws IOException;
}
