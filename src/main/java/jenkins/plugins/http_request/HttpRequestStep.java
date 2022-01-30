package jenkins.plugins.http_request;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import edu.umd.cs.findbugs.annotations.NonNull;

import org.apache.http.HttpHeaders;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import jenkins.plugins.http_request.util.HttpRequestFormDataPart;
import jenkins.plugins.http_request.util.HttpRequestNameValuePair;

/**
 * @author Martin d'Anjou
 */
public final class HttpRequestStep extends Step {

    private final @NonNull String url;
	private boolean ignoreSslErrors = DescriptorImpl.ignoreSslErrors;
	private HttpMode httpMode                 = DescriptorImpl.httpMode;
    private String httpProxy                  = DescriptorImpl.httpProxy;
    private String proxyAuthentication        = DescriptorImpl.proxyAuthentication;
    private String validResponseCodes         = DescriptorImpl.validResponseCodes;
    private String validResponseContent       = DescriptorImpl.validResponseContent;
    private MimeType acceptType               = DescriptorImpl.acceptType;
    private MimeType contentType              = DescriptorImpl.contentType;
    private Integer timeout                   = DescriptorImpl.timeout;
    private Boolean consoleLogResponseBody    = DescriptorImpl.consoleLogResponseBody;
    private Boolean quiet                     = DescriptorImpl.quiet;
    private String authentication             = DescriptorImpl.authentication;
    private String requestBody                = DescriptorImpl.requestBody;
    private String uploadFile                 = DescriptorImpl.uploadFile;
    private String multipartName              = DescriptorImpl.multipartName;
    private boolean wrapAsMultipart           = DescriptorImpl.wrapAsMultipart;
    private Boolean useSystemProperties       = DescriptorImpl.useSystemProperties;
    private boolean useNtlm                   = DescriptorImpl.useNtlm;
    private List<HttpRequestNameValuePair> customHeaders = DescriptorImpl.customHeaders;
	private List<HttpRequestFormDataPart> formData = DescriptorImpl.formData;
	private String outputFile = DescriptorImpl.outputFile;
	private ResponseHandle responseHandle = DescriptorImpl.responseHandle;

    @DataBoundConstructor
    public HttpRequestStep(@NonNull String url) {
        this.url = url;
    }

	@NonNull
	public String getUrl() {
		return url;
	}

	public boolean isIgnoreSslErrors() {
		return ignoreSslErrors;
	}

	@DataBoundSetter
	public void setIgnoreSslErrors(boolean ignoreSslErrors) {
		this.ignoreSslErrors = ignoreSslErrors;
	}

	@DataBoundSetter
    public void setHttpMode(HttpMode httpMode) {
        this.httpMode = httpMode;
    }

    public HttpMode getHttpMode() {
        return httpMode;
    }

    @DataBoundSetter
    public void setHttpProxy(String httpProxy) {
        this.httpProxy = httpProxy;
    }

    public String getHttpProxy() {
        return httpProxy;
    }

    @DataBoundSetter
    public void setValidResponseCodes(String validResponseCodes) {
        this.validResponseCodes = validResponseCodes;
    }

    public String getValidResponseCodes() {
        return validResponseCodes;
    }

    @DataBoundSetter
    public void setValidResponseContent(String validResponseContent) {
        this.validResponseContent = validResponseContent;
    }

    public String getValidResponseContent() {
        return validResponseContent;
    }

    @DataBoundSetter
    public void setAcceptType(MimeType acceptType) {
        this.acceptType = acceptType;
    }

    public MimeType getAcceptType() {
        return acceptType;
    }

    @DataBoundSetter
    public void setContentType(MimeType contentType) {
        this.contentType = contentType;
    }

    public MimeType getContentType() {
        return contentType;
    }

    @DataBoundSetter
    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Integer getTimeout() {
        return timeout;
    }

    @DataBoundSetter
    public void setConsoleLogResponseBody(Boolean consoleLogResponseBody) {
        this.consoleLogResponseBody = consoleLogResponseBody;
    }

    public Boolean getConsoleLogResponseBody() {
        return consoleLogResponseBody;
    }

    @DataBoundSetter
    public void setQuiet(Boolean quiet) {
        this.quiet = quiet;
    }

    public Boolean getQuiet() {
        return quiet;
    }

    @DataBoundSetter
    public void setAuthentication(String authentication) {
        this.authentication = authentication;
    }

    public String getAuthentication() {
        return authentication;
    }

	@DataBoundSetter
	public void setProxyAuthentication(String proxyAuthentication) {
		this.proxyAuthentication = proxyAuthentication;
	}

