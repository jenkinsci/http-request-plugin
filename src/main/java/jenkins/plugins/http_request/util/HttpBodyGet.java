package jenkins.plugins.http_request.util;

import java.net.URI;

import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;

public class HttpBodyGet extends HttpEntityEnclosingRequestBase {
	public HttpBodyGet(final String uri) {
		super();
		setURI(URI.create(uri));
	}

	@Override
	public String getMethod() {
		return HttpGet.METHOD_NAME;
	}
}
