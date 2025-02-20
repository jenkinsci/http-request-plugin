package jenkins.plugins.http_request.util;

import java.io.Serial;
import java.io.Serializable;

import org.apache.hc.core5.http.NameValuePair;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

/**
 * @author Janario Oliveira
 */
public class HttpRequestNameValuePair extends AbstractDescribableImpl<HttpRequestNameValuePair>
        implements NameValuePair, Serializable {

    @Serial
    private static final long serialVersionUID = -5179602567301232134L;
    private final String name;
    private final String value;
    private final boolean maskValue;

    @DataBoundConstructor
    public HttpRequestNameValuePair(String name, String value, boolean maskValue) {
        this.name = name;
        this.value = value;
        this.maskValue = maskValue;
    }

    public HttpRequestNameValuePair(String name, String value) {
        this(name, value, false);
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public boolean getMaskValue() {
        return maskValue;
    }

    @Extension
    public static class NameValueParamDescriptor extends Descriptor<HttpRequestNameValuePair> {

        @NonNull
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
