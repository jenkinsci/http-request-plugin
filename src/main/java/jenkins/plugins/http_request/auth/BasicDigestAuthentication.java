package jenkins.plugins.http_request.auth;

import java.io.PrintStream;
import java.io.Serial;
import java.net.URISyntaxException;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.utils.URIUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

import jenkins.plugins.http_request.HttpRequestGlobalConfig;

/**
 * @author Janario Oliveira
 * @deprecated use Jenkins credentials, marked to remove in 1.8.19
 */
@Deprecated
public class BasicDigestAuthentication extends AbstractDescribableImpl<BasicDigestAuthentication>
        implements Authenticator {
    @Serial
    private static final long serialVersionUID = 4818288270720177069L;

    private final String keyName;
    private final String userName;
    private final String password;

    @DataBoundConstructor
    public BasicDigestAuthentication(String keyName, String userName,
            String password) {
        this.keyName = keyName;
        this.userName = userName;
        this.password = password;
    }

    public String getKeyName() {
        return keyName;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public CloseableHttpClient authenticate(HttpClientBuilder clientBuilder, HttpClientContext context,
                                            HttpUriRequestBase requestBase, PrintStream logger) {
        try {
            CredentialBasicAuthentication.auth(clientBuilder, context, URIUtils.extractHost(requestBase.getUri()), userName, password, null);
            return clientBuilder.build();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    @Extension
    public static class BasicDigestAuthenticationDescriptor extends Descriptor<BasicDigestAuthentication> {

        public FormValidation doCheckKeyName(@QueryParameter String value) {
            return HttpRequestGlobalConfig.validateKeyName(value);
        }

        public FormValidation doCheckUserName(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckPassword(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Basic/Digest Authentication";
        }
    }
}
