package jenkins.plugins.http_request;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.XmlFile;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.util.FormValidation;
import hudson.util.XStream2;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;

import jenkins.plugins.http_request.auth.Authenticator;
import jenkins.plugins.http_request.auth.BasicDigestAuthentication;
import jenkins.plugins.http_request.auth.FormAuthentication;
import jenkins.plugins.http_request.util.HttpRequestNameValuePair;

/**
 * @author Martin d'Anjou
 */
@Extension
public class HttpRequestGlobalConfig extends GlobalConfiguration {

    /**
     * @deprecated removed without replacement
     */
    @Deprecated
    private transient List<BasicDigestAuthentication> basicDigestAuthentications = new ArrayList<>();
    private List<FormAuthentication> formAuthentications = new ArrayList<>();

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
        File rootDir = Jenkins.get().getRootDir();
        File xmlFile = new File(rootDir, "jenkins.plugins.http_request.HttpRequest.xml");
        return new XmlFile(XSTREAM2, xmlFile);
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) {
        req.bindJSON(this, json);
        save();
        return true;
    }

	public static FormValidation validateKeyName(String value) {
		List<Authenticator> list = HttpRequestGlobalConfig.get().getAuthentications();

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

    public static HttpRequestGlobalConfig get() {
        return GlobalConfiguration.all().get(HttpRequestGlobalConfig.class);
    }

    /**
     * @deprecated removed without replacement
     */
    @Deprecated
    public List<BasicDigestAuthentication> getBasicDigestAuthentications() {
        return basicDigestAuthentications;
    }

    /**
     * @deprecated removed without replacement
     */
    @Deprecated
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
		return new ArrayList<>(formAuthentications);
    }

    public Authenticator getAuthentication(String keyName) {
        for (Authenticator authenticator : getAuthentications()) {
            if (authenticator.getKeyName().equals(keyName)) {
                return authenticator;
            }
        }
        return null;
    }

	protected Object readResolve() {
		this.basicDigestAuthentications = new ArrayList<>();
		return this;
	}
}
