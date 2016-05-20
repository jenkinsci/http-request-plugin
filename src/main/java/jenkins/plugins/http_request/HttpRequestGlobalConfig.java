package jenkins.plugins.http_request;

import hudson.Extension;
import hudson.XmlFile;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.util.XStream2;

import java.io.File;
import java.util.List;
import java.util.ArrayList;

import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;

import jenkins.plugins.http_request.auth.Authenticator;
import jenkins.plugins.http_request.auth.BasicDigestAuthentication;
import jenkins.plugins.http_request.auth.FormAuthentication;
import jenkins.plugins.http_request.util.HttpRequestNameValuePair;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Martin d'Anjou
 */
@Extension
public class HttpRequestGlobalConfig extends GlobalConfiguration {

    private List<BasicDigestAuthentication> basicDigestAuthentications = new ArrayList<BasicDigestAuthentication>();
    private List<FormAuthentication> formAuthentications = new ArrayList<FormAuthentication>();

    private static final XStream2 XSTREAM2 = new XStream2();

    public HttpRequestGlobalConfig() {
        load();
    }

    @Initializer(before = InitMilestone.PLUGINS_STARTED)
    public static void xStreamCompatibility() {
        XSTREAM2.addCompatibilityAlias("jenkins.plugins.http_request.HttpRequest$DescriptorImpl", HttpRequestGlobalConfig.class);
        XSTREAM2.addCompatibilityAlias("jenkins.plugins.http_request.util.NameValuePair", HttpRequestNameValuePair.class);
    }

    @Override
    protected XmlFile getConfigFile() {
        Jenkins j = Jenkins.getInstance();
        if (j == null) return null;
        File rootDir = j.getRootDir();
        File xmlFile = new File(rootDir, "jenkins.plugins.http_request.HttpRequest.xml");
        return new XmlFile(XSTREAM2, xmlFile);
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json)
    throws FormException
    {
        req.bindJSON(this, json);
        save();
        return true;
    }

    public static HttpRequestGlobalConfig get() {
        return GlobalConfiguration.all().get(HttpRequestGlobalConfig.class);
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
}
