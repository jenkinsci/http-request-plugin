package jenkins.plugins.http_request.util;

import java.net.URI;

import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;

public class HttpMkcol extends HttpEntityEnclosingRequestBase {
	public final static String METHOD_NAME = "MKCOL";

	public HttpMkcol(final String uri) {
		super();
		setURI(URI.create(uri));
	}

	@Override
	public String getMethod() {
		return METHOD_NAME;
	}
}
