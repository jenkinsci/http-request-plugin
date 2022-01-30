package jenkins.plugins.http_request;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import edu.umd.cs.findbugs.annotations.NonNull;

import org.apache.http.HttpHeaders;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.common.AbstractIdCredentialsListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;

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
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;

import jenkins.plugins.http_request.auth.BasicDigestAuthentication;
import jenkins.plugins.http_request.auth.FormAuthentication;
import jenkins.plugins.http_request.util.HttpClientUtil;
import jenkins.plugins.http_request.util.HttpRequestFormDataPart;
import jenkins.plugins.http_request.util.HttpRequestNameValuePair;

/**
 * @author Janario Oliveira
 */
public class HttpRequest extends Builder {

	private final @NonNull String url;
	private Boolean ignoreSslErrors = DescriptorImpl.ignoreSslErrors;
	private HttpMode httpMode                 = DescriptorImpl.httpMode;
	private String httpProxy                  = DescriptorImpl.httpProxy;
	private String proxyAuthentication        = DescriptorImpl.proxyAuthentication;
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
    private String uploadFile                 = DescriptorImpl.uploadFile;
    private String multipartName              = DescriptorImpl.multipartName;
    private Boolean wrapAsMultipart           = DescriptorImpl.wrapAsMultipart;
    private Boolean useSystemProperties       = DescriptorImpl.useSystemProperties;
    private boolean useNtlm                   = DescriptorImpl.useNtlm;
    private List<HttpRequestNameValuePair> customHeaders = DescriptorImpl.customHeaders;
    private List<HttpRequestFormDataPart> formData = DescriptorImpl.formData;

	@DataBoundConstructor
	public HttpRequest(@NonNull String url) {
		this.url = url;
	}

	@NonNull
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

	@NonNull
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

	public String getProxyAuthentication() {
		return proxyAuthentication;
	}

	@DataBoundSetter
	public void setProxyAuthentication(String proxyAuthentication) {
		this.proxyAuthentication = proxyAuthentication;
	}

	public String getRequestBody() {
		return requestBody;
	}

	@DataBoundSetter
	public void setRequestBody(String requestBody) {
		this.requestBody = requestBody;
	}

	public Boolean getUseSystemProperties() {
		return useSystemProperties;
	}

	@DataBoundSetter
	public void setUseSystemProperties(Boolean useSystemProperties) {
		this.useSystemProperties = useSystemProperties;
	}

	public List<HttpRequestNameValuePair> getCustomHeaders() {
		return customHeaders;
	}

	@DataBoundSetter
	public void setCustomHeaders(List<HttpRequestNameValuePair> customHeaders) {
		this.customHeaders = customHeaders;
	}

	public List<HttpRequestFormDataPart> getFormData() {
		return formData;
	}

	@DataBoundSetter
	public void setFormData(List<HttpRequestFormDataPart> formData) {
		this.formData = Collections.unmodifiableList(formData);
	}

	public String getUploadFile() {
		return uploadFile;
	}

	@DataBoundSetter
	public void setUploadFile(String uploadFile) {
		this.uploadFile = uploadFile;
	}

	public String getMultipartName() {
		return multipartName;
	}

	@DataBoundSetter
	public void setMultipartName(String multipartName) {
		this.multipartName = multipartName;
	}

	public Boolean getWrapAsMultipart() {
		return wrapAsMultipart;
	}

	@DataBoundSetter
	public void setWrapAsMultipart(Boolean wrapAsMultipart) {
		this.wrapAsMultipart = wrapAsMultipart;
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
		if (formData == null) {
			formData = DescriptorImpl.formData;
		}
		if (validResponseCodes == null || validResponseCodes.trim().isEmpty()) {
			validResponseCodes = DescriptorImpl.validResponseCodes;
		}
		if (ignoreSslErrors == null) {
			//default for new job false(DescriptorImpl.ignoreSslErrors) for old ones true to keep same behavior
			ignoreSslErrors = true;
		}
		if (quiet == null) {
			quiet = DescriptorImpl.quiet;
		}
		if (useSystemProperties == null) {
			// old jobs use it (for compatibility), new jobs doesn't (jelly was not reading the default)
			useSystemProperties = !DescriptorImpl.useSystemProperties;
		}

		if(wrapAsMultipart == null) {
			wrapAsMultipart = DescriptorImpl.wrapAsMultipart;
		}
		return this;
	}

