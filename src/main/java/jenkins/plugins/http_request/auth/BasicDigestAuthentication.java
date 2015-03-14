package jenkins.plugins.http_request.auth;

import java.io.PrintStream;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import jenkins.plugins.http_request.HttpRequest;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * @author Janario Oliveira
 */
public class BasicDigestAuthentication extends AbstractDescribableImpl<BasicDigestAuthentication>
        implements Authenticator {

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

    public void authenticate(DefaultHttpClient client,
            HttpRequestBase requestBase, PrintStream logger, int timeout) {
        client.getCredentialsProvider().setCredentials(
                new AuthScope(requestBase.getURI().getHost(), requestBase.getURI().getPort()),
                new UsernamePasswordCredentials(userName, password));
    }

    @Extension
    public static class BasicDigestAuthenticationDescriptor extends Descriptor<BasicDigestAuthentication> {

        public FormValidation doCheckKeyName(@QueryParameter String value) {
            HttpRequest.DescriptorImpl descriptor = (HttpRequest.DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(HttpRequest.class);
            return descriptor.doValidateKeyName(value);
        }

        public FormValidation doCheckUserName(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckPassword(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        @Override
        public String getDisplayName() {
            return "Basic/Digest Authentication";
        }
    }
}
