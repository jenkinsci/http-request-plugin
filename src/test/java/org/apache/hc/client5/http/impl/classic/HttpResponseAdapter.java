package org.apache.hc.client5.http.impl.classic;

import org.apache.hc.core5.http.ClassicHttpResponse;

public final class HttpResponseAdapter {

    /**
     * Wraps {@link CloseableHttpResponse#adapt(ClassicHttpResponse)}.
     * @param response the ClassicHttpResponse
     * @return adapted CloseableHttpResponse
     */
    public static CloseableHttpResponse adapt(final ClassicHttpResponse response) {
        return CloseableHttpResponse.adapt(response);
    }
}
