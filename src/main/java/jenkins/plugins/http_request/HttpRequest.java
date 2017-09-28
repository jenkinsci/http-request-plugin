package jenkins.plugins.http_request;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.common.AbstractIdCredentialsListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.common.base.Strings;
import com.google.common.collect.Range;
import com.google.common.collect.Ranges;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.model.Items;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;

import jenkins.plugins.http_request.auth.BasicDigestAuthentication;
import jenkins.plugins.http_request.auth.FormAuthentication;
import jenkins.plugins.http_request.util.HttpClientUtil;
import jenkins.plugins.http_request.util.HttpRequestNameValuePair;

/**
 * @author Janario Oliveira
 */
public class HttpRequest extends Builder {

    private @Nonnull String url;
	private Boolean ignoreSslErrors = DescriptorImpl.ignoreSslErrors;
	private HttpMode httpMode                 = DescriptorImpl.httpMode;
	private String httpProxy                  = DescriptorImpl.httpProxy;
    private Boolean passBuildParameters       = DescriptorImpl.passBuildParameters;
    private String validResponseCodes         = DescriptorImpl.validResponseCodes;
    private String validResponseContent       = DescriptorImpl.validResponseContent;
    private MimeType acceptType               = DescriptorImpl.acceptType;
    private MimeType contentType              = DescriptorImpl.contentType;
    private String outputFile                 = DescriptorImpl.outputFile;
    private Integer timeout                   = DescriptorImpl.timeout;
    private Boolean consoleLogResponseBody    = DescriptorImpl.consoleLogResponseBody;
    private Boolean quiet                     = DescriptorImpl.quiet;
    private String authentication             = DescriptorImpl.authentication;
    private String requestBody                = DescriptorImpl.requestBody;
    private List<HttpRequestNameValuePair> customHeaders = DescriptorImpl.customHeaders;

	@DataBoundConstructor
	public HttpRequest(@Nonnull String url) {
		this.url = url;
	}

	@Nonnull
	public String getUrl() {
		return url;
	}

	public Boolean getIgnoreSslErrors() {
		return ignoreSslErrors;
	}

	@DataBoundSetter
	public void setIgnoreSslErrors(Boolean ignoreSslErrors) {
		this.ignoreSslErrors = ignoreSslErrors;
	}

	public HttpMode getHttpMode() {
		return httpMode;
	}

	@DataBoundSetter
	public void setHttpMode(HttpMode httpMode) {
		this.httpMode = httpMode;
	}

	public String getHttpProxy() {
		return httpProxy;
	}

	@DataBoundSetter
	public void setHttpProxy(String httpProxy) {
		this.httpProxy = httpProxy;
	}

	public Boolean getPassBuildParameters() {
		return passBuildParameters;
	}

	@DataBoundSetter
	public void setPassBuildParameters(Boolean passBuildParameters) {
		this.passBuildParameters = passBuildParameters;
	}

	@Nonnull
	public String getValidResponseCodes() {
		return validResponseCodes;
	}

	@DataBoundSetter
	public void setValidResponseCodes(String validResponseCodes) {
		this.validResponseCodes = validResponseCodes;
	}

	public String getValidResponseContent() {
		return validResponseContent;
	}

	@DataBoundSetter
	public void setValidResponseContent(String validResponseContent) {
		this.validResponseContent = validResponseContent;
	}

	public MimeType getAcceptType() {
		return acceptType;
	}

	@DataBoundSetter
	public void setAcceptType(MimeType acceptType) {
		this.acceptType = acceptType;
	}

	public MimeType getContentType() {
		return contentType;
	}

	@DataBoundSetter
	public void setContentType(MimeType contentType) {
		this.contentType = contentType;
	}

	public String getOutputFile() {
		return outputFile;
	}

	@DataBoundSetter
	public void setOutputFile(String outputFile) {
		this.outputFile = outputFile;
	}

	public Integer getTimeout() {
		return timeout;
	}

	@DataBoundSetter
	public void setTimeout(Integer timeout) {
		this.timeout = timeout;
	}

	public Boolean getConsoleLogResponseBody() {
		return consoleLogResponseBody;
	}

	@DataBoundSetter
	public void setConsoleLogResponseBody(Boolean consoleLogResponseBody) {
		this.consoleLogResponseBody = consoleLogResponseBody;
	}

	public Boolean getQuiet() {
		return quiet;
	}

	@DataBoundSetter
	public void setQuiet(Boolean quiet) {
		this.quiet = quiet;
	}

	public String getAuthentication() {
		return authentication;
	}

