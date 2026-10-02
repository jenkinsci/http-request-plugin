package jenkins.plugins.http_request;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.umd.cs.findbugs.annotations.NonNull;

import net.sf.json.JSONObject;

import org.apache.hc.core5.http.HttpHeaders;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import jenkins.plugins.http_request.util.HttpRequestFormDataPart;
import jenkins.plugins.http_request.util.HttpRequestNameValuePair;

/**
 * A standalone post-build action that performs an HTTP request. It shares its request semantics
 * with {@link HttpRequest} and {@link HttpRequestStep}, but only permits the HTTP methods listed
 * in {@link HttpPostMode}.
 */
public class HttpRequestPublisher extends Recorder {
    private final @NonNull String url;
    private boolean ignoreSslErrors            = DescriptorImpl.ignoreSslErrors;
    private HttpMode httpMode              = DescriptorImpl.httpMode;
    private String httpProxy                   = DescriptorImpl.httpProxy;
    private String proxyAuthentication         = DescriptorImpl.proxyAuthentication;
    private String validResponseCodes          = DescriptorImpl.validResponseCodes;
    private String validResponseContent        = DescriptorImpl.validResponseContent;
    private MimeType acceptType                = DescriptorImpl.acceptType;
    private MimeType contentType               = DescriptorImpl.contentType;
    private String outputFile                  = DescriptorImpl.outputFile;
    private Integer timeout                    = DescriptorImpl.timeout;
    private Boolean consoleLogResponseBody     = DescriptorImpl.consoleLogResponseBody;
    private Boolean quiet                      = DescriptorImpl.quiet;
    private String authentication              = DescriptorImpl.authentication;
    private String requestBody                 = DescriptorImpl.requestBody;
    private String uploadFile                  = DescriptorImpl.uploadFile;
    private String multipartName               = DescriptorImpl.multipartName;
    private boolean wrapAsMultipart            = DescriptorImpl.wrapAsMultipart;
    private Boolean useSystemProperties        = DescriptorImpl.useSystemProperties;
    private boolean useNtlm                    = DescriptorImpl.useNtlm;
    private ResponseHandle responseHandle      = DescriptorImpl.responseHandle;
    private List<HttpRequestNameValuePair> customHeaders = DescriptorImpl.customHeaders;
    private List<HttpRequestFormDataPart> formData = DescriptorImpl.formData;

    @DataBoundConstructor
    public HttpRequestPublisher(@NonNull String url) {
        this.url = url;
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    public boolean isIgnoreSslErrors() {
        return ignoreSslErrors;
    }

    @DataBoundSetter
    public void setIgnoreSslErrors(boolean ignoreSslErrors) {
        this.ignoreSslErrors = ignoreSslErrors;
    }

    public HttpMode getHttpMode() {
        return httpMode;
    }

    @DataBoundSetter
    public void setHttpMode(HttpMode httpMode) {
        this.httpMode = httpMode;
    }

    public String getHttpProxy() {
        return httpProxy;
    }

    @DataBoundSetter
    public void setHttpProxy(String httpProxy) {
        this.httpProxy = httpProxy;
    }

    public String getProxyAuthentication() {
        return proxyAuthentication;
    }

    @DataBoundSetter
    public void setProxyAuthentication(String proxyAuthentication) {
        this.proxyAuthentication = proxyAuthentication;
    }

    @NonNull
    public String getValidResponseCodes() {
        return validResponseCodes;
    }

    @DataBoundSetter
    public void setValidResponseCodes(String validResponseCodes) {
        this.validResponseCodes = validResponseCodes;
    }

    public String getValidResponseContent() {
        return validResponseContent;
    }

    @DataBoundSetter
    public void setValidResponseContent(String validResponseContent) {
        this.validResponseContent = validResponseContent;
    }

    public MimeType getAcceptType() {
        return acceptType;
    }

    @DataBoundSetter
    public void setAcceptType(MimeType acceptType) {
        this.acceptType = acceptType;
    }

    public MimeType getContentType() {
        return contentType;
    }

    @DataBoundSetter
    public void setContentType(MimeType contentType) {
        this.contentType = contentType;
    }

    public String getOutputFile() {
        return outputFile;
    }

    @DataBoundSetter
    public void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
    }

    public Integer getTimeout() {
        return timeout;
    }

    @DataBoundSetter
    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Boolean getConsoleLogResponseBody() {
        return consoleLogResponseBody;
    }

    @DataBoundSetter
    public void setConsoleLogResponseBody(Boolean consoleLogResponseBody) {
        this.consoleLogResponseBody = consoleLogResponseBody;
    }

    public Boolean getQuiet() {
        return quiet;
    }

