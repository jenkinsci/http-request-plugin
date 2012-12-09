package jenkins.plugins.http_request;

import jenkins.plugins.http_request.auth.BasicDigestAuthentication;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.servlet.ServletException;
import static jenkins.plugins.http_request.HttpRequest.DescriptorImpl.DEFAULT_OPTION;
import static jenkins.plugins.http_request.HttpRequest.DescriptorImpl.NULL_AUTHENTICATION;
import jenkins.plugins.http_request.util.RequestAction;
import jenkins.plugins.http_request.auth.Authenticator;
import jenkins.plugins.http_request.auth.FormAuthentication;
import jenkins.plugins.http_request.util.HttpClientUtil;
import jenkins.plugins.http_request.util.NameValuePair;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Janario Oliveira
 */
public class HttpRequest extends Builder {

    private final String url;
    private final HttpMode httpMode;
    private final String authentication;

    @DataBoundConstructor
    public HttpRequest(String url, String httpMode, String authentication) throws
            MalformedURLException {
        this.url = url;
        this.httpMode = DEFAULT_OPTION.equals(httpMode) ? null : HttpMode.valueOf(httpMode);
        this.authentication = NULL_AUTHENTICATION.equals(authentication) ? null : authentication;
    }

    public String getUrl() {
        return url;
    }

    public HttpMode getHttpMode() {
        return httpMode;
    }

    public String getAuthentication() {
        return authentication;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher,
            BuildListener listener) throws IOException, InterruptedException {
        PrintStream logger = listener.getLogger();

        HttpMode mode = httpMode != null ? httpMode : getDescriptor().getDefaultHttpMode();
        logger.println("HttpMode: " + mode);

        DefaultHttpClient httpclient = new DefaultHttpClient();

        logger.println("Parameters: ");
        List<NameValuePair> params = createParameters(build.getBuildVariables(), logger, build.getEnvironment(listener));

        RequestAction requestAction = new RequestAction(url, mode.name());
        requestAction.setParams(params);

        final HttpClientUtil clientUtil = new HttpClientUtil();
        HttpRequestBase method = clientUtil.createRequestBase(requestAction);

        if (authentication != null) {
            Authenticator auth = getDescriptor().getAuthentication(authentication);
            if (auth == null) {
                throw new IllegalStateException("Authentication " + authentication + " doesn't exists anymore");
            }
            logger.println("Using authentication: " + auth.getKeyName());

            auth.authenticate(httpclient, method, logger);
        }

        HttpResponse execute = clientUtil.execute(httpclient, method, logger);
        //from 400(client error) to 599(server error)
        return !(execute.getStatusLine().getStatusCode() >= 400
                && execute.getStatusLine().getStatusCode() <= 599);
    }

