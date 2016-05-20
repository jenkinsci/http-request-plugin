package jenkins.plugins.http_request.util;

import hudson.FilePath;
import jenkins.plugins.http_request.HttpMode;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;

import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * @author Janario Oliveira
 */
public class HttpClientUtil {

    public HttpRequestBase createRequestBase(RequestAction requestAction) throws IOException {
        if (requestAction.getMode() == HttpMode.HEAD) {
            return makeHead(requestAction);

        } else if (requestAction.getMode() == HttpMode.GET) {
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

    private HttpEntity makeEntity(List<HttpRequestNameValuePair> params) throws
            UnsupportedEncodingException {
        return new UrlEncodedFormEntity(params);
    }

    public HttpGet makeGet(RequestAction requestAction) throws IOException {
        final String url = requestAction.getUrl().toString();
        final StringBuilder sb = new StringBuilder(url);

        if (!requestAction.getParams().isEmpty()) {
            sb.append(url.contains("?") ? "&" : "?");
            final HttpEntity entity = makeEntity(requestAction.getParams());

            final BufferedReader br = new BufferedReader(new InputStreamReader(entity.getContent(), Charset.forName("UTF-8")));
            String s;
            while ((s = br.readLine()) != null) {
                sb.append(s);
            }
            br.close();
        }
        return new HttpGet(sb.toString());
    }

    public HttpHead makeHead(RequestAction requestAction) throws UnsupportedEncodingException {
        final HttpHead httpHead = new HttpHead(requestAction.getUrl().toString());

        return httpHead;
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
        final HttpDelete httpDelete = new HttpDelete(requestAction.getUrl().toString());

        return httpDelete;
    }

    public HttpResponse execute(DefaultHttpClient client, HttpContext context, HttpRequestBase method,
                                PrintStream logger, Integer timeout) throws IOException, InterruptedException {
        doSecurity(client, method.getURI());

        logger.println("Sending request to url: " + method.getURI());
        
        if (timeout !=null) {
            client.getParams().setParameter("http.socket.timeout", timeout * 1000);
            client.getParams().setParameter("http.connection.timeout", timeout * 1000);
            client.getParams().setParameter("http.connection-manager.timeout", timeout * 1000);
            client.getParams().setParameter("http.protocol.head-body-timeout", timeout * 1000);
        }
        
        final HttpResponse httpResponse = client.execute(method, context);
        logger.println("Response Code: " + httpResponse.getStatusLine());
        
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
            throw new IOException("Error unknown", ex);
        }
    }
}
