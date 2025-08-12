package jenkins.plugins.http_request.util;

import java.io.Serial;
import java.net.URI;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;

public class HttpMkcol extends HttpUriRequestBase {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final  String METHOD_NAME = "MKCOL";

    public HttpMkcol(final String uri) {
        super(METHOD_NAME, URI.create(uri));
    }
}
