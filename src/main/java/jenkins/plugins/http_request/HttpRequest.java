package jenkins.plugins.http_request;

import static com.google.common.base.Preconditions.checkArgument;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.base.Objects;
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
import net.sf.json.JSONObject;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.SystemDefaultHttpClient;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Janario Oliveira
 */
public class HttpRequest extends Builder {

    private final String name;
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

    /**
     * @deprecated only to deserialize and serialize in a new form
     */
    @Deprecated
    private Boolean returnCodeBuildRelevant;

    @DataBoundConstructor
    public HttpRequest(String name, String url, HttpMode httpMode, String authentication, MimeType contentType,
                       MimeType acceptType, String outputFile, Boolean returnCodeBuildRelevant,
                       Boolean consoleLogResponseBody, Boolean passBuildParameters,
                       List<NameValuePair> customHeaders, Integer timeout,
                       String validResponseCodes)
                       throws URISyntaxException {
        this.name = name;
        this.url = url;
        this.contentType = contentType;
        this.acceptType = acceptType;
        this.outputFile = outputFile;
        this.httpMode = httpMode;
        this.customHeaders = customHeaders;
        this.validResponseCodes = validResponseCodes;
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

    public String getName() {
        return name;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public String getValidResponseCodes() {
        return validResponseCodes;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        defineDefaultConfigurations();

        final PrintStream logger = listener.getLogger();

        if (!name.isEmpty())  {
            logger.println("Name: " + name);
        }
        logger.println("HttpMode: " + httpMode);

        final SystemDefaultHttpClient httpclient = new SystemDefaultHttpClient();

        final EnvVars envVars = build.getEnvironment(listener);

        if (!envVars.isEmpty())
            logger.println("Parameters: ");

        final List<NameValuePair> params = createParameters(build, logger, envVars);
        String evaluatedUrl = evaluate(url, build.getBuildVariableResolver(), envVars);
        logger.println(String.format("URL: %s", evaluatedUrl));
        final RequestAction requestAction;
        if (passBuildParameters) {
            requestAction = new RequestAction(name, new URL(evaluatedUrl), httpMode, params);
        } else {
            requestAction = new RequestAction(name, new URL(evaluatedUrl), httpMode, null);
        }
        final HttpClientUtil clientUtil = new HttpClientUtil();
        if(outputFile != null && !outputFile.isEmpty()) {
            FilePath outputFilePath = build.getWorkspace().child(outputFile);
            clientUtil.setOutputFile(outputFilePath);
        }
        final HttpRequestBase httpRequestBase = clientUtil.createRequestBase(requestAction);

        if (contentType != MimeType.NOT_SET) {
            httpRequestBase.setHeader("Content-type", contentType.getValue());
            logger.println("Content-type: " + contentType);
        }

        if (acceptType != MimeType.NOT_SET){
            httpRequestBase.setHeader("Accept", acceptType.getValue());
            logger.println("Accept: " + acceptType);
        }

        for (NameValuePair header : customHeaders) {
            httpRequestBase.addHeader(header.getName(), header.getValue());
        }

        if (authentication != null) {
            final Authenticator auth = getDescriptor().getAuthentication(authentication);
            if (auth == null) {
                throw new IllegalStateException("Authentication " + authentication + " doesn't exists anymore");
            }

            logger.println("Using authentication: " + auth.getKeyName());
            auth.authenticate(httpclient, httpRequestBase, logger, timeout);
        }

        final HttpResponse execute = clientUtil.execute(httpclient, httpRequestBase, logger, consoleLogResponseBody, timeout);

        boolean successCode = false;
        List<Range<Integer>> ranges = getDescriptor().parseToRange(validResponseCodes);
        for (Range<Integer> range : ranges) {
            if (range.contains(execute.getStatusLine().getStatusCode())) {
                logger.println("Success code from " + range);
                successCode = true;
                break;
            }
        }
        if (!successCode) {
            logger.println("Fail: Any code list (" + ranges + ") match the returned code " + execute.getStatusLine().getStatusCode());
        }
        return successCode;
    }

    private List<NameValuePair> createParameters(
            AbstractBuild<?, ?> build, PrintStream logger,
            EnvVars envVars) {
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
                checkArgument(from !=null , "Invalid number %s", fromTo[0]);

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
}
