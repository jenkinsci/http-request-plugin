package jenkins.plugins.http_request.auth;

import jenkins.plugins.http_request.util.RequestAction;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.plugins.http_request.HttpRequest;
import jenkins.plugins.http_request.util.HttpClientUtil;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * @author Janario Oliveira
 */
public class FormAuthentication extends AbstractDescribableImpl<FormAuthentication>
        implements Authenticator {

    private final String keyName;
    private List<RequestAction> actions = new ArrayList<RequestAction>();

    @DataBoundConstructor
    public FormAuthentication(String keyName) {
        this.keyName = keyName;
    }

    public String getKeyName() {
        return keyName;
    }

    public List<RequestAction> getActions() {
        return actions;
    }

    public void setActions(List<RequestAction> actions) {
        this.actions = actions;
    }

    @Override
    public FormAuthenticationDescriptor getDescriptor() {
        return (FormAuthenticationDescriptor) super.getDescriptor();
    }

    public void authenticate(DefaultHttpClient client,
            HttpRequestBase requestBase, PrintStream logger) throws IOException {
        HttpClientUtil clientUtil = new HttpClientUtil();
        for (RequestAction requestAction : actions) {
            HttpRequestBase method = clientUtil.createRequestBase(requestAction);

            HttpResponse execute = clientUtil.execute(client, method, logger);
            //from 400(client error) to 599(server error)
            if ((execute.getStatusLine().getStatusCode() >= 400
                    && execute.getStatusLine().getStatusCode() <= 599)) {
                throw new IllegalStateException("Error doing authentication");
            }
        }
    }

    @Extension
    public static class FormAuthenticationDescriptor extends Descriptor<FormAuthentication> {

        public FormValidation doCheckKeyName(@QueryParameter String value) {
            HttpRequest.DescriptorImpl descriptor = (HttpRequest.DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(HttpRequest.class);
            return descriptor.doValidateKeyName(value);
        }

        @Override
        public String getDisplayName() {
            return "Form Authentication";
        }
    }
}
