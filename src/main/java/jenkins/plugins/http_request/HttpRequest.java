package jenkins.plugins.http_request;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.servlet.ServletException;
import net.sf.json.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Janario Oliveira
 */
public class HttpRequest extends Builder {

    private final String url;
    private final HttpMode httpMode;

    @DataBoundConstructor
    public HttpRequest(String url, String httpMode) throws MalformedURLException {
        this.url = url;
        this.httpMode = httpMode != null && !httpMode.isEmpty()
                ? HttpMode.valueOf(httpMode) : null;
    }

    public String getUrl() {
        return url;
    }

    public HttpMode getHttpMode() {
        return httpMode;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher,
            BuildListener listener) throws UnsupportedEncodingException,
            IOException, InterruptedException {
        PrintStream logger = listener.getLogger();
        HttpMode mode = httpMode != null ? httpMode : getDescriptor().getDefaultHttpMode();

        logger.println("HttpMode: " + mode);
        logger.println("Sending request to url: " + url + " with parameters:");

        HttpEntity params = createParameters(build.getBuildVariables(),
                logger, build.getEnvironment(listener));


        HttpRequestBase method = mode == HttpMode.GET
                ? makeGet(url, params) : makePost(url, params);

        HttpClient httpclient = new DefaultHttpClient();
        HttpResponse execute = httpclient.execute(method);

        logger.println("Response Code: " + execute.getStatusLine());
        logger.println("Response: \n" + EntityUtils.toString(execute.getEntity()));
        method.releaseConnection();

        //from 400(client error) to 599(server error)
        return !(execute.getStatusLine().getStatusCode() >= 400
                && execute.getStatusLine().getStatusCode() <= 599);
    }

    private HttpEntity createParameters(Map<String, String> buildVariables,
            PrintStream logger, EnvVars envVars) throws
            UnsupportedEncodingException {
        List<NameValuePair> l = new ArrayList<NameValuePair>();
        for (Map.Entry<String, String> entry : buildVariables.entrySet()) {
            doValueAndLog(entry, envVars, logger);
            l.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));

        }
        return new UrlEncodedFormEntity(l);
    }

    private void doValueAndLog(Entry<String, String> entry, EnvVars envVars,
            PrintStream logger) {
        //replace envs
        if (entry.getValue().trim().startsWith("$")) {
            final String key = entry.getValue().trim().replaceFirst("\\$", "");
            if (envVars.containsKey(key)) {
                entry.setValue(envVars.get(key));
            }
        }
        logger.println("  " + entry.getKey() + " = " + entry.getValue());
    }

    private HttpGet makeGet(String url, HttpEntity params) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(params.getContent()));

        StringBuilder sb = new StringBuilder(url).append("?");
        String s;
        while ((s = br.readLine()) != null) {
            sb.append(s);
        }
        return new HttpGet(sb.toString());
    }

    private HttpPost makePost(String url, HttpEntity params) {
        HttpPost httpPost = new HttpPost(url);
        httpPost.setEntity(params);
        return httpPost;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        private HttpMode defaultHttpMode = HttpMode.POST;

        public HttpMode getDefaultHttpMode() {
            return defaultHttpMode;
        }

        public FormValidation doCheckDefaultHttpMode(
                @QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0) {
                return FormValidation.error("Please set an http mode");
            }
            return doCheckHttpMode(value);
        }

        public FormValidation doCheckUrl(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0) {
                return FormValidation.error("Please set an url");
            }

            try {
                new URL(value);
            } catch (MalformedURLException e) {
                return FormValidation.error("Please set a valid url");
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckHttpMode(@QueryParameter String value)
                throws IOException, ServletException {
            if (!value.isEmpty()) {
                boolean validMode = false;
                for (HttpMode mode : HttpMode.values()) {
                    validMode = validMode || mode.toString().equals(value);
                    if (validMode) {
                        break;
                    }
                }

                if (!validMode) {
                    return FormValidation.error("Please set a valid http mode(GET|POST)");
                }
            }

            return FormValidation.ok();
        }

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
            defaultHttpMode = HttpMode.valueOf(formData.getString("defaultHttpMode"));
            save();
            return super.configure(req, formData);
        }
    }
}
