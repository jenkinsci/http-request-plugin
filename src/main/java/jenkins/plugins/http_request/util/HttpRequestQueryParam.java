package jenkins.plugins.http_request.util;

import java.io.Serializable;

import org.apache.http.NameValuePair;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

public class HttpRequestQueryParam extends AbstractDescribableImpl<HttpRequestQueryParam>
        implements NameValuePair, Serializable {

    private static final long serialVersionUID = 1L;
    private final String name;
    private String value;

    @DataBoundConstructor
    public HttpRequestQueryParam(String name) {
        this.name = name;
        this.value = "";
    }

    public HttpRequestQueryParam(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    @DataBoundSetter
    public void setValue(String value) {
        this.value = value;
    }

    @Extension
    public static class QueryParamDescriptor extends Descriptor<HttpRequestQueryParam> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Query Param";
        }

        public FormValidation doCheckName(@QueryParameter String name) {
            return FormValidation.validateRequired(name);
        }
    }
}
