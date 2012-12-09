package jenkins.plugins.http_request.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import jenkins.plugins.http_request.HttpMode;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

/**
 * @author Janario Oliveira
 */
public class HttpClientUtil {

    public HttpRequestBase createRequestBase(RequestAction requestAction) throws
            UnsupportedEncodingException, IOException {
        return (HttpMode.valueOf(requestAction.getMode()) == HttpMode.GET)
                ? makeGet(requestAction) : makePost(requestAction);
    }

    private HttpEntity makeEntity(List<NameValuePair> params) throws
            UnsupportedEncodingException {

        List<BasicNameValuePair> p = new ArrayList<BasicNameValuePair>(params.size());
        for (NameValuePair nameValuePair : params) {
            p.add(new BasicNameValuePair(nameValuePair.getName(), nameValuePair.getValue()));
        }
        return new UrlEncodedFormEntity(p);
    }

    public HttpGet makeGet(RequestAction requestAction) throws
            UnsupportedEncodingException, IOException {
        StringBuilder sb = new StringBuilder(requestAction.getUrl());

        if (!requestAction.getParams().isEmpty()) {
            sb.append("?");
            HttpEntity entity = makeEntity(requestAction.getParams());

            BufferedReader br = new BufferedReader(new InputStreamReader(entity.getContent()));
            String s;
            while ((s = br.readLine()) != null) {
                sb.append(s);
            }
        }
        return new HttpGet(sb.toString());
    }

    public HttpPost makePost(RequestAction requestAction) throws
            UnsupportedEncodingException {
        HttpEntity entity = makeEntity(requestAction.getParams());

        HttpPost httpPost = new HttpPost(requestAction.getUrl());
        httpPost.setEntity(entity);
        return httpPost;
    }

    public HttpResponse execute(DefaultHttpClient client, HttpRequestBase method,
            PrintStream logger) throws IOException {
        doSecurity(client, method.getURI());

        logger.println("Sending request to url: " + method.getURI());

        HttpResponse execute = client.execute(method);
        logger.println("Response Code: " + execute.getStatusLine());
        logger.println("Response: \n" + EntityUtils.toString(execute.getEntity()));
        EntityUtils.consume(execute.getEntity());

        return execute;
    }

    private void doSecurity(DefaultHttpClient base, URI uri) throws IOException {
        if (!uri.getScheme().equals("https")) {
            return;
        }

        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            X509TrustManager tm = new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] xcs,
                        String string) {
                }

                public void checkServerTrusted(X509Certificate[] xcs,
                        String string) {
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            };
            X509HostnameVerifier verifier = new X509HostnameVerifier() {
                @Override
                public void verify(String string, SSLSocket ssls)
                        throws IOException {
                }

                @Override
                public void verify(String string, X509Certificate xc)
                        throws SSLException {
                }

                @Override
                public void verify(String string, String[] strings,
                        String[] strings1) throws SSLException {
                }

                @Override
                public boolean verify(String string, SSLSession ssls) {
                    return true;
                }
            };
            ctx.init(null, new TrustManager[]{tm}, null);
            SSLSocketFactory ssf = new SSLSocketFactory(new TrustStrategy() {
                public boolean isTrusted(X509Certificate[] chain,
                        String authType) throws
                        CertificateException {
                    return true;
                }
            }, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            ssf.setHostnameVerifier(verifier);

            SchemeRegistry schemeRegistry = base.getConnectionManager().getSchemeRegistry();
            final int port = uri.getPort() < 0 ? 443 : uri.getPort();
            schemeRegistry.register(new Scheme(uri.getScheme(), port, ssf));
        } catch (Exception ex) {
            throw new IOException("Error unknow", ex);
        }
    }
}