	@DataBoundSetter
	public void setAuthentication(String authentication) {
		this.authentication = authentication;
	}

	public String getRequestBody() {
		return requestBody;
	}

	@DataBoundSetter
	public void setRequestBody(String requestBody) {
		this.requestBody = requestBody;
	}

	public List<HttpRequestNameValuePair> getCustomHeaders() {
		return customHeaders;
	}

	@DataBoundSetter
	public void setCustomHeaders(List<HttpRequestNameValuePair> customHeaders) {
		this.customHeaders = customHeaders;
	}

	@Initializer(before = InitMilestone.PLUGINS_STARTED)
	public static void xStreamCompatibility() {
		Items.XSTREAM2.aliasField("logResponseBody", HttpRequest.class, "consoleLogResponseBody");
		Items.XSTREAM2.aliasField("consoleLogResponseBody", HttpRequest.class, "consoleLogResponseBody");
		Items.XSTREAM2.alias("pair", HttpRequestNameValuePair.class);
	}

	protected Object readResolve() {
		if (customHeaders == null) {
			customHeaders = DescriptorImpl.customHeaders;
		}
		if (validResponseCodes == null || validResponseCodes.trim().isEmpty()) {
			validResponseCodes = DescriptorImpl.validResponseCodes;
		}
		if (ignoreSslErrors == null) {
			//default for new job false(DescriptorImpl.ignoreSslErrors) for old ones true to keep same behavior
			ignoreSslErrors = true;
		}
		if (quiet == null) {
			quiet = false;
		}
		return this;
	}

	private List<HttpRequestNameValuePair> createParams(EnvVars envVars, AbstractBuild<?, ?> build, TaskListener listener) throws IOException {
		Map<String, String> buildVariables = build.getBuildVariables();
		if (buildVariables.isEmpty()) {
			return Collections.emptyList();
		}
		PrintStream logger = listener.getLogger();
		logger.println("Parameters: ");

		List<HttpRequestNameValuePair> l = new ArrayList<>();
		for (Map.Entry<String, String> entry : buildVariables.entrySet()) {
			String value = envVars.expand(entry.getValue());
			logger.println("  " + entry.getKey() + " = " + value);

			l.add(new HttpRequestNameValuePair(entry.getKey(), value));
		}
		return l;
	}

	String resolveUrl(EnvVars envVars,
					  AbstractBuild<?, ?> build, TaskListener listener) throws IOException {
		String url = envVars.expand(getUrl());
		if (Boolean.TRUE.equals(getPassBuildParameters()) && getHttpMode() == HttpMode.GET) {
			List<HttpRequestNameValuePair> params = createParams(envVars, build, listener);
			if (!params.isEmpty()) {
				url = HttpClientUtil.appendParamsToUrl(url, params);
			}
		}
		return url;
	}

	List<HttpRequestNameValuePair> resolveHeaders(EnvVars envVars) {
		final List<HttpRequestNameValuePair> headers = new ArrayList<>();
		if (contentType != null && contentType != MimeType.NOT_SET) {
			headers.add(new HttpRequestNameValuePair("Content-type", contentType.getContentType().toString()));
		}
		if (acceptType != null && acceptType != MimeType.NOT_SET) {
			headers.add(new HttpRequestNameValuePair("Accept", acceptType.getValue()));
		}
		for (HttpRequestNameValuePair header : customHeaders) {
			String headerName = envVars.expand(header.getName());
			String headerValue = envVars.expand(header.getValue());
			boolean maskValue = headerName.equalsIgnoreCase("Authorization") ||
					header.getMaskValue();

			headers.add(new HttpRequestNameValuePair(headerName, headerValue, maskValue));
		}
		return headers;
	}

	String resolveBody(EnvVars envVars,
					  AbstractBuild<?, ?> build, TaskListener listener) throws IOException {
		String body = envVars.expand(getRequestBody());
		if (Strings.isNullOrEmpty(body) && Boolean.TRUE.equals(getPassBuildParameters())) {
			List<HttpRequestNameValuePair> params = createParams(envVars, build, listener);
			if (!params.isEmpty()) {
				body = HttpClientUtil.paramsToString(params);
			}
		}
		return body;
	}