    @DataBoundSetter
    public void setQuiet(Boolean quiet) {
        this.quiet = quiet;
    }

    public String getAuthentication() {
        return authentication;
    }

    @DataBoundSetter
    public void setAuthentication(String authentication) {
        this.authentication = authentication;
    }

    public String getRequestBody() {
        return requestBody;
    }

    @DataBoundSetter
    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public Boolean getUseSystemProperties() {
        return useSystemProperties;
    }

    @DataBoundSetter
    public void setUseSystemProperties(Boolean useSystemProperties) {
        this.useSystemProperties = useSystemProperties;
    }

    public boolean isUseNtlm() {
        return useNtlm;
    }

    @DataBoundSetter
    public void setUseNtlm(boolean useNtlm) {
        this.useNtlm = useNtlm;
    }

    public ResponseHandle getResponseHandle() {
        return responseHandle;
    }

    @DataBoundSetter
    public void setResponseHandle(ResponseHandle responseHandle) {
        this.responseHandle = responseHandle;
    }

    public List<HttpRequestNameValuePair> getCustomHeaders() {
        return customHeaders;
    }

    @DataBoundSetter
    public void setCustomHeaders(List<HttpRequestNameValuePair> customHeaders) {
        this.customHeaders = customHeaders;
    }

    public List<HttpRequestFormDataPart> getFormData() {
        return formData;
    }

    @DataBoundSetter
    public void setFormData(List<HttpRequestFormDataPart> formData) {
        this.formData = Collections.unmodifiableList(formData);
    }

    public String getUploadFile() {
        return uploadFile;
    }

    @DataBoundSetter
    public void setUploadFile(String uploadFile) {
        this.uploadFile = uploadFile;
    }

    public String getMultipartName() {
        return multipartName;
    }

    @DataBoundSetter
    public void setMultipartName(String multipartName) {
        this.multipartName = multipartName;
    }

    public boolean isWrapAsMultipart() {
        return wrapAsMultipart;
    }

    @DataBoundSetter
    public void setWrapAsMultipart(boolean wrapAsMultipart) {
        this.wrapAsMultipart = wrapAsMultipart;
    }

    String resolveUrl(EnvVars envVars) {
        return envVars.expand(url);
    }

    String resolveBody(EnvVars envVars) {
        return envVars.expand(requestBody);
    }

    List<HttpRequestNameValuePair> resolveHeaders(EnvVars envVars) {
        final List<HttpRequestNameValuePair> headers = new ArrayList<>();
        if (contentType != null && contentType != MimeType.NOT_SET) {
            headers.add(new HttpRequestNameValuePair(HttpHeaders.CONTENT_TYPE, contentType.getContentType().toString()));
        }
        if (acceptType != null && acceptType != MimeType.NOT_SET) {
            headers.add(new HttpRequestNameValuePair(HttpHeaders.ACCEPT, acceptType.getValue()));
        }
        for (HttpRequestNameValuePair header : customHeaders) {
            String headerName = envVars.expand(header.getName());
            String headerValue = envVars.expand(header.getValue());
            boolean maskValue = headerName.equalsIgnoreCase(HttpHeaders.AUTHORIZATION) ||
                    header.getMaskValue();

            headers.add(new HttpRequestNameValuePair(headerName, headerValue, maskValue));
        }
        return headers;
    }

    FilePath resolveOutputFile(EnvVars envVars, AbstractBuild<?, ?> build) {
        if (outputFile == null || outputFile.trim().isEmpty()) {
            return null;
        }
        String filePath = envVars.expand(outputFile);
        FilePath workspace = build.getWorkspace();
        if (workspace == null) {
            throw new IllegalStateException("Could not find workspace to save file outputFile: " + outputFile);
        }
        return workspace.child(filePath);
    }

    FilePath resolveUploadFile(EnvVars envVars, AbstractBuild<?, ?> build) {
        return resolveUploadFileInternal(uploadFile, envVars, build);
    }