	public String getProxyAuthentication() {
		return proxyAuthentication;
	}

	@DataBoundSetter
    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getRequestBody() {
        return requestBody;
    }

	@DataBoundSetter
	public void setUseSystemProperties(Boolean useSystemProperties) {
		this.useSystemProperties = useSystemProperties;
	}

	public Boolean getUseSystemProperties() {
		return useSystemProperties;
	}

    @DataBoundSetter
    public void setCustomHeaders(List<HttpRequestNameValuePair> customHeaders) {
        this.customHeaders = customHeaders;
    }

    public List<HttpRequestNameValuePair> getCustomHeaders() {
        return customHeaders;
    }

	public List<HttpRequestFormDataPart> getFormData() {
		return formData;
	}

	@DataBoundSetter
	public void setFormData(List<HttpRequestFormDataPart> formData) {
		this.formData = Collections.unmodifiableList(formData);
	}

	public String getOutputFile() {
		return outputFile;
	}

	@DataBoundSetter
	public void setOutputFile(String outputFile) {
		this.outputFile = outputFile;
	}

	public ResponseHandle getResponseHandle() {
		return responseHandle;
	}


	@DataBoundSetter
	public void setResponseHandle(ResponseHandle responseHandle) {
		this.responseHandle = responseHandle;
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

	public boolean isWrapAsMultipart() {
		return wrapAsMultipart;
	}

	@DataBoundSetter
	public void setWrapAsMultipart(boolean wrapAsMultipart) {
		this.wrapAsMultipart = wrapAsMultipart;
	}
	
	@DataBoundSetter
	public void setUseNtlm(boolean useNtlm) {
		this.useNtlm = useNtlm;
	}

	public boolean isUseNtlm() {
		return useNtlm;
	}

	@Override
	public StepExecution start(StepContext context) {
		return new Execution(context, this);
	}

	@Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

	List<HttpRequestNameValuePair> resolveHeaders() {
		final List<HttpRequestNameValuePair> headers = new ArrayList<>();
		if (contentType != null && contentType != MimeType.NOT_SET) {
			headers.add(new HttpRequestNameValuePair(HttpHeaders.CONTENT_TYPE, contentType.getContentType().toString()));
		}
		if (acceptType != null && acceptType != MimeType.NOT_SET) {
			headers.add(new HttpRequestNameValuePair(HttpHeaders.ACCEPT, acceptType.getValue()));
		}
		for (HttpRequestNameValuePair header : customHeaders) {
			String headerName = header.getName();
			String headerValue = header.getValue();
			boolean maskValue = headerName.equalsIgnoreCase(HttpHeaders.AUTHORIZATION) ||
					header.getMaskValue();

			headers.add(new HttpRequestNameValuePair(headerName, headerValue, maskValue));
		}
		return headers;
	}

	@Extension
    public static final class DescriptorImpl extends StepDescriptor {
        public static final boolean ignoreSslErrors = HttpRequest.DescriptorImpl.ignoreSslErrors;
        public static final HttpMode httpMode                  = HttpRequest.DescriptorImpl.httpMode;
        public static final String   httpProxy                 = HttpRequest.DescriptorImpl.httpProxy;
        public static final String   proxyAuthentication       = HttpRequest.DescriptorImpl.proxyAuthentication;
        public static final String   validResponseCodes        = HttpRequest.DescriptorImpl.validResponseCodes;
        public static final String   validResponseContent      = HttpRequest.DescriptorImpl.validResponseContent;
        public static final MimeType acceptType                = HttpRequest.DescriptorImpl.acceptType;
        public static final MimeType contentType               = HttpRequest.DescriptorImpl.contentType;
        public static final int      timeout                   = HttpRequest.DescriptorImpl.timeout;
        public static final Boolean  consoleLogResponseBody    = HttpRequest.DescriptorImpl.consoleLogResponseBody;
        public static final Boolean  quiet                     = HttpRequest.DescriptorImpl.quiet;
        public static final String   authentication            = HttpRequest.DescriptorImpl.authentication;
        public static final String   requestBody               = HttpRequest.DescriptorImpl.requestBody;
        public static final String   uploadFile                = HttpRequest.DescriptorImpl.uploadFile;
        public static final String   multipartName             = HttpRequest.DescriptorImpl.multipartName;
        public static final boolean  wrapAsMultipart           = HttpRequest.DescriptorImpl.wrapAsMultipart;
        public static final Boolean  useSystemProperties       = HttpRequest.DescriptorImpl.useSystemProperties;
        public static final boolean  useNtlm                   = HttpRequest.DescriptorImpl.useNtlm;
        public static final List <HttpRequestNameValuePair> customHeaders = Collections.emptyList();
        public static final List <HttpRequestFormDataPart> formData = Collections.emptyList();
        public static final String outputFile = "";
		public static final ResponseHandle responseHandle = ResponseHandle.STRING;

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            Set<Class<?>> context = new HashSet<>();
            Collections.addAll(context, Run.class, TaskListener.class);
            return Collections.unmodifiableSet(context);
        }

		@Override
        public String getFunctionName() {
            return "httpRequest";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Perform an HTTP Request and return a response object";
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

		public ListBoxModel doFillResponseHandleItems() {
			ListBoxModel items = new ListBoxModel();
			for (ResponseHandle responseHandle : ResponseHandle.values()) {
				items.add(responseHandle.name());
			}
			return items;
		}

        public ListBoxModel doFillAuthenticationItems(@AncestorInPath Item project,
													  @QueryParameter String url) {
            return HttpRequest.DescriptorImpl.fillAuthenticationItems(project, url);
        }

		public ListBoxModel doFillProxyAuthenticationItems(@AncestorInPath Item project,
														   @QueryParameter String url) {
			return HttpRequest.DescriptorImpl.fillAuthenticationItems(project, url);
		}

		public FormValidation doCheckValidResponseCodes(@QueryParameter String value) {
            return HttpRequest.DescriptorImpl.checkValidResponseCodes(value);
        }

    }

    public static final class Execution extends SynchronousNonBlockingStepExecution<ResponseContentSupplier> {

        private final transient HttpRequestStep step;

		Execution(@NonNull StepContext context, HttpRequestStep step) {
			super(context);
			this.step = step;
		}

		@Override
		protected ResponseContentSupplier run() throws Exception {
			HttpRequestExecution exec = HttpRequestExecution.from(step,
					step.getQuiet() ? TaskListener.NULL : Objects.requireNonNull(getContext().get(TaskListener.class)),
					this);

			Launcher launcher = getContext().get(Launcher.class);
			if (launcher != null) {
				VirtualChannel channel = launcher.getChannel();
				if (channel == null) {
					throw new IllegalStateException("Launcher doesn't support remoting but it is required");
				}
				return channel.call(exec);
			}

			return exec.call();
		}

        private static final long serialVersionUID = 1L;

		FilePath resolveOutputFile() {
			String outputFile = step.getOutputFile();
			if (outputFile == null || outputFile.trim().isEmpty()) {
				return null;
			}

			try {
				FilePath workspace = getContext().get(FilePath.class);
				if (workspace == null) {
					throw new IllegalStateException("Could not find workspace to save file outputFile: " + outputFile +
							". You should use it inside a 'node' block");
				}
				return workspace.child(outputFile);
			} catch (IOException | InterruptedException e) {
				throw new IllegalStateException(e);
			}
		}

		FilePath resolveUploadFile() {
			return resolveUploadFileInternal(step.getUploadFile());
		}

		public Item getProject() throws IOException, InterruptedException {
			return Objects.requireNonNull(getContext().get(Run.class)).getParent();
		}

		private FilePath resolveUploadFileInternal(String path) {
			if (path == null || path.trim().isEmpty()) {
				return null;
			}

			try {
				FilePath workspace = getContext().get(FilePath.class);
				if (workspace == null) {
					throw new IllegalStateException("Could not find workspace to check existence of upload file: " + path +
							". You should use it inside a 'node' block");
				}
				FilePath uploadFilePath = workspace.child(path);
				if (!uploadFilePath.exists()) {
					throw new IllegalStateException("Could not find upload file: " + path);
				}
				return uploadFilePath;
			} catch (IOException | InterruptedException e) {
				throw new IllegalStateException(e);
			}
		}

		List<HttpRequestFormDataPart> resolveFormDataParts() {
			List<HttpRequestFormDataPart> formData = step.getFormData();
			if (formData == null || formData.isEmpty()) {
				return Collections.emptyList();
			}

			List<HttpRequestFormDataPart> resolved = new ArrayList<>(formData.size());

			for (HttpRequestFormDataPart part : formData) {
				HttpRequestFormDataPart newPart = new HttpRequestFormDataPart(part.getUploadFile(),
						part.getName(), part.getFileName(), part.getContentType(), part.getBody());
				newPart.setResolvedUploadFile(resolveUploadFileInternal(part.getUploadFile()));
				resolved.add(newPart);
			}

			return resolved;
		}
	}
}
