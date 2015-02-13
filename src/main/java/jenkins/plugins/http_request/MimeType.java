package jenkins.plugins.http_request;

import hudson.util.ListBoxModel;

/**
 * @author James Chapman
 */
public enum MimeType {

    TEXT_HTML, APPLICATION_JSON, APPLICATION_ZIP, APPLICATION_TAR, APPLICATION_OCTETSTREAM;

    public static ListBoxModel getContentTypeFillItems() {
        ListBoxModel items = new ListBoxModel();
        for (MimeType mimeType : values()) {
            items.add(mimeType.name());
        }
        return items;
    }
}
