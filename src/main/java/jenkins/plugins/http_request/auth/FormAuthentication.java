package jenkins.plugins.http_request.auth;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

import jenkins.plugins.http_request.HttpRequestGlobalConfig;
import jenkins.plugins.http_request.util.HttpClientUtil;
import jenkins.plugins.http_request.util.RequestAction;

/**
 * @author Janario Oliveira
 */
public class FormAuthentication extends AbstractDescribableImpl<FormAuthentication>
        implements Authenticator {

	private static final long serialVersionUID = -4370238820437831639L;
	private final String keyName;
    private final List<RequestAction> actions;

    @DataBoundConstructor
    public FormAuthentication(String keyName, List<RequestAction> actions) {
        this.keyName = keyName;
        this.actions = actions == null ? new ArrayList<>() : actions;
    }

    public String getKeyName() {
        return keyName;
    }

    public List<RequestAction> getActions() {
        return Collections.unmodifiableList(actions);
    }

	@Override
	public CloseableHttpClient authenticate(HttpClientBuilder clientBuilder, HttpContext context,
								   HttpRequestBase requestBase, PrintStream logger) throws IOException {
		CloseableHttpClient client = clientBuilder.build();
		final HttpClientUtil clientUtil = new HttpClientUtil();
		for (RequestAction requestAction : actions) {
			final HttpRequestBase method = clientUtil.createRequestBase(requestAction);

			final HttpResponse execute = clientUtil.execute(client, context, method, logger);
			//from 400(client error) to 599(server error)
			if ((execute.getStatusLine().getStatusCode() >= 400
					&& execute.getStatusLine().getStatusCode() <= 599)) {
				throw new IllegalStateException("Error doing authentication");
			}
		}
		return client;
	}

    @Extension
    public static class FormAuthenticationDescriptor extends Descriptor<FormAuthentication> {

        public FormValidation doCheckKeyName(@QueryParameter String value) {
            return HttpRequestGlobalConfig.validateKeyName(value);
        }

        @Override
        public String getDisplayName() {
            return "Form Authentication";
        }

    }
}
