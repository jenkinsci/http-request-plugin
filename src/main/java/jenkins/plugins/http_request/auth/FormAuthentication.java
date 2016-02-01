package jenkins.plugins.http_request.auth;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import jenkins.plugins.http_request.HttpRequest;
import jenkins.plugins.http_request.util.HttpClientUtil;
import jenkins.plugins.http_request.util.RequestAction;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * @author Janario Oliveira
 */
public class FormAuthentication extends AbstractDescribableImpl<FormAuthentication>
        implements Authenticator {

    private final String keyName;
    private final List<RequestAction> actions;

    @DataBoundConstructor
    public FormAuthentication(String keyName, List<RequestAction> actions) {
        this.keyName = keyName;
        this.actions = actions == null ? new ArrayList<RequestAction>() : actions;
    }

    public String getKeyName() {
        return keyName;
    }

    public List<RequestAction> getActions() {
        return Collections.unmodifiableList(actions);
    }

    public void authenticate(DefaultHttpClient client, HttpContext context,
            HttpRequestBase requestBase, PrintStream logger, Integer timeout) throws IOException, InterruptedException {
        final HttpClientUtil clientUtil = new HttpClientUtil();
        for (RequestAction requestAction : actions) {
            final HttpRequestBase method = clientUtil.createRequestBase(requestAction);

            final HttpResponse execute = clientUtil.execute(client, context, method, logger, timeout);
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

        public String getHttpRequestHelpPath() {
            return "/descriptor/"+HttpRequest.class.getName()+"/help";
        }

    }
}
