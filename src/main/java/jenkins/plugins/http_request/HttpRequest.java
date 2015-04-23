package jenkins.plugins.http_request;

import com.google.gson.Gson;
import hudson.Extension;
import hudson.Launcher;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Items;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
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
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Janario Oliveira
 */
public class HttpRequest extends Recorder {

    private String baseUrl = "http://localhost:9000";
    private Artifact artifact;
    private String artifactSource;
    @DataBoundConstructor
    public HttpRequest(String team, String application, String artifactName, String state, List<NameValuePair> tags, String artifactSource)
            throws URISyntaxException {

        this.artifactSource = artifactSource;
        Map<String, String> tagsMap = covertToMap(tags);
        this.artifact = new Artifact(artifactName, state, team, application, tagsMap);
    }

    private Map<String, String> covertToMap(List<NameValuePair> tags) {
        Map<String, String> map = new HashMap<String, String>();
        for(NameValuePair tag : tags) {
            map.put(tag.getName(), tag.getValue());
        }
        return map;
    }

    @Initializer(before = InitMilestone.PLUGINS_STARTED)
    public static void xStreamCompatibility() {
        Items.XSTREAM2.aliasField("logResponseBody", HttpRequest.class, "consoleLogResponseBody");
        Items.XSTREAM2.aliasField("consoleLogResponseBody", HttpRequest.class, "consoleLogResponseBody");
        Items.XSTREAM2.alias("pair", NameValuePair.class);
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        final PrintStream logger = listener.getLogger();
        return createNewArtifact(logger);
    }

    private boolean createNewArtifact(PrintStream logger) throws IOException {
        DefaultHttpClient client = new SystemDefaultHttpClient();
        HttpResponse metadataResponse = createMetaData(logger, client);

        if(requestWasSuccessful(metadataResponse)) {
            logger.println("Successfully created metadata for artifact " + artifact.name);
            return sendArtifactFile(logger, client);
        }
        else {
            logger.println("Error creating metadata: " + EntityUtils.toString(metadataResponse.getEntity()));
            return false;
        }
    }

    private HttpResponse createMetaData(PrintStream logger, DefaultHttpClient client) throws IOException {
        HttpPost metadataPost = getMetadataPostRequest();

        logger.println("URL: " + metadataPost.getURI().toString());
        logger.println("Body: " + EntityUtils.toString(metadataPost.getEntity()));

        return client.execute(metadataPost);
    }

    private boolean sendArtifactFile(PrintStream logger, DefaultHttpClient client) throws IOException {

        HttpPost artifactPost = getArtifactPostRequest();
        HttpResponse artifactResponse = client.execute(artifactPost);
        Boolean success = requestWasSuccessful(artifactResponse);

        if (success){
            logger.println("Successfully sent file to s3");
        }
        else {
            logger.println("Error sending file to s3: " + EntityUtils.toString(artifactResponse.getEntity()));
        }
        return success;
    }

    private Boolean requestWasSuccessful(HttpResponse response) {
        return response.getStatusLine().getStatusCode() == 200;
    }

    private HttpPost getMetadataPostRequest() throws UnsupportedEncodingException {
        HttpPost post = new HttpPost(baseUrl + "/metadata");
        StringEntity body = new StringEntity(createMetadataPostBody());
        post.addHeader("content-type", "application/json");
        post.setEntity(body);
        return post;
    }

    private String createMetadataPostBody() {
        Gson gson = new Gson();
        return gson.toJson(this.artifact);
    }

    private HttpPost getArtifactPostRequest() {
        String artifactPostUrl = String.format(baseUrl + "/artifact/%s/%s/%s", artifact.team, artifact.application, artifact.name);
        HttpPost artifactPost = new HttpPost(artifactPostUrl);
        File toUpload = new File(artifactSource);
        HttpEntity body = MultipartEntityBuilder.create()
                .addBinaryBody(artifact.name, toUpload)
                .build();
        artifactPost.setEntity(body);
        artifactPost.addHeader("content-type", "multipart/form-data");
        return artifactPost;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
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




        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Publish Artifact";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws
                FormException {
            req.bindJSON(this, formData);
            save();
            return true;
        }

    }

}
