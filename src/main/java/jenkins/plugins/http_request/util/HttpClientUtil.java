package jenkins.plugins.http_request.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.core5.http.io.entity.StringEntity;

import jenkins.plugins.http_request.HttpMode;

/**
 * @author Janario Oliveira
 */
public class HttpClientUtil {

    public HttpUriRequestBase createRequestBase(RequestAction requestAction) throws IOException {
        HttpUriRequestBase httpRequestBase = doCreateRequestBase(requestAction);
        for (HttpRequestNameValuePair header : requestAction.getHeaders()) {
            httpRequestBase.addHeader(header.getName(), header.getValue());
        }

        return httpRequestBase;
    }

    private HttpUriRequestBase doCreateRequestBase(RequestAction requestAction) throws IOException {
        //without entity
        if (requestAction.getMode() == HttpMode.HEAD) {
            return new HttpHead(getUrlWithParams(requestAction));
        } else if (requestAction.getMode() == HttpMode.GET && (requestAction.getRequestBody() == null || requestAction.getRequestBody().isEmpty())) {
            return new HttpGet(getUrlWithParams(requestAction));
        }

        //with entity
        final String uri = requestAction.getUrl().toString();
        HttpUriRequestBase http;
        if (requestAction.getMode() == HttpMode.GET) {
            http = new HttpGet(getUrlWithParams(requestAction));
        } else if (requestAction.getMode() == HttpMode.DELETE) {
            http = new HttpDelete(uri);
        } else if (requestAction.getMode() == HttpMode.PUT) {
            http = new HttpPut(uri);
        } else if (requestAction.getMode() == HttpMode.PATCH) {
            http = new HttpPatch(uri);
        } else if (requestAction.getMode() == HttpMode.OPTIONS) {
            return new HttpOptions(getUrlWithParams(requestAction));
        } else if (requestAction.getMode() == HttpMode.MKCOL) {
            return new HttpMkcol(uri);
        } else { //default post
            http = new HttpPost(uri);
        }

        http.setEntity(makeEntity(requestAction));
        return http;
    }

    private HttpEntity makeEntity(RequestAction requestAction) {
        if (requestAction.getRequestBody() != null && !requestAction.getRequestBody().isEmpty()) {
            ContentType contentType = null;
            for (HttpRequestNameValuePair header : requestAction.getHeaders()) {
                if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(header.getName())) {
                    contentType = ContentType.parse(header.getValue());
                    break;
                }
            }

            return new StringEntity(requestAction.getRequestBody(), contentType);
        }
        return toUrlEncoded(requestAction.getParams());
    }

    private String getUrlWithParams(RequestAction requestAction) throws IOException {
        String url = requestAction.getUrl().toString();

        if (!requestAction.getParams().isEmpty()) {
            url = appendParamsToUrl(url, requestAction.getParams());
        }
        return url;
    }

    private static UrlEncodedFormEntity toUrlEncoded(List<HttpRequestNameValuePair> params) {
        return new UrlEncodedFormEntity(params);
    }

    public static String appendParamsToUrl(String url, List<HttpRequestNameValuePair> params) throws IOException {
        url += url.contains("?") ? "&" : "?";
        url += paramsToString(params);

        return url;
    }

    public static String paramsToString(List<HttpRequestNameValuePair> params) throws IOException {
        try (UrlEncodedFormEntity entity = toUrlEncoded(params); InputStream is = entity.getContent()) {
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        }
    }

    public HttpResponse execute(HttpClient client, HttpClientContext context, HttpUriRequestBase method,
                                PrintStream logger) throws IOException {
         try {
            logger.println("Sending request to url: " + method.getUri());

            final HttpResponse httpResponse = client.executeOpen(URIUtils.extractHost(method.getUri()), method, context);
            logger.println("Response Code: " + httpResponse.getCode());

            return httpResponse;
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
}
