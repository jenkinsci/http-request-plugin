package jenkins.plugins.http_request;

import hudson.util.ListBoxModel;

/**
 * @author James Chapman
 */
public enum ContentType {

    TEXT_HTML, APPLICATION_JSON;

    public static ListBoxModel getContentTypeFillItems() {
        ListBoxModel items = new ListBoxModel();
        for (ContentType contentType : values()) {
            items.add(contentType.name());
        }
        return items;
    }
}
