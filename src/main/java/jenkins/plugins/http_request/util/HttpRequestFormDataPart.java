package jenkins.plugins.http_request.util;

import java.io.Serializable;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.FormValidation.CheckMethod;

public class HttpRequestFormDataPart extends AbstractDescribableImpl<HttpRequestFormDataPart>
		implements Serializable {

	private static final long serialVersionUID = 1L;
	private final String contentType;
	private final String body;
	private final String name;
	private final String fileName;
	private final String uploadFile;
	private FilePath resolvedUploadFile;

	@DataBoundConstructor
	public HttpRequestFormDataPart(final String uploadFile, final String name,
			final String fileName, final String contentType, final String body) {
		this.contentType = contentType;
		this.body = body;
		this.name = name;
		this.fileName = fileName;
		this.uploadFile = uploadFile;
	}

	public String getBody() {
		return body;
	}

	public String getContentType() {
		return contentType;
	}

	public String getFileName() {
		return fileName;
	}

	public String getName() {
		return name;
	}

	public String getUploadFile() {
		return uploadFile;
	}

	public FilePath getResolvedUploadFile() {
		return resolvedUploadFile;
	}

	public void setResolvedUploadFile(FilePath resolvedUploadFile) {
		this.resolvedUploadFile = resolvedUploadFile;
	}

	@Extension
	public static class FormDataDescriptor extends Descriptor<HttpRequestFormDataPart> {
		@Override
		public String getDisplayName() {
			return "Multipart Form Data Entry";
		}

		public FormValidation doCheckName(@QueryParameter String value) {
			return FormValidation.validateRequired(value);
		}
	}
}
