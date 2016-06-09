package jenkins.plugins.http_request.util;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.plugins.http_request.HttpMode;
import jenkins.plugins.http_request.HttpRequest;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * @author Janario Oliveira
 */
public class RequestAction extends AbstractDescribableImpl<RequestAction> {

    private final URL url;
    private final HttpMode mode;
    private final String requestBody;
    private final List<HttpRequestNameValuePair> params;

    @DataBoundConstructor
    public RequestAction(URL url, HttpMode mode, String requestBody, List<HttpRequestNameValuePair> params) {
        this.url = url;
        this.mode = mode;
        this.requestBody = requestBody;
        this.params = params == null ? new ArrayList<HttpRequestNameValuePair>() : params;
    }

    public URL getUrl() {
        return url;
    }

    public HttpMode getMode() {
        return mode;
    }

    public List<HttpRequestNameValuePair> getParams() {
        return Collections.unmodifiableList(params);
    }

    public String getRequestBody() {
        return requestBody;
    }

    @Extension
    public static class ActionFormAuthenticationDescriptor extends Descriptor<RequestAction> {

        @Override
        public String getDisplayName() {
            return "Action Form Authentication";
        }

        public FormValidation doCheckUrl(@QueryParameter String value) {
            return HttpRequestValidation.checkUrl(value);
        }

        public FormValidation doCheckTimeout(@QueryParameter String timeout) {
            try {
                Integer.parseInt(timeout);
                return FormValidation.ok();
            } catch (NumberFormatException e) {
                return FormValidation.error("Not a number");
            }
        }

        public ListBoxModel doFillModeItems() {
            return HttpMode.getFillItems();
        }
    }
}
