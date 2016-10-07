package jenkins.plugins.http_request;

import hudson.util.ListBoxModel;

/**
 * @author James Chapman
 */
public enum MimeType {

	NOT_SET(""), TEXT_HTML("text/html"), TEXT_PLAIN("text/plain"), APPLICATION_FORM("application/x-www-form-urlencoded"), APPLICATION_JSON("application/json"), APPLICATION_TAR("application/x-tar"), APPLICATION_ZIP(
			"application/zip"), APPLICATION_OCTETSTREAM("application/octet-stream");

	private final String value;

	MimeType(String value) {
		this.value = value;
	}

	public String getValue() {
		return this.value;
	}

	public static ListBoxModel getContentTypeFillItems() {
		ListBoxModel items = new ListBoxModel();
		for (MimeType mimeType : values()) {
			items.add(mimeType.name());
		}
		return items;
	}

}
