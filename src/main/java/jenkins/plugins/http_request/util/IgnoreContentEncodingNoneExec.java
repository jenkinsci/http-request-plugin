package jenkins.plugins.http_request.util;

import java.io.IOException;

import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.io.entity.HttpEntityWrapper;

/**
 * Ignores the non-standard {@code Content-Encoding: none} that some servers send for uncompressed content.
 * Since HttpClient 5.6, an unknown content coding fails the request (JENKINS-76353).
 * Register this handler after {@code ChainElement.COMPRESS} so that it runs before decompression.
 */
public class IgnoreContentEncodingNoneExec implements ExecChainHandler {

	private static final String NONE = "none";

	@Override
	public ClassicHttpResponse execute(final ClassicHttpRequest request, final ExecChain.Scope scope,
			final ExecChain chain) throws IOException, HttpException {
		final ClassicHttpResponse response = chain.proceed(request, scope);
		final HttpEntity entity = response.getEntity();
		if (entity != null && entity.getContentEncoding() != null
				&& NONE.equalsIgnoreCase(entity.getContentEncoding().trim())) {
			response.setEntity(new NoContentEncodingEntity(entity));
		}
		return response;
	}

	private static final class NoContentEncodingEntity extends HttpEntityWrapper {

		NoContentEncodingEntity(final HttpEntity entity) {
			super(entity);
		}

		@Override
		public String getContentEncoding() {
			return null;
		}
	}
}
