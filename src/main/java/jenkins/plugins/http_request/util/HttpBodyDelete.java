package jenkins.plugins.http_request.util;

import java.net.URI;

import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;

/**
 * @author Janario Oliveira
 * Add support to send body in delete method
 */
public class HttpBodyDelete extends HttpEntityEnclosingRequestBase {
	public HttpBodyDelete(final String uri) {
		super();
		setURI(URI.create(uri));
	}

	@Override
	public String getMethod() {
		return HttpDelete.METHOD_NAME;
	}
}
