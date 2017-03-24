package jenkins.plugins.http_request.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HttpContext;

import com.google.common.base.Strings;

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
        if (requestAction.getMode() == HttpMode.HEAD) {
            return makeHead(requestAction);

        } else if (requestAction.getMode() == HttpMode.GET) {
            return makeGet(requestAction);

        } else if (requestAction.getMode() == HttpMode.POST) {
            return makePost(requestAction);

        } else if (requestAction.getMode() == HttpMode.PUT) {
            return makePut(requestAction);

        }  else if (requestAction.getMode() == HttpMode.PATCH) {
            return makePatch(requestAction);

        } else if (requestAction.getMode() == HttpMode.DELETE) {
            return makeDelete(requestAction);
        }

        return makePost(requestAction);
    }

	private HttpEntity makeEntity(RequestAction requestAction) throws
			UnsupportedEncodingException {
		if (!Strings.isNullOrEmpty(requestAction.getRequestBody())) {
			ContentType contentType = null;
			for (HttpRequestNameValuePair header : requestAction.getHeaders()) {
				if ("Content-type".equalsIgnoreCase(header.getName())) {
					contentType = ContentType.parse(header.getValue());
					break;
				}
			}

			return new StringEntity(requestAction.getRequestBody(), contentType);
		}
		return toUrlEncoded(requestAction.getParams());
	}

	public HttpGet makeGet(RequestAction requestAction) throws IOException {
        String url = requestAction.getUrl().toString();

        if (!requestAction.getParams().isEmpty()) {
			url = appendParamsToUrl(url, requestAction.getParams());
        }
        return new HttpGet(url);
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
		StringBuilder sb = new StringBuilder();
		final HttpEntity entity = toUrlEncoded(params);

		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(entity.getContent(), StandardCharsets.UTF_8));
			String s;
			while ((s = br.readLine()) != null) {
				sb.append(s);
			}
			return sb.toString();
		} finally {
			if (br != null) {
				br.close();
			}
		}
	}

	public HttpHead makeHead(RequestAction requestAction) throws UnsupportedEncodingException {
        final HttpHead httpHead = new HttpHead(requestAction.getUrl().toString());

        return httpHead;
    }

    public HttpPost makePost(RequestAction requestAction) throws UnsupportedEncodingException {
        final HttpEntity httpEntity = makeEntity(requestAction);
        final HttpPost httpPost = new HttpPost(requestAction.getUrl().toString());
        httpPost.setEntity(httpEntity);

        return httpPost;
    }

    public HttpPut makePut(RequestAction requestAction) throws UnsupportedEncodingException {
        final HttpEntity entity = makeEntity(requestAction);
        final HttpPut httpPut = new HttpPut(requestAction.getUrl().toString());
        httpPut.setEntity(entity);

        return httpPut;
    }

    public HttpPatch makePatch(RequestAction requestAction) throws UnsupportedEncodingException {
        final HttpEntity entity = makeEntity(requestAction);
        final HttpPatch httpPatch = new HttpPatch(requestAction.getUrl().toString());
        httpPatch.setEntity(entity);

        return httpPatch;
    }

    public HttpDelete makeDelete(RequestAction requestAction) throws UnsupportedEncodingException {
        final HttpDelete httpDelete = new HttpDelete(requestAction.getUrl().toString());

        return httpDelete;
    }

    public HttpResponse execute(HttpClient client, HttpContext context, HttpRequestBase method,
								PrintStream logger) throws IOException, InterruptedException {
        logger.println("Sending request to url: " + method.getURI());
        
        final HttpResponse httpResponse = client.execute(method, context);
        logger.println("Response Code: " + httpResponse.getStatusLine());
        
        return httpResponse;
    }
}
