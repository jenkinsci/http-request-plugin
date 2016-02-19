package jenkins.plugins.http_request.util;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import jenkins.plugins.http_request.HttpRequest;
import org.apache.http.NameValuePair;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * @author Janario Oliveira
 */
public class HttpRequestNameValuePair extends AbstractDescribableImpl<HttpRequestNameValuePair>
        implements NameValuePair {

    private final String name;
    private final String value;

    @DataBoundConstructor
    public HttpRequestNameValuePair(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    @Extension
    public static class NameValueParamDescriptor extends Descriptor<HttpRequestNameValuePair> {

        @Override
        public String getDisplayName() {
            return "Name Value Param";
        }

        public FormValidation doCheckName(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckValue(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }
    }
}
