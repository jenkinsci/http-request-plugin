package jenkins.plugins.http_request.util;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.util.ArrayList;
import java.util.List;
import jenkins.plugins.http_request.HttpMode;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * @author Janario Oliveira
 */
public class RequestAction extends AbstractDescribableImpl<RequestAction> {

    private String url;
    private String mode;
    private List<NameValuePair> params = new ArrayList<NameValuePair>();

    public RequestAction() {
    }

    @DataBoundConstructor
    public RequestAction(String url, String mode) {
        this.url = url;
        this.mode = mode;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public List<NameValuePair> getParams() {
        return params;
    }

    public void setParams(List<NameValuePair> params) {
        this.params = params;
    }

    @Override
    public ActionFormAuthenticationDescriptor getDescriptor() {
        return (ActionFormAuthenticationDescriptor) super.getDescriptor();
    }

    @Extension
    public static class ActionFormAuthenticationDescriptor extends Descriptor<RequestAction> {

        public FormValidation doCheckUrl(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public ListBoxModel doFillModeItems() {
            return HttpMode.getFillItems();
        }

        @Override
        public String getDisplayName() {
            return "Action Form Authentication";
        }
    }
}