	FilePath resolveOutputFile(EnvVars envVars, AbstractBuild<?,?> build) {
		if (outputFile == null || outputFile.trim().isEmpty()) {
			return null;
		}
		String filePath = envVars.expand(outputFile);
		FilePath workspace = build.getWorkspace();
		if (workspace == null) {
			throw new IllegalStateException("Could not find workspace to save file outputFile: " + outputFile);
		}
		return workspace.child(filePath);
	}

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener)
    throws InterruptedException, IOException
    {
		EnvVars envVars = build.getEnvironment(listener);
		for (Map.Entry<String, String> e : build.getBuildVariables().entrySet()) {
			envVars.put(e.getKey(), e.getValue());
		}

		HttpRequestExecution exec = HttpRequestExecution.from(this, envVars, build,
				this.getQuiet() ? TaskListener.NULL : listener);
		launcher.getChannel().call(exec);

        return true;
    }

	@Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
		public static final boolean ignoreSslErrors = false;
		public static final HttpMode httpMode                  = HttpMode.GET;
		public static final String   httpProxy                 = "";
        public static final Boolean  passBuildParameters       = false;
        public static final String   validResponseCodes        = "100:399";
        public static final String   validResponseContent      = "";
        public static final MimeType acceptType                = MimeType.NOT_SET;
        public static final MimeType contentType               = MimeType.NOT_SET;
        public static final String   outputFile                = "";
        public static final int      timeout                   = 0;
        public static final Boolean  consoleLogResponseBody    = false;
        public static final Boolean  quiet                     = false;
        public static final String   authentication            = "";
        public static final String   requestBody               = "";
        public static final List <HttpRequestNameValuePair> customHeaders = Collections.<HttpRequestNameValuePair>emptyList();

        public DescriptorImpl() {
            load();
        }

        @SuppressWarnings("rawtypes")
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "HTTP Request";
        }

        public ListBoxModel doFillHttpModeItems() {
            return HttpMode.getFillItems();
        }

        public ListBoxModel doFillAcceptTypeItems() {
            return MimeType.getContentTypeFillItems();
        }

        public ListBoxModel doFillContentTypeItems() {
            return MimeType.getContentTypeFillItems();
        }

        public ListBoxModel doFillAuthenticationItems(@AncestorInPath Item project,
													  @QueryParameter String url) {
            return fillAuthenticationItems(project, url);
        }

        public static ListBoxModel fillAuthenticationItems(Item project, String url) {
			if (project == null || !project.hasPermission(Item.CONFIGURE)) {
				return new StandardListBoxModel();
			}

			List<Option> options = new ArrayList<>();
			for (BasicDigestAuthentication basic : HttpRequestGlobalConfig.get().getBasicDigestAuthentications()) {
				options.add(new Option("(deprecated - use Jenkins Credentials) " +
						basic.getKeyName(), basic.getKeyName()));
            }

            for (FormAuthentication formAuthentication : HttpRequestGlobalConfig.get().getFormAuthentications()) {
				options.add(new Option(formAuthentication.getKeyName()));
			}

			AbstractIdCredentialsListBoxModel<StandardListBoxModel, StandardCredentials> items = new StandardListBoxModel()
					.includeEmptyValue()
					.includeAs(ACL.SYSTEM,
							project, StandardUsernamePasswordCredentials.class,
							URIRequirementBuilder.fromUri(url).build());
			items.addMissing(options);
            return items;
        }

        public static List<Range<Integer>> parseToRange(String value) {
            List<Range<Integer>> validRanges = new ArrayList<Range<Integer>>();

            String[] codes = value.split(",");
            for (String code : codes) {
                String[] fromTo = code.trim().split(":");
                checkArgument(fromTo.length <= 2, "Code %s should be an interval from:to or a single value", code);

                Integer from;
                try {
                    from = Integer.parseInt(fromTo[0]);
                } catch (NumberFormatException nfe) {
                    throw new IllegalArgumentException("Invalid number "+fromTo[0]);
                }

                Integer to = from;
                if (fromTo.length != 1) {
                    try {
                        to = Integer.parseInt(fromTo[1]);
                    } catch (NumberFormatException nfe) {
                        throw new IllegalArgumentException("Invalid number "+fromTo[1]);
                    }
                }

                checkArgument(from <= to, "Interval %s should be FROM less than TO", code);
                validRanges.add(Ranges.closed(from, to));
            }

            return validRanges;
        }

        public FormValidation doCheckValidResponseCodes(@QueryParameter String value) {
            return checkValidResponseCodes(value);
        }

        public static FormValidation checkValidResponseCodes(String value) {
            if (value == null || value.trim().isEmpty()) {
                return FormValidation.ok();
            }

            try {
                parseToRange(value);
            } catch (IllegalArgumentException iae) {
                return FormValidation.error("Response codes expected is wrong. "+iae.getMessage());
            }
            return FormValidation.ok();

        }
    }

}