    private List<NameValuePair> createParameters(
            Map<String, String> buildVariables, PrintStream logger,
            EnvVars envVars) {
        List<NameValuePair> l = new ArrayList<NameValuePair>();
        for (Map.Entry<String, String> entry : buildVariables.entrySet()) {
            doValueAndLog(entry, envVars, logger);
            l.add(new NameValuePair(entry.getKey(), entry.getValue()));
        }

        return l;
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

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public static final String DEFAULT_OPTION = "Default";
        public static final String NULL_AUTHENTICATION = "--";
        private HttpMode defaultHttpMode = HttpMode.POST;
        private List<BasicDigestAuthentication> basicDigestAuthentications = new ArrayList<BasicDigestAuthentication>();
        private List<FormAuthentication> formAuthentications = new ArrayList<FormAuthentication>();

        public DescriptorImpl() {
            load();
        }

        public HttpMode getDefaultHttpMode() {
            return defaultHttpMode;
        }

        public ListBoxModel doFillDefaultHttpModeItems() {
            return HttpMode.getFillItems();
        }

        public ListBoxModel doFillHttpModeItems() {
            ListBoxModel items = HttpMode.getFillItems();
            items.add(0, new ListBoxModel.Option(DEFAULT_OPTION));

            return items;
        }

        public ListBoxModel doFillAuthenticationItems() {
            ListBoxModel items = new ListBoxModel();
            items.add(NULL_AUTHENTICATION);
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
            if (Util.fixEmptyAndTrim(value) == null) {
                return FormValidation.error("Please set an url");
            }

            try {
                new URL(value);
            } catch (MalformedURLException e) {
                return FormValidation.error("Please set a valid url");
            }

            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public List<BasicDigestAuthentication> getBasicDigestAuthentications() {
            return basicDigestAuthentications;
        }

        public List<FormAuthentication> getFormAuthentications() {
            return formAuthentications;
        }

        public Authenticator getAuthentication(String keyName) {
            for (Authenticator authenticator : getAuthentications()) {
                if (authenticator.getKeyName().equals(keyName)) {
                    return authenticator;
                }
            }
            return null;
        }

        public List<Authenticator> getAuthentications() {
            List<Authenticator> list = new ArrayList<Authenticator>();
            list.addAll(basicDigestAuthentications);
            list.addAll(formAuthentications);
            return list;
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

        @Override
        public String getDisplayName() {
            return "HTTP Request";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws
                FormException {
            defaultHttpMode = HttpMode.valueOf(formData.getString("defaultHttpMode"));
            basicDigestAuthentications = req.bindParametersToList(BasicDigestAuthentication.class, "basicDigestAuthentication.");
            formAuthentications = req.bindParametersToList(FormAuthentication.class, "formAuthentication.");

            //FIXME this should be imported automatic to FormAuthentication instance
            JSON get = (JSON) formData.get("formAuthentications");
            if (get != null) {
                List<FormAuthentication> setActions = new ArrayList<FormAuthentication>(formAuthentications);
                if (get.isArray()) {
                    JSONArray ar = (JSONArray) get;
                    for (Object object : ar) {
                        addActions((JSONObject) object, setActions);
                    }
                } else {
                    addActions((JSONObject) get, setActions);
                }
            }
            save();
            return super.configure(req, formData);
        }

        private void addActions(JSONObject o, List<FormAuthentication> forms) {
            JSON json = (JSON) o.get("actions");

            List<RequestAction> actions = new ArrayList<RequestAction>();
            if (json.isArray()) {
                JSONArray ar = (JSONArray) json;
                for (Object object : ar) {
                    final JSONObject jsonAction = (JSONObject) object;
                    final RequestAction action = (RequestAction) jsonAction.toBean(RequestAction.class);
                    addParams(jsonAction, action);

                    actions.add(action);
                }
            } else {
                final JSONObject jsonAction = (JSONObject) json;
                final RequestAction action = (RequestAction) jsonAction.toBean(RequestAction.class);
                addParams(jsonAction, action);
                actions.add(action);
            }

            String keyName = o.getString("keyName");
            for (Iterator<FormAuthentication> it = forms.iterator(); it.hasNext();) {
                FormAuthentication formAuthentication = it.next();
                if (formAuthentication.getKeyName().equals(keyName)) {
                    formAuthentication.setActions(actions);
                    it.remove();
                    break;
                }
            }
        }

        private void addParams(JSONObject jo, final RequestAction action) {
            JSON params = (JSON) jo.get("params");
            List<NameValuePair> paramsList = new ArrayList<NameValuePair>();
            if (params != null) {
                if (params.isArray()) {
                    JSONArray arParams = (JSONArray) params;
                    for (Object paramObject : arParams) {
                        JSONObject joParam = (JSONObject) paramObject;
                        paramsList.add((NameValuePair) ((JSONObject) joParam).toBean(NameValuePair.class));
                    }
                } else {
                    paramsList.add((NameValuePair) ((JSONObject) params).toBean(NameValuePair.class));
                }
            }
            action.setParams(paramsList);
        }
    }
}
