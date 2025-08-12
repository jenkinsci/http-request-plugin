package jenkins.plugins.http_request.auth;

import java.io.PrintStream;
import java.io.Serial;
import java.net.URISyntaxException;
import java.util.Hashtable;
import java.util.Map;

import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

/**
 * @author Janario Oliveira
 */
public class CredentialBasicAuthentication implements Authenticator {
    @Serial
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
        extraCredentials.put(host, credential);
    }

    @Override
    public String getKeyName() {
        return credential.getId();
    }

    @Override
    public CloseableHttpClient authenticate(HttpClientBuilder clientBuilder, HttpClientContext context, HttpUriRequestBase requestBase, PrintStream logger) {
        prepare(clientBuilder, context, requestBase);
        return clientBuilder.build();
    }

    public void prepare(HttpClientBuilder clientBuilder, HttpClientContext context, HttpUriRequestBase requestBase) {
        try {
            prepare(clientBuilder, context, URIUtils.extractHost(requestBase.getUri()));
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    public void prepare(HttpClientBuilder clientBuilder, HttpClientContext context, HttpHost targetHost) {
        auth(clientBuilder, context, targetHost,
                credential.getUsername(), credential.getPassword().getPlainText(), extraCredentials);
    }

    static void auth(HttpClientBuilder clientBuilder, HttpClientContext context, HttpHost targetHost,
                     String username, String password, Map<HttpHost, StandardUsernamePasswordCredentials> extraCreds) {
        BasicCredentialsProvider provider = new BasicCredentialsProvider();
        AuthCache authCache = new BasicAuthCache();

        Credentials mainCredentials = new UsernamePasswordCredentials(username, password.toCharArray());
        setCredentials(targetHost, provider, authCache, mainCredentials);

        if (extraCreds != null && !extraCreds.isEmpty()) {
            for (Map.Entry<HttpHost, StandardUsernamePasswordCredentials> creds : extraCreds.entrySet()) {
                Credentials extraCredentials = new UsernamePasswordCredentials(
                        creds.getValue().getUsername(),
                        creds.getValue().getPassword().getPlainText().toCharArray());
                setCredentials(creds.getKey(), provider, authCache, extraCredentials);
            }
        }

        clientBuilder.setDefaultCredentialsProvider(provider);
        context.setAuthCache(authCache);
        context.setCredentialsProvider(provider);
    }

    static void setCredentials(HttpHost targetHost, BasicCredentialsProvider provider, AuthCache authCache, Credentials credentials) {
        provider.setCredentials(new AuthScope(targetHost), credentials);

        BasicScheme basicScheme = new BasicScheme();
        basicScheme.initPreemptive(credentials);
        authCache.put(targetHost, basicScheme);
    }
}
