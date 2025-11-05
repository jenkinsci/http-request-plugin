package jenkins.plugins.http_request.auth;

import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.List;

import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.impl.auth.NTLMSchemeFactory;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.config.RegistryBuilder;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

/**
 * @author Daniel Torrescusa
 */
public class CredentialNtlmAuthentication implements Authenticator {

    private final StandardUsernamePasswordCredentials credential;
    private final String username;
    private final String domain;

    public CredentialNtlmAuthentication(StandardUsernamePasswordCredentials credential) {
        this.credential = credential;

        String[] split = credential.getUsername().split("\\\\");
        Integer pieces = split.length;

        if (pieces.equals(2)) {
            this.username = split[1];
            this.domain = split[0];
        } else if (pieces.equals(1)) {
            this.username = split[0];
            this.domain = null;
        } else {
            throw new IllegalStateException("Username contains more than one \\");
        }

    }

    @Override
    public String getKeyName() {
        return credential.getId();
    }

    @Override
    public CloseableHttpClient authenticate(HttpClientBuilder clientBuilder, HttpClientContext context, HttpUriRequestBase requestBase, PrintStream logger) {
        return auth(clientBuilder, context, requestBase,
                username, credential.getPassword().getPlainText(), domain);
    }

    static CloseableHttpClient auth(HttpClientBuilder clientBuilder, HttpClientContext context, HttpUriRequestBase requestBase,
                                     String username, String password, String domain) {
        try {
            BasicCredentialsProvider provider = new BasicCredentialsProvider();
            provider.setCredentials(
                    new AuthScope(requestBase.getUri().getHost(), requestBase.getUri().getPort()),
                    new NTCredentials(username, password.toCharArray(), requestBase.getUri().getHost(), domain));

			// register NTLM authentication support explicitly
			Lookup<AuthSchemeFactory> authRegistry = RegistryBuilder.<AuthSchemeFactory>create()
					.register(StandardAuthScheme.NTLM, NTLMSchemeFactory.INSTANCE)
					.build();
			RequestConfig config = RequestConfig.custom().setTargetPreferredAuthSchemes(List.of(StandardAuthScheme.NTLM))
					.build();

            clientBuilder
					.setDefaultRequestConfig(config)
					.setDefaultCredentialsProvider(provider)
					.setDefaultAuthSchemeRegistry(authRegistry);

			context.setRequestConfig(config);
            context.setCredentialsProvider(provider);
			context.setAuthSchemeRegistry(authRegistry);

            return clientBuilder.build();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
}
