package jenkins.plugins.http_request.util;

import java.io.Serializable;
import java.util.Map;

import org.apache.http.NameValuePair;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
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

	private static final long serialVersionUID = -5179602567301232134L;
	private final String name;
    private final String value;
    private boolean maskValue;

    @DataBoundConstructor
    public HttpRequestNameValuePair(String name, String value, boolean maskValue) {
        this.name = name;
        this.value = value;
        this.maskValue = maskValue;
    }

    public HttpRequestNameValuePair(String name, String value) {
        this(name, value, false);
    }
    
    /**
     * Constructor that accepts a Map.Entry to support headers with special characters in their names
     * @param entry Map.Entry containing the header name and value
     */
    public HttpRequestNameValuePair(Map.Entry<String, String> entry) {
        this(entry.getKey(), entry.getValue(), false);
    }
    
    /**
     * Constructor that accepts a header name and value directly
     * This allows headers with special characters (like hyphens) to be created more easily
     * @param name The header name (can contain special characters)
     * @param value The header value
     * @param maskValue Whether to mask the value in logs
     * @return A new HttpRequestNameValuePair
     */
    public static HttpRequestNameValuePair create(String name, String value, boolean maskValue) {
        return new HttpRequestNameValuePair(name, value, maskValue);
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
    
    @DataBoundSetter
    public void setMaskValue(boolean maskValue) {
        this.maskValue = maskValue;
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
