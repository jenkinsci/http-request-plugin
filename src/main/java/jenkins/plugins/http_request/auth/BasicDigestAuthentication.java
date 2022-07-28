package jenkins.plugins.http_request.auth;

import java.io.PrintStream;

import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
	private static final long serialVersionUID = 4818288270720177069L;

	@SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "SECURITY-2053:Field should not be serialized")
	private transient final String keyName;
	@SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "SECURITY-2053:Field should not be serialized")
    private transient final String userName;
	@SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "SECURITY-2053:Field should not be serialized")
    private transient final String password;

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
	public CloseableHttpClient authenticate(HttpClientBuilder clientBuilder, HttpContext context,
											HttpRequestBase requestBase, PrintStream logger) {
		CredentialBasicAuthentication.auth(clientBuilder, context, URIUtils.extractHost(requestBase.getURI()), userName, password);
		return clientBuilder.build();
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
