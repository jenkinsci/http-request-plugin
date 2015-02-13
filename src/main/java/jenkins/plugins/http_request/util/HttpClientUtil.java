package jenkins.plugins.http_request.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

import jenkins.plugins.http_request.HttpMode;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

/**
 * @author Janario Oliveira
 */
public class HttpClientUtil {

    public HttpRequestBase createRequestBase(RequestAction requestAction) throws
            UnsupportedEncodingException, IOException {

        if (requestAction.getMode() == HttpMode.GET) {
            return makeGet(requestAction);

        } else if (requestAction.getMode() == HttpMode.POST) {
            return makePost(requestAction);

        } else if (requestAction.getMode() == HttpMode.PUT) {
            return makePut(requestAction);

        } else if (requestAction.getMode() == HttpMode.DELETE) {
            return makeDelete(requestAction);
        }

        return makePost(requestAction);
    }

    private HttpEntity makeEntity(List<NameValuePair> params) throws
            UnsupportedEncodingException {
        return new UrlEncodedFormEntity(params);
    }

    public HttpGet makeGet(RequestAction requestAction) throws
            UnsupportedEncodingException, IOException {
        final String url = requestAction.getUrl().toString();
        final StringBuilder sb = new StringBuilder(url);

        if (!requestAction.getParams().isEmpty()) {
            sb.append(url.contains("?") ? "&" : "?");
            final HttpEntity entity = makeEntity(requestAction.getParams());

            final BufferedReader br = new BufferedReader(new InputStreamReader(entity.getContent()));
            String s;
            while ((s = br.readLine()) != null) {
                sb.append(s);
            }
        }
        return new HttpGet(sb.toString());
    }

    public HttpPost makePost(RequestAction requestAction) throws UnsupportedEncodingException {
        final HttpEntity httpEntity = makeEntity(requestAction.getParams());
        final HttpPost httpPost = new HttpPost(requestAction.getUrl().toString());
        httpPost.setEntity(httpEntity);

        return httpPost;
    }

    public HttpPut makePut(RequestAction requestAction) throws UnsupportedEncodingException {
        final HttpEntity entity = makeEntity(requestAction.getParams());
        final HttpPut httpPut = new HttpPut(requestAction.getUrl().toString());
        httpPut.setEntity(entity);

        return httpPut;
    }

    public HttpDelete makeDelete(RequestAction requestAction) throws UnsupportedEncodingException {
        final HttpEntity httpEntity = makeEntity(requestAction.getParams());
        final HttpDelete httpDelete = new HttpDelete(requestAction.getUrl().toString());

        return httpDelete;
    }

    public HttpResponse execute(DefaultHttpClient client, HttpRequestBase method,
            PrintStream logger, boolean logResponseBody) throws IOException {

        doSecurity(client, method.getURI());
        logger.println("Sending request to url: " + method.getURI());
        final HttpResponse httpResponse = client.execute(method);
        logger.println("Response Code: " + httpResponse.getStatusLine());
	if (logResponseBody){
	    logger.println("Response: \n" + EntityUtils.toString(httpResponse.getEntity()));
	}
        
        EntityUtils.consume(httpResponse.getEntity());

        return httpResponse;
    }

    private void doSecurity(DefaultHttpClient base, URI uri) throws IOException {
        if (!uri.getScheme().equals("https")) {
            return;
        }

        try {
            final SSLSocketFactory ssf = new SSLSocketFactory(new TrustStrategy() {
                public boolean isTrusted(X509Certificate[] chain,
                        String authType) throws CertificateException {
                    return true;
                }
            }, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            final SchemeRegistry schemeRegistry = base.getConnectionManager().getSchemeRegistry();
            final int port = uri.getPort() < 0 ? 443 : uri.getPort();
            schemeRegistry.register(new Scheme(uri.getScheme(), port, ssf));
        } catch (Exception ex) {
            throw new IOException("Error unknow", ex);
        }
    }
}
