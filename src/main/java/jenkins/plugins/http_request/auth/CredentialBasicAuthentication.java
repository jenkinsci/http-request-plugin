package jenkins.plugins.http_request.auth;

import java.io.PrintStream;
import java.util.Hashtable;
import java.util.Map;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

/**
 * @author Janario Oliveira
 */
public class CredentialBasicAuthentication implements Authenticator {
    private static final long serialVersionUID = 8034231374732499786L;

    private final StandardUsernamePasswordCredentials credential;
    private final Map<HttpHost, StandardUsernamePasswordCredentials> extraCredentials = new Hashtable<>();

    public CredentialBasicAuthentication(StandardUsernamePasswordCredentials credential) {
        this.credential = credential;
    }

    public void addCredentials(HttpHost host, StandardUsernamePasswordCredentials credentials) {
        if (host == null || credentials == null) {
            throw new IllegalArgumentException("Null target host or credentials");
        }
        extraCredentials.put(host, credentials);
    }

    @Override
    public String getKeyName() {
        return credential.getId();
    }

    @Override
    public CloseableHttpClient authenticate(HttpClientBuilder clientBuilder, HttpContext context, HttpRequestBase requestBase, HttpHost proxyHost, PrintStream logger) {
        prepare(clientBuilder, context, requestBase, proxyHost);
        return clientBuilder.build();
    }

    public void prepare(HttpClientBuilder clientBuilder, HttpContext context, HttpRequestBase requestBase, HttpHost proxyHost) {
        prepare(clientBuilder, context, URIUtils.extractHost(requestBase.getURI()), proxyHost);
    }

    public void prepare(HttpClientBuilder clientBuilder, HttpContext context, HttpHost targetHost, HttpHost proxyHost) {
        auth(clientBuilder, context, targetHost, proxyHost,
                credential.getUsername(), credential.getPassword().getPlainText(), extraCredentials);
    }

    static void auth(HttpClientBuilder clientBuilder, HttpContext context, HttpHost targetHost, HttpHost proxyHost,
                     String username, String password) {
        auth(clientBuilder, context, targetHost, proxyHost, username, password, null);
    }

    static void auth(HttpClientBuilder clientBuilder, HttpContext context, HttpHost targetHost, HttpHost proxyHost,
                     String username, String password, Map<HttpHost, StandardUsernamePasswordCredentials> extraCreds) {
        CredentialsProvider provider = new BasicCredentialsProvider();
        AuthCache authCache = new BasicAuthCache();

        provider.setCredentials(new AuthScope(targetHost.getHostName(), targetHost.getPort()),
                new org.apache.http.auth.UsernamePasswordCredentials(username, password));

        if (proxyHost != null) {
            // Do not preempt authentication if target is the proxy
            if (!targetHost.getHostName().equals(proxyHost.getHostName()) ||
                (targetHost.getPort() != (proxyHost.getPort()))) {
                authCache.put(targetHost, new BasicScheme());
            }
        } else {
            authCache.put(targetHost, new BasicScheme());
        }

        if (extraCreds != null && !extraCreds.isEmpty()) {
            for (Map.Entry<HttpHost, StandardUsernamePasswordCredentials> creds : extraCreds.entrySet()) {
                provider.setCredentials(
                        new AuthScope(creds.getKey().getHostName(), creds.getKey().getPort()),
                        new org.apache.http.auth.UsernamePasswordCredentials(
                                creds.getValue().getUsername(),
                                creds.getValue().getPassword().getPlainText()
                        )
                );

                if (proxyHost != null) {
                    // Do not preempt authentication if target is the proxy
                    if (!creds.getKey().getHostName().equals(proxyHost.getHostName()) ||
                        (creds.getKey().getPort() != (proxyHost.getPort()))) {
                        authCache.put(targetHost, new BasicScheme());
                    }
                } else {
                    authCache.put(targetHost, new BasicScheme());
                }
            }
        }

        clientBuilder.setDefaultCredentialsProvider(provider);
        context.setAttribute(HttpClientContext.AUTH_CACHE, authCache);
    }
}