	private List<HttpRequestNameValuePair> createParams(EnvVars envVars, AbstractBuild<?, ?> build, TaskListener listener) {
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
			headers.add(new HttpRequestNameValuePair(HttpHeaders.CONTENT_TYPE, contentType.getContentType().toString()));
		}
		if (acceptType != null && acceptType != MimeType.NOT_SET) {
			headers.add(new HttpRequestNameValuePair(HttpHeaders.ACCEPT, acceptType.getValue()));
		}
		for (HttpRequestNameValuePair header : customHeaders) {
			String headerName = envVars.expand(header.getName());
			String headerValue = envVars.expand(header.getValue());
			boolean maskValue = headerName.equalsIgnoreCase(HttpHeaders.AUTHORIZATION) ||
					header.getMaskValue();

			headers.add(new HttpRequestNameValuePair(headerName, headerValue, maskValue));
		}
		return headers;
	}

	String resolveBody(EnvVars envVars,
					  AbstractBuild<?, ?> build, TaskListener listener) throws IOException {
		String body = envVars.expand(getRequestBody());
		if ((body == null || body.isEmpty()) && Boolean.TRUE.equals(getPassBuildParameters())) {
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

	FilePath resolveUploadFile(EnvVars envVars, AbstractBuild<?, ?> build) {
		return resolveUploadFileInternal(uploadFile, envVars, build);
	}

	private static FilePath resolveUploadFileInternal(String path, EnvVars envVars, AbstractBuild<?, ?> build) {
		if (path == null || path.trim().isEmpty()) {
			return null;
		}
		String filePath = envVars.expand(path);
		try {
			FilePath workspace = build.getWorkspace();
			if (workspace == null) {
				throw new IllegalStateException(
						"Could not find workspace to check existence of upload file: " + path
								+ ". You should use it inside a 'node' block");
			}
			FilePath uploadFilePath = workspace.child(filePath);
			if (!uploadFilePath.exists()) {
				throw new IllegalStateException("Could not find upload file: " + path);
			}
			return uploadFilePath;
		} catch (IOException | InterruptedException e) {
			throw new IllegalStateException(e);
		}
	}

	List<HttpRequestFormDataPart> resolveFormDataParts(EnvVars envVars, AbstractBuild<?, ?> build) {
		if (formData == null || formData.isEmpty()) {
			return Collections.emptyList();
		}

		List<HttpRequestFormDataPart> resolved = new ArrayList<>(formData.size());

		for (HttpRequestFormDataPart part : formData) {
			String name = envVars.expand(part.getName());
			String fileName = envVars.expand(part.getFileName());
			FilePath resolvedUploadFile =
					resolveUploadFileInternal(part.getUploadFile(), envVars, build);
			String body = envVars.expand(part.getBody());

			HttpRequestFormDataPart newPart = new HttpRequestFormDataPart(part.getUploadFile(),
					name, fileName, part.getContentType(), body);
			newPart.setResolvedUploadFile(resolvedUploadFile);
			resolved.add(newPart);
		}

		return resolved;
	}

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener)
    throws InterruptedException, IOException
    {
		EnvVars envVars = build.getEnvironment(listener);
		envVars.putAll(build.getBuildVariables());

		HttpRequestExecution exec = HttpRequestExecution.from(this, envVars, build,
				this.getQuiet() ? TaskListener.NULL : listener);
		VirtualChannel channel = launcher.getChannel();
		if (channel == null) {
			throw new IllegalStateException("Launcher doesn't support remoting but it is required");
		}
		channel.call(exec);

        return true;
    }

	public boolean isUseNtlm() {
		return useNtlm;
	}

	public void setUseNtlm(boolean useNtlm) {
		this.useNtlm = useNtlm;
	}

	@Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
		public static final boolean ignoreSslErrors = false;
		public static final HttpMode httpMode                  = HttpMode.GET;
		public static final String   httpProxy                 = "";
		public static final String proxyAuthentication         = "";
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
        public static final String   uploadFile                = "";
        public static final String   multipartName             = "";
        public static final boolean  wrapAsMultipart           = true;
        public static final Boolean  useSystemProperties       = false;
        public static final boolean  useNtlm                   = false;
        public static final List<HttpRequestNameValuePair> customHeaders = Collections.emptyList();
        public static final List<HttpRequestFormDataPart> formData = Collections.emptyList();

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @NonNull
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

        public ListBoxModel doFillProxyAuthenticationItems(@AncestorInPath Item project,
														   @QueryParameter String url) {
			if (project == null || !project.hasPermission(Item.CONFIGURE)) {
				return new StandardListBoxModel();
			} else {
				return new StandardListBoxModel()
						.includeEmptyValue()
						.includeAs(ACL.SYSTEM,
								project, StandardUsernamePasswordCredentials.class,
								URIRequirementBuilder.fromUri(url).build());
			}
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

        public static List<IntStream> parseToRange(String value) {
            List<IntStream> validRanges = new ArrayList<>();

            if (value == null || value.isEmpty()) {
                value = HttpRequest.DescriptorImpl.validResponseCodes;
            }

            String[] codes = value.split(",");
            for (String code : codes) {
                String[] fromTo = code.trim().split(":");
                if (fromTo.length > 2) {
                    throw new IllegalArgumentException(String.format("Code %s should be an interval from:to or a single value", code));
                }

                int from;
                try {
                    from = Integer.parseInt(fromTo[0]);
                } catch (NumberFormatException nfe) {
                    throw new IllegalArgumentException("Invalid number "+fromTo[0]);
                }

                int to = from;
                if (fromTo.length != 1) {
                    try {
                        to = Integer.parseInt(fromTo[1]);
                    } catch (NumberFormatException nfe) {
                        throw new IllegalArgumentException("Invalid number "+fromTo[1]);
                    }
                }

                if (from > to) {
                    throw new IllegalArgumentException(String.format("Interval %s should be FROM less than TO", code));
                }
                validRanges.add(IntStream.rangeClosed(from, to));
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
