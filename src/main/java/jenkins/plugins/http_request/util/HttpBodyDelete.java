package jenkins.plugins.http_request.util;

import java.net.URI;

import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;

/**
 * Add support to send body in delete method
 *
 * @author Janario Oliveira
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
