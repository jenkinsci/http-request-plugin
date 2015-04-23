package jenkins.plugins.http_request;

import static com.google.common.base.Preconditions.checkArgument;

import javax.servlet.ServletException;
import java.io.*;
import java.net.URISyntaxException;
import java.util.*;

import com.google.common.base.Supplier;
import com.google.common.collect.Range;
import com.google.common.collect.Ranges;
import com.google.common.primitives.Ints;
import hudson.Extension;
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
import jenkins.plugins.http_request.util.NameValuePair;
import net.sf.json.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.SystemDefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Janario Oliveira
 */
public class HttpRequest extends Builder {

    private String team;
    private String application;
    private String artifactName;
    private String artifactSource;
    private String state;
    private List<NameValuePair> tags = new ArrayList<NameValuePair>();
    private String baseUrl = "http://localhost:9000";

    @DataBoundConstructor
    public HttpRequest(String team, String application, String artifactName, String state, List<NameValuePair> tags, String artifactSource)
            throws URISyntaxException {

        this.team = team;
        this.application = application;
        this.artifactName = artifactName;
        this.state = state;
        this.tags = tags;
        this.artifactSource = artifactSource;

//        this.team = "testTeam";
//        this.application = "testApplication";
//        this.artifactName = "testArtifactName.zip";
//        this.artifactSource = "testArtifactName.zip";
    }

    @Initializer(before = InitMilestone.PLUGINS_STARTED)
    public static void xStreamCompatibility() {
        Items.XSTREAM2.aliasField("logResponseBody", HttpRequest.class, "consoleLogResponseBody");
        Items.XSTREAM2.aliasField("consoleLogResponseBody", HttpRequest.class, "consoleLogResponseBody");
        Items.XSTREAM2.alias("pair", NameValuePair.class);
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
//        defineDefaultConfigurations();
//
//        final PrintStream logger = listener.getLogger();
//        logger.println("HttpMode: " + httpMode);
//
//
//        final EnvVars envVars = build.getEnvironment(listener);
//        final List<NameValuePair> params = createParameters(build, logger, envVars);
//        String evaluatedUrl = evaluate(url, build.getBuildVariableResolver(), envVars);
//        logger.println(String.format("URL: %s", evaluatedUrl));
//
//
//        DefaultHttpClient httpclient = new SystemDefaultHttpClient();
//        RequestAction requestAction = new RequestAction(new URL(evaluatedUrl), httpMode, params);
//        HttpClientUtil clientUtil = new HttpClientUtil();
//        HttpRequestBase httpRequestBase = getHttpRequestBase(logger, requestAction, clientUtil);
//        HttpContext context = new BasicHttpContext();
//
//        if (authentication != null) {
//            final Authenticator auth = getDescriptor().getAuthentication(authentication);
//            if (auth == null) {
//                throw new IllegalStateException("Authentication " + authentication + " doesn't exists anymore");
//            }
//
//            logger.println("Using authentication: " + auth.getKeyName());
//            auth.authenticate(httpclient, context, httpRequestBase, logger, timeout);
//        }
//        final HttpResponse response = clientUtil.execute(httpclient, context, httpRequestBase, logger, timeout);
//
//        try {
//            ResponseContentSupplier responseContentSupplier = new ResponseContentSupplier(response);
//            logResponse(build, logger, responseContentSupplier);
//
//            return responseCodeIsValid(response, logger) && contentIsValid(responseContentSupplier, logger);
//        } finally {
//            EntityUtils.consume(response.getEntity());
//        }
        return weather();
    }

    private boolean createNewArtifact() throws IOException {
        DefaultHttpClient client = new SystemDefaultHttpClient();
        HttpPost metadataPost = getMetadataPostRequest();
        HttpResponse metadataResponse = client.execute(metadataPost);
        if(requestWasSuccessful(metadataResponse)) {
//            HttpPost artifactPost = getArtifactPostRequest();
//            HttpResponse artifactResponse = client.execute(artifactPost);
//            return requestWasSuccessful(artifactResponse);
            return true;
        }
        else {
            return false;
        }
    }

    private boolean weather() throws IOException {
        DefaultHttpClient client = new SystemDefaultHttpClient();
        HttpGet get = new HttpGet("http://api.openweathermap.org/data/2.5/weather?q=London,uk");
        HttpResponse metadataResponse = client.execute(get);
        if(requestWasSuccessful(metadataResponse)) {
//            HttpPost artifactPost = getArtifactPostRequest();
//            HttpResponse artifactResponse = client.execute(artifactPost);
//            return requestWasSuccessful(artifactResponse);
            return true;
        }
        else {
            return false;
        }
    }

    private Boolean requestWasSuccessful(HttpResponse response) {
        return response.getStatusLine().getStatusCode() == 200;
    }

    private HttpPost getArtifactPostRequest() {
        String artifactPostUrl = String.format(baseUrl + "/artifact/%s/%s/%s", team, application, artifactName);
        HttpPost artifactPost = new HttpPost(artifactPostUrl);
        File artifact = new File(artifactSource);
        HttpEntity body = MultipartEntityBuilder.create()
                .addBinaryBody(artifactName, artifact)
                .build();
        artifactPost.setEntity(body);
        artifactPost.addHeader("content-type", "multipart/form-data");
        return artifactPost;
    }

    private HttpPost getMetadataPostRequest() throws UnsupportedEncodingException {
        HttpPost post = new HttpPost(baseUrl + "/metadata");
        StringEntity body = new StringEntity(createMetadataPostBody());
        post.addHeader("content-type", "application/json");
        post.setEntity(body);
        return post;
    }

    private String createMetadataPostBody() {
        return String.format("{\"name\":\"%s\",\"state\":\"test\",\"team\":\"%s\",\"application\":\"%s\",\"tags\":{}}",
                artifactName, state, team, application);
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
        private String defaultDeploymentAction = "Create new artifact";
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

        public ListBoxModel doFillDefaultDeploymentActionItems() {
            ListBoxModel items = new ListBoxModel();
            List<String> vals = Arrays.asList("Create new artifact", "Update state to production");
            for (String action : vals) {
                items.add(action);
            }
            return items;
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
