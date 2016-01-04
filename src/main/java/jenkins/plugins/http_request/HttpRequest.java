package jenkins.plugins.http_request;

import static com.google.common.base.Preconditions.checkArgument;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.Range;
import com.google.common.collect.Ranges;
import com.google.common.primitives.Ints;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Items;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.VariableResolver;
import jenkins.plugins.http_request.auth.Authenticator;
import jenkins.plugins.http_request.auth.BasicDigestAuthentication;
import jenkins.plugins.http_request.auth.FormAuthentication;
import jenkins.plugins.http_request.util.HttpClientUtil;
import jenkins.plugins.http_request.util.NameValuePair;
import jenkins.plugins.http_request.util.RequestAction;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.SystemDefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Janario Oliveira
 */
public class HttpRequest extends Builder implements SimpleBuildStep {

    private final String url;
    private HttpMode httpMode;
    private MimeType contentType;
    private MimeType acceptType;
    private final String outputFile;
    private final String authentication;
    private Boolean consoleLogResponseBody;
    private Boolean passBuildParameters;
    private List<NameValuePair> customHeaders = new ArrayList<NameValuePair>();
    private final Integer timeout;
    private String validResponseCodes;
    private String validResponseContent;

    /**
     * @deprecated only to deserialize and serialize in a new form
     */
    @Deprecated
    private Boolean returnCodeBuildRelevant;

    @DataBoundConstructor
    public HttpRequest(String url, HttpMode httpMode, String authentication, MimeType contentType,
                       MimeType acceptType, String outputFile, Boolean returnCodeBuildRelevant,
                       Boolean consoleLogResponseBody, Boolean passBuildParameters,
                       List<NameValuePair> customHeaders, Integer timeout,
                       String validResponseCodes, String validResponseContent)
            throws URISyntaxException {
        this.url = url;
        this.contentType = contentType;
        this.acceptType = acceptType;
        this.outputFile = outputFile;
        this.httpMode = httpMode;
        this.customHeaders = customHeaders;
        this.validResponseCodes = validResponseCodes;
        this.validResponseContent = validResponseContent;
        this.authentication = Util.fixEmpty(authentication);
        this.consoleLogResponseBody = consoleLogResponseBody;
        this.passBuildParameters = passBuildParameters;
        this.timeout = timeout;

        this.returnCodeBuildRelevant = returnCodeBuildRelevant;
    }

    @Initializer(before = InitMilestone.PLUGINS_STARTED)
    public static void xStreamCompatibility() {
        Items.XSTREAM2.aliasField("logResponseBody", HttpRequest.class, "consoleLogResponseBody");
        Items.XSTREAM2.aliasField("consoleLogResponseBody", HttpRequest.class, "consoleLogResponseBody");
        Items.XSTREAM2.alias("pair", NameValuePair.class);
    }

    public Object readResolve() {
        defineDefaultConfigurations();
        return this;
    }

    public void defineDefaultConfigurations() {
        returnCodeBuildRelevant = Objects.firstNonNull(returnCodeBuildRelevant, getDescriptor().defaultReturnCodeBuildRelevant);
        consoleLogResponseBody = Objects.firstNonNull(consoleLogResponseBody, getDescriptor().defaultLogResponseBody);
        httpMode = Objects.firstNonNull(httpMode, getDescriptor().defaultHttpMode);

        contentType = Objects.firstNonNull(contentType, MimeType.NOT_SET);
        acceptType = Objects.firstNonNull(acceptType, MimeType.NOT_SET);
        passBuildParameters = Objects.firstNonNull(passBuildParameters, true);
        customHeaders = Objects.firstNonNull(customHeaders, Collections.<NameValuePair>emptyList());

        if (validResponseCodes == null || validResponseCodes.trim().isEmpty()) {
            validResponseCodes = returnCodeBuildRelevant ? "100:399" : "100:599";
        }
    }

    public Boolean getConsoleLogResponseBody() {
        return consoleLogResponseBody;
    }

