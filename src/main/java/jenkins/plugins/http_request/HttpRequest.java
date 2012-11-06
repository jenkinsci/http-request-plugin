package jenkins.plugins.http_request;

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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Map;
import javax.servlet.ServletException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Janario Oliveira
 */
public class HttpRequest extends Builder {

    private final URL url;
    private final HttpMode httpMode;

    @DataBoundConstructor
    public HttpRequest(String url, String httpMode) throws MalformedURLException {
        this.url = new URL(url);
        this.httpMode = httpMode != null && !httpMode.isEmpty()
                ? HttpMode.valueOf(httpMode) : null;
    }

    public URL getUrl() {
        return url;
    }

    public HttpMode getHttpMode() {
        return httpMode;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException {
        PrintStream logger = listener.getLogger();
        HttpMode mode = httpMode != null ? httpMode : getDescriptor().getDefaultHttpMode();
        URL finalURL = url;

        logger.println("HttpMode: " + mode);
        logger.println("Sending request to url: " + url + " with parameters:");

        String params = createParameters(logger, build.getBuildVariables());

        //doGet
        if (mode == HttpMode.GET) {
            finalURL = new URL(url.toExternalForm() + "?" + params);
        }

        URLConnection connection = finalURL.openConnection();

        //doPost
        OutputStreamWriter writer = null;
        if (mode == HttpMode.POST) {
            connection.setDoOutput(true);
            writer = new OutputStreamWriter(connection.getOutputStream());
            writer.write(params.toString());
            writer.flush();
        }

        //send
        try {
            InputStream inputStream = connection.getInputStream();
            inputStream.close();

        } catch (IOException ioe) {
            HttpURLConnection httpConn = (HttpURLConnection) connection;

            InputStream errorStream = httpConn.getErrorStream();
            logResponse(errorStream, logger);
            errorStream.close();

            throw ioe;
        }


        if (writer != null) {
            writer.close();
        }

        return true;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    private String createParameters(PrintStream logger, Map<String, String> buildVariables)
            throws UnsupportedEncodingException {
        StringBuilder params = new StringBuilder();
        for (Map.Entry<String, String> entry : buildVariables.entrySet()) {
            logger.println("  " + entry.getKey() + " = " + entry.getValue());

            if (params.length() != 0) {
                params.append("&");
            }
            params.append(URLEncoder.encode(entry.getKey(), "UTF-8")).append("=")
                    .append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return params.toString();
    }

    private void logResponse(InputStream inputStream, PrintStream logger) throws IOException {
        logger.println();
        logger.println("Response:");
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        while ((line = reader.readLine()) != null) {
            logger.println("  " + line);
        }
        logger.println();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        private HttpMode defaultHttpMode = HttpMode.POST;

        public HttpMode getDefaultHttpMode() {
            return defaultHttpMode;
        }

        public FormValidation doCheckDefaultHttpMode(@QueryParameter String value)
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
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            defaultHttpMode = HttpMode.valueOf(formData.getString("defaultHttpMode"));
            save();
            return super.configure(req, formData);
        }
    }
}