    private static FilePath resolveUploadFileInternal(String path, EnvVars envVars, AbstractBuild<?, ?> build) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        String filePath = envVars.expand(path);
        try {
            FilePath workspace = build.getWorkspace();
            if (workspace == null) {
                throw new IllegalStateException(
                        "Could not find workspace to check existence of upload file: " + path
                                + ". You should use it inside a 'node' block");
            }
            FilePath uploadFilePath = workspace.child(filePath);
            if (!uploadFilePath.exists()) {
                throw new IllegalStateException("Could not find upload file: " + path);
            }
            return uploadFilePath;
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    List<HttpRequestFormDataPart> resolveFormDataParts(EnvVars envVars, AbstractBuild<?, ?> build) {
        if (formData == null || formData.isEmpty()) {
            return Collections.emptyList();
        }

        List<HttpRequestFormDataPart> resolved = new ArrayList<>(formData.size());

        for (HttpRequestFormDataPart part : formData) {
            String name = envVars.expand(part.getName());
            String fileName = envVars.expand(part.getFileName());
            FilePath resolvedUploadFile =
                    resolveUploadFileInternal(part.getUploadFile(), envVars, build);
            String body = envVars.expand(part.getBody());

            HttpRequestFormDataPart newPart = new HttpRequestFormDataPart(part.getUploadFile(),
                    name, fileName, part.getContentType(), body);
            newPart.setResolvedUploadFile(resolvedUploadFile);
            resolved.add(newPart);
        }

        return resolved;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {

        EnvVars envVars = build.getEnvironment(listener);
        envVars.putAll(build.getBuildVariables());

        HttpRequestExecution exec = HttpRequestExecution.from(this, envVars, build,
                Boolean.TRUE.equals(quiet) ? TaskListener.NULL : listener);
        VirtualChannel channel = launcher.getChannel();
        if (channel == null) {
            throw new IllegalStateException("Launcher doesn't support remoting but it is required");
        }
        channel.call(exec);

        return true;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public static final boolean  ignoreSslErrors           = HttpRequest.DescriptorImpl.ignoreSslErrors;
        public static final HttpMode httpMode                  = HttpRequest.DescriptorImpl.httpMode;
        public static final String   httpProxy                 = HttpRequest.DescriptorImpl.httpProxy;
        public static final String   proxyAuthentication       = HttpRequest.DescriptorImpl.proxyAuthentication;
        public static final String   validResponseCodes        = HttpRequest.DescriptorImpl.validResponseCodes;
        public static final String   validResponseContent      = HttpRequest.DescriptorImpl.validResponseContent;
        public static final MimeType acceptType                = HttpRequest.DescriptorImpl.acceptType;
        public static final MimeType contentType               = HttpRequest.DescriptorImpl.contentType;
        public static final int      timeout                   = HttpRequest.DescriptorImpl.timeout;
        public static final Boolean  consoleLogResponseBody    = HttpRequest.DescriptorImpl.consoleLogResponseBody;
        public static final Boolean  quiet                     = HttpRequest.DescriptorImpl.quiet;
        public static final String   authentication            = HttpRequest.DescriptorImpl.authentication;
        public static final String   requestBody               = HttpRequest.DescriptorImpl.requestBody;
        public static final String   uploadFile                = HttpRequest.DescriptorImpl.uploadFile;
        public static final String   multipartName             = HttpRequest.DescriptorImpl.multipartName;
        public static final boolean  wrapAsMultipart           = HttpRequest.DescriptorImpl.wrapAsMultipart;
        public static final Boolean  useSystemProperties       = HttpRequest.DescriptorImpl.useSystemProperties;
        public static final boolean  useNtlm                   = HttpRequest.DescriptorImpl.useNtlm;
        public static final List<HttpRequestNameValuePair> customHeaders = Collections.emptyList();
        public static final List<HttpRequestFormDataPart> formData = Collections.emptyList();
        public static final String   outputFile                = "";
        public static final ResponseHandle responseHandle      = ResponseHandle.STRING;

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "HTTP Request";
        }

        @Override
        public Publisher newInstance(StaplerRequest2 req, @NonNull JSONObject formData) throws FormException {
			return (HttpRequestPublisher) super.newInstance(req, formData);
        }

        public ListBoxModel doFillHttpModeItems() {
            return HttpMode.getPublisherFillItems();
        }

        public ListBoxModel doFillAcceptTypeItems() {
            return MimeType.getContentTypeFillItems();
        }

        public ListBoxModel doFillContentTypeItems() {
            return MimeType.getContentTypeFillItems();
        }

        public ListBoxModel doFillResponseHandleItems() {
            ListBoxModel items = new ListBoxModel();
            for (ResponseHandle responseHandle : ResponseHandle.values()) {
                items.add(responseHandle.name());
            }
            return items;
        }

        public ListBoxModel doFillAuthenticationItems(@AncestorInPath Item project,
                                                      @QueryParameter String url) {
            return HttpRequest.DescriptorImpl.fillAuthenticationItems(project, url);
        }

        public ListBoxModel doFillProxyAuthenticationItems(@AncestorInPath Item project,
                                                           @QueryParameter String url) {
            return HttpRequest.DescriptorImpl.fillAuthenticationItems(project, url);
        }

        public FormValidation doCheckValidResponseCodes(@QueryParameter String value) {
            return HttpRequest.DescriptorImpl.checkValidResponseCodes(value);
        }
    }
}