    public String getUrl() {
        return url;
    }

    public HttpMode getHttpMode() {
        return httpMode;
    }

    public MimeType getContentType() {
        return contentType;
    }

    public MimeType getAcceptType() {
        return acceptType;
    }

    public List<NameValuePair> getCustomHeaders() {
        return customHeaders;
    }

    public String getOutputFile() {
        return outputFile;
    }

    public String getAuthentication() {
        return authentication;
    }

    public Boolean getPassBuildParameters() {
        return passBuildParameters;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public String getValidResponseCodes() {
        return validResponseCodes;
    }

    public String getValidResponseContent() {
        return validResponseContent;
    }

    @Deprecated
    public Boolean getReturnCodeBuildRelevant() {
        return returnCodeBuildRelevant;
    }

    @Override
    public void perform(Run<?,?> run, FilePath workspace, Launcher launcher, TaskListener listener)
    throws InterruptedException, IOException
    {
        defineDefaultConfigurations();

        final PrintStream logger = listener.getLogger();
        logger.println("HttpMode: " + httpMode);


        final EnvVars envVars = run.getEnvironment(listener);
        final List<NameValuePair> params = createParameters(run, logger, envVars);
        String evaluatedUrl;
        if (run instanceof AbstractBuild<?, ?>) {
            final AbstractBuild<?, ?> build = (AbstractBuild<?, ?>) run;
            evaluatedUrl = evaluate(url, build.getBuildVariableResolver(), envVars);
        } else {
            evaluatedUrl = url;
        }
        logger.println(String.format("URL: %s", evaluatedUrl));


        DefaultHttpClient httpclient = new SystemDefaultHttpClient();
        RequestAction requestAction = new RequestAction(new URL(evaluatedUrl), httpMode, params);
        HttpClientUtil clientUtil = new HttpClientUtil();
        HttpRequestBase httpRequestBase = getHttpRequestBase(logger, requestAction, clientUtil);
        HttpContext context = new BasicHttpContext();

        if (authentication != null) {
            final Authenticator auth = getDescriptor().getAuthentication(authentication);
            if (auth == null) {
                throw new IllegalStateException("Authentication " + authentication + " doesn't exists anymore");
            }

            logger.println("Using authentication: " + auth.getKeyName());
            auth.authenticate(httpclient, context, httpRequestBase, logger, timeout);
        }
        final HttpResponse response = clientUtil.execute(httpclient, context, httpRequestBase, logger, timeout);

        try {
            ResponseContentSupplier responseContentSupplier = new ResponseContentSupplier(response);
            logResponse(workspace, logger, responseContentSupplier);

            return; // How to deal with this -> responseCodeIsValid(response, logger) && contentIsValid(responseContentSupplier, logger);
        } finally {
            EntityUtils.consume(response.getEntity());
        }
    }

    private boolean contentIsValid(ResponseContentSupplier responseContentSupplier, PrintStream logger) {
        if (Strings.isNullOrEmpty(validResponseContent)) {
            return true;
        }

        String response = responseContentSupplier.get();
        if (!response.contains(validResponseContent)) {
            logger.println("Fail: Response with length " + response.length() + " doesn't contain '" + validResponseContent + "'");
            return false;
        }
        return true;
    }

    private boolean responseCodeIsValid(HttpResponse response, PrintStream logger) {
        List<Range<Integer>> ranges = getDescriptor().parseToRange(validResponseCodes);
        for (Range<Integer> range : ranges) {
            if (range.contains(response.getStatusLine().getStatusCode())) {
                logger.println("Success code from " + range);
                return true;
            }
        }
        logger.println("Fail: Any code list (" + ranges + ") match the returned code " + response.getStatusLine().getStatusCode());
        return false;

    }

    private void logResponse(FilePath workspace, PrintStream logger, ResponseContentSupplier responseContentSupplier) throws IOException, InterruptedException {

        if (consoleLogResponseBody || workspace != null) {
            if (consoleLogResponseBody) {
                logger.println("Response: \n" + responseContentSupplier.get());
            }
            if (workspace != null && responseContentSupplier.get() != null) {
                OutputStream write = null;
                try {
                    write = workspace.write();
                    write.write(responseContentSupplier.get().getBytes());
                } finally {
                    if (write != null) {
                        write.close();
                    }
                }
            }
        }
    }

    private HttpRequestBase getHttpRequestBase(PrintStream logger, RequestAction requestAction, HttpClientUtil clientUtil) throws IOException {
        HttpRequestBase httpRequestBase = clientUtil.createRequestBase(requestAction);

        if (contentType != MimeType.NOT_SET) {
            httpRequestBase.setHeader("Content-type", contentType.getValue());
            logger.println("Content-type: " + contentType);
        }

        if (acceptType != MimeType.NOT_SET) {
            httpRequestBase.setHeader("Accept", acceptType.getValue());
            logger.println("Accept: " + acceptType);
        }

        for (NameValuePair header : customHeaders) {
            httpRequestBase.addHeader(header.getName(), header.getValue());
        }
        return httpRequestBase;
    }

    private List<NameValuePair> createParameters(
            Run<?, ?> run, PrintStream logger,
            EnvVars envVars) {
        if (!passBuildParameters) {
            return Collections.emptyList();
        }

        if (!(run instanceof AbstractBuild<?, ?>)) {
            return Collections.emptyList();
        }
        final AbstractBuild<?, ?> build = (AbstractBuild<?, ?>) run;

        if (!envVars.isEmpty()) {
            logger.println("Parameters: ");
        }

        final VariableResolver<String> vars = build.getBuildVariableResolver();

        List<NameValuePair> l = new ArrayList<NameValuePair>();
        for (Map.Entry<String, String> entry : build.getBuildVariables().entrySet()) {
            String value = evaluate(entry.getValue(), vars, envVars);
            logger.println("  " + entry.getKey() + " = " + value);

            l.add(new NameValuePair(entry.getKey(), value));
        }

        return l;
    }

    private String evaluate(String value, VariableResolver<String> vars, Map<String, String> env) {
        return Util.replaceMacro(Util.replaceMacro(value, vars), env);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        private HttpMode defaultHttpMode = HttpMode.POST;
        private List<BasicDigestAuthentication> basicDigestAuthentications = new ArrayList<BasicDigestAuthentication>();
        private List<FormAuthentication> formAuthentications = new ArrayList<FormAuthentication>();
        private boolean defaultReturnCodeBuildRelevant = true;
        private boolean defaultLogResponseBody = true;

        public DescriptorImpl() {
            load();
        }

        public boolean isDefaultLogResponseBody() {
            return defaultLogResponseBody;
        }

        public void setDefaultLogResponseBody(boolean defaultLogResponseBody) {
            this.defaultLogResponseBody = defaultLogResponseBody;
        }

        public HttpMode getDefaultHttpMode() {
            return defaultHttpMode;
        }

        public boolean getDefaultLogResponseBody() {
            return defaultLogResponseBody;
        }

        public void setDefaultHttpMode(HttpMode defaultHttpMode) {
            this.defaultHttpMode = defaultHttpMode;
        }

        public List<BasicDigestAuthentication> getBasicDigestAuthentications() {
            return basicDigestAuthentications;
        }

        public void setBasicDigestAuthentications(
                List<BasicDigestAuthentication> basicDigestAuthentications) {
            this.basicDigestAuthentications = basicDigestAuthentications;
        }

        public List<FormAuthentication> getFormAuthentications() {
            return formAuthentications;
        }

        public void setFormAuthentications(
                List<FormAuthentication> formAuthentications) {
            this.formAuthentications = formAuthentications;
        }

        public List<Authenticator> getAuthentications() {
            List<Authenticator> list = new ArrayList<Authenticator>();
            list.addAll(basicDigestAuthentications);
            list.addAll(formAuthentications);
            return list;
        }

        public Authenticator getAuthentication(String keyName) {
            for (Authenticator authenticator : getAuthentications()) {
                if (authenticator.getKeyName().equals(keyName)) {
                    return authenticator;
                }
            }
            return null;
        }

        public boolean isDefaultReturnCodeBuildRelevant() {
            return defaultReturnCodeBuildRelevant;
        }

        public void setDefaultReturnCodeBuildRelevant(boolean defaultReturnCodeBuildRelevant) {
            this.defaultReturnCodeBuildRelevant = defaultReturnCodeBuildRelevant;
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "HTTP Request";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws
                FormException {
            req.bindJSON(this, formData);
            save();
            return true;
        }

        public ListBoxModel doFillDefaultHttpModeItems() {
            return HttpMode.getFillItems();
        }

        public ListBoxModel doFillHttpModeItems() {
            return HttpMode.getFillItems();
        }

        public ListBoxModel doFillDefaultContentTypeItems() {
            return MimeType.getContentTypeFillItems();
        }

        public ListBoxModel doFillContentTypeItems() {
            ListBoxModel items = MimeType.getContentTypeFillItems();
            return items;
        }

        public ListBoxModel doFillDefaultAcceptTypeItems() {
            return MimeType.getContentTypeFillItems();
        }

        public ListBoxModel doFillAcceptTypeItems() {
            ListBoxModel items = MimeType.getContentTypeFillItems();
            return items;
        }

        public ListBoxModel doFillAuthenticationItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("");
            for (BasicDigestAuthentication basicDigestAuthentication : basicDigestAuthentications) {
                items.add(basicDigestAuthentication.getKeyName());
            }
            for (FormAuthentication formAuthentication : formAuthentications) {
                items.add(formAuthentication.getKeyName());
            }

            return items;
        }

        public FormValidation doCheckUrl(@QueryParameter String value)
                throws IOException, ServletException {
            return FormValidation.ok();
            // return HttpRequestValidation.checkUrl(value);
        }

        public FormValidation doValidateKeyName(@QueryParameter String value) {
            List<Authenticator> list = getAuthentications();

            int count = 0;
            for (Authenticator basicAuthentication : list) {
                if (basicAuthentication.getKeyName().equals(value)) {
                    count++;
                }
            }

            if (count > 1) {
                return FormValidation.error("The Key Name must be unique");
            }

            return FormValidation.validateRequired(value);
        }

        List<Range<Integer>> parseToRange(String value) {
            List<Range<Integer>> validRanges = new ArrayList<Range<Integer>>();

            String[] codes = value.split(",");
            for (String code : codes) {
                String[] fromTo = code.trim().split(":");
                checkArgument(fromTo.length <= 2, "Code %s should be an interval from:to or a single value", code);
                Integer from = Ints.tryParse(fromTo[0]);
                checkArgument(from != null, "Invalid number %s", fromTo[0]);

                Integer to = from;
                if (fromTo.length != 1) {
                    to = Ints.tryParse(fromTo[1]);
                    checkArgument(to != null, "Invalid number %s", fromTo[1]);
                }

                checkArgument(from <= to, "Interval %s should be FROM less than TO", code);
                validRanges.add(Ranges.closed(from, to));
            }

            return validRanges;
        }

        public FormValidation doCheckValidResponseCodes(@QueryParameter String value) {
            if (value == null || value.trim().isEmpty()) {
                return FormValidation.ok();
            }

            try {
                parseToRange(value);
            } catch (IllegalArgumentException iae) {
                return FormValidation.error(iae.getMessage());
            }
            return FormValidation.ok();
        }
    }

    private class ResponseContentSupplier implements Supplier<String> {

        private String content;
        private final HttpResponse response;

        private ResponseContentSupplier(HttpResponse response) {
            this.response = response;
        }

        public String get() {
            try {
                if (content == null) {
                    content = EntityUtils.toString(response.getEntity());
                }
                return content;
            } catch (IOException e) {
                return null;
            }
        }
    }
}
