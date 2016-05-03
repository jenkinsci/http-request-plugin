package jenkins.plugins.http_request;

import static com.google.common.base.Preconditions.checkArgument;
import com.google.common.collect.Range;
import com.google.common.collect.Ranges;
import com.google.common.primitives.Ints;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import javax.inject.Inject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import jenkins.plugins.http_request.HttpRequest;
import jenkins.plugins.http_request.auth.Authenticator;
import jenkins.plugins.http_request.auth.BasicDigestAuthentication;
import jenkins.plugins.http_request.auth.FormAuthentication;
import jenkins.plugins.http_request.util.HttpRequestNameValuePair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;

import net.sf.json.JSONObject;

/**
 * @author Martin d'Anjou
 */
public final class HttpRequestStep extends AbstractStepImpl {

    private @Nonnull String url;
    private HttpMode httpMode                 = DescriptorImpl.httpMode;
    private String validResponseCodes         = DescriptorImpl.validResponseCodes;
    private String validResponseContent       = DescriptorImpl.validResponseContent;
    private MimeType acceptType               = DescriptorImpl.acceptType;
    private MimeType contentType              = DescriptorImpl.contentType;
    private Integer timeout                   = DescriptorImpl.timeout;
    private Boolean consoleLogResponseBody    = DescriptorImpl.consoleLogResponseBody;
    private String authentication             = DescriptorImpl.authentication;
    private String requestBody                = DescriptorImpl.requestBody;
    private List<HttpRequestNameValuePair> customHeaders = DescriptorImpl.customHeaders;

    @DataBoundConstructor
    public HttpRequestStep(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    @DataBoundSetter
    public void setHttpMode(HttpMode httpMode) {
        this.httpMode = httpMode;
    }

    public HttpMode getHttpMode() {
        return httpMode;
    }

    @DataBoundSetter
    public void setValidResponseCodes(String validResponseCodes) {
        this.validResponseCodes = validResponseCodes;
    }

    public String getValidResponseCodes() {
        return validResponseCodes;
    }

    @DataBoundSetter
    public void setValidResponseContent(String validResponseContent) {
        this.validResponseContent = validResponseContent;
    }

    public String getValidResponseContent() {
        return validResponseContent;
    }

    @DataBoundSetter
    public void setAcceptType(MimeType acceptType) {
        this.acceptType = acceptType;
    }

    public MimeType getAcceptType() {
        return acceptType;
    }

    @DataBoundSetter
    public void setContentType(MimeType contentType) {
        this.contentType = contentType;
    }

    public MimeType getContentType() {
        return contentType;
    }

    @DataBoundSetter
    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Integer getTimeout() {
        return timeout;
    }

    @DataBoundSetter
    public void setConsoleLogResponseBody(Boolean consoleLogResponseBody) {
        this.consoleLogResponseBody = consoleLogResponseBody;
    }

    public Boolean getConsoleLogResponseBody() {
        return consoleLogResponseBody;
    }

    @DataBoundSetter
    public void setAuthentication(String authentication) {
        this.authentication = authentication;
    }

    public String getAuthentication() {
        return authentication;
    }

    @DataBoundSetter
    public void setCustomHeaders(List<HttpRequestNameValuePair> customHeaders) {
        this.customHeaders = customHeaders;
    }

    public List<HttpRequestNameValuePair> getCustomHeaders() {
        return customHeaders;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }
    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {
        public static final HttpMode httpMode                  = HttpRequest.DescriptorImpl.httpMode;
        public static final String   validResponseCodes        = HttpRequest.DescriptorImpl.validResponseCodes;
        public static final String   validResponseContent      = HttpRequest.DescriptorImpl.validResponseContent;
        public static final MimeType acceptType                = HttpRequest.DescriptorImpl.acceptType;
        public static final MimeType contentType               = HttpRequest.DescriptorImpl.contentType;
        public static final int      timeout                   = HttpRequest.DescriptorImpl.timeout;
        public static final Boolean  consoleLogResponseBody    = HttpRequest.DescriptorImpl.consoleLogResponseBody;
        public static final String   authentication            = HttpRequest.DescriptorImpl.authentication;
        public static final String   requestBody               = HttpRequest.DescriptorImpl.requestBody;
        public static final List <HttpRequestNameValuePair> customHeaders = Collections.<HttpRequestNameValuePair>emptyList();

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override
        public String getFunctionName() {
            return "httpRequest";
        }

        @Override
        public String getDisplayName() {
            return "Perform an HTTP Request and return a response object";
        }

        public ListBoxModel doFillHttpModeItems() {
            return HttpMode.getFillItems();
        }

        public ListBoxModel doFillAcceptTypeItems() {
            return MimeType.getContentTypeFillItems();
        }

        public ListBoxModel doFillContentTypeItems() {
            return MimeType.getContentTypeFillItems();
        }

        public ListBoxModel doFillAuthenticationItems() {
            return HttpRequest.DescriptorImpl.fillAuthenticationItems();
        }

        public FormValidation doValidateKeyName(@QueryParameter String value) {
            return HttpRequest.DescriptorImpl.validateKeyName(value);
        }

        public FormValidation doCheckValidResponseCodes(@QueryParameter String value) {
            return HttpRequest.DescriptorImpl.checkValidResponseCodes(value);
        }

    }

    public static final class Execution extends AbstractSynchronousNonBlockingStepExecution<ResponseContentSupplier> {

        @Inject
        private transient HttpRequestStep step;

        @StepContextParameter
        private transient Run run;

        @StepContextParameter
        private transient TaskListener listener;

        @Override
        protected ResponseContentSupplier run() throws Exception {
            HttpRequest httpRequest = new HttpRequest(step.url);
            httpRequest.setHttpMode(step.httpMode);
            httpRequest.setConsoleLogResponseBody(step.consoleLogResponseBody);
            httpRequest.setValidResponseCodes(step.validResponseCodes);
            httpRequest.setValidResponseContent(step.validResponseContent);
            httpRequest.setAcceptType(step.acceptType);
            httpRequest.setContentType(step.contentType);
            httpRequest.setTimeout(step.timeout);
            httpRequest.setConsoleLogResponseBody(step.consoleLogResponseBody);
            httpRequest.setAuthentication(step.authentication);
            httpRequest.setRequestBody(step.requestBody);
            httpRequest.setCustomHeaders(step.customHeaders);
            ResponseContentSupplier response = httpRequest.performHttpRequest(run, listener);
            return response;
        }

        private static final long serialVersionUID = 1L;

    }
}
