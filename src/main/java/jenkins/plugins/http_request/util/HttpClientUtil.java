package jenkins.plugins.http_request.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HttpContext;

import jenkins.plugins.http_request.HttpMode;

/**
 * @author Janario Oliveira
 */
public class HttpClientUtil {

    public HttpRequestBase createRequestBase(RequestAction requestAction) throws IOException {
        HttpRequestBase httpRequestBase = doCreateRequestBase(requestAction);
        for (HttpRequestNameValuePair header : requestAction.getHeaders()) {
            httpRequestBase.addHeader(header.getName(), header.getValue());
        }

        return httpRequestBase;
    }

    private HttpRequestBase doCreateRequestBase(RequestAction requestAction) throws IOException {
        //without entity
    	if (requestAction.getMode() == HttpMode.HEAD) {
			return new HttpHead(getUrlWithParams(requestAction));
		} else if (requestAction.getMode() == HttpMode.GET && (requestAction.getRequestBody() == null || requestAction.getRequestBody().isEmpty())) {
			return new HttpGet(getUrlWithParams(requestAction));
        }

		//with entity
		final String uri = requestAction.getUrl().toString();
		HttpEntityEnclosingRequestBase http;
		if (requestAction.getMode() == HttpMode.GET) {
			http = new HttpBodyGet(getUrlWithParams(requestAction));
		} else if (requestAction.getMode() == HttpMode.DELETE) {
			http = new HttpBodyDelete(uri);
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

	private HttpEntity makeEntity(RequestAction requestAction) throws
			UnsupportedEncodingException {
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

	private static UrlEncodedFormEntity toUrlEncoded(List<HttpRequestNameValuePair> params) throws UnsupportedEncodingException {
		return new UrlEncodedFormEntity(params);
	}

	public static String appendParamsToUrl(String url, List<HttpRequestNameValuePair> params) throws IOException {
		url += url.contains("?") ? "&" : "?";
		url += paramsToString(params);

		return url;
	}

	public static String paramsToString(List<HttpRequestNameValuePair> params) throws IOException {
		try (InputStream is = toUrlEncoded(params).getContent()) {
			return IOUtils.toString(is, StandardCharsets.UTF_8);
		}
	}

    public HttpResponse execute(HttpClient client, HttpContext context, HttpRequestBase method,
								PrintStream logger) throws IOException {
        logger.println("Sending request to url: " + method.getURI());

        final HttpResponse httpResponse = client.execute(method, context);
        logger.println("Response Code: " + httpResponse.getStatusLine());

        return httpResponse;
    }
}
