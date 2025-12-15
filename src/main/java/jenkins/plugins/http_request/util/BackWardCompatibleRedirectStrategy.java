package jenkins.plugins.http_request.util;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.protocol.HttpContext;

public class BackWardCompatibleRedirectStrategy extends DefaultRedirectStrategy {

	private static final String[] REDIRECT_METHODS = new String[]{HttpGet.METHOD_NAME, HttpHead.METHOD_NAME};

	@Override
	public boolean isRedirected(final HttpRequest request, final HttpResponse response, final HttpContext context) {
		if (!response.containsHeader(HttpHeaders.LOCATION)) {
			return false;
		}

		final int statusCode = response.getCode();
		final String method = request.getMethod();
		final Header locationHeader = response.getFirstHeader("location");
		return switch (statusCode) {
			case HttpStatus.SC_MOVED_TEMPORARILY ->
					isRedirectable(method) && locationHeader != null;
			case HttpStatus.SC_MOVED_PERMANENTLY, HttpStatus.SC_TEMPORARY_REDIRECT,
				 HttpStatus.SC_PERMANENT_REDIRECT -> isRedirectable(method);
			case HttpStatus.SC_SEE_OTHER -> true;
			default -> false;
		};
	}

	protected boolean isRedirectable(final String method) {
		for (final String m : REDIRECT_METHODS) {
			if (m.equalsIgnoreCase(method)) {
				return true;
			}
		}
		return false;
	}
}
