package jenkins.plugins.http_request.util;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import jenkins.plugins.http_request.HttpRequest;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * @author Janario Oliveira
 */
public class NameValuePair extends AbstractDescribableImpl<NameValuePair>
        implements org.apache.http.NameValuePair {

    private final String name;
    private final String value;

    @DataBoundConstructor
    public NameValuePair(String name, String value) {
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
    public static class NameValueParamDescriptor extends Descriptor<NameValuePair> {

        @Override
        public String getDisplayName() {
            return "Name Value Param";
        }

        public String getHttpRequestHelpPath() {
            return "/descriptor/"+HttpRequest.class.getName()+"/help";
        }

        public FormValidation doCheckName(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckValue(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }
    }
}
