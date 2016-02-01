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
import org.apache.http.client.AuthCache;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;
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

    public void authenticate(DefaultHttpClient client, HttpContext context,
            HttpRequestBase requestBase, PrintStream logger, Integer timeout) {
        client.getCredentialsProvider().setCredentials(
                new AuthScope(requestBase.getURI().getHost(), requestBase.getURI().getPort()),
                new UsernamePasswordCredentials(userName, password));
        AuthCache authCache = new BasicAuthCache();
        authCache.put(URIUtils.extractHost(requestBase.getURI()), new BasicScheme());
        context.setAttribute(ClientContext.AUTH_CACHE, authCache);
    }

    @Extension
    public static class BasicDigestAuthenticationDescriptor extends Descriptor<BasicDigestAuthentication> {

        public String getHttpRequestHelpPath() {
            return "/descriptor/"+HttpRequest.class.getName()+"/help";
        }

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
