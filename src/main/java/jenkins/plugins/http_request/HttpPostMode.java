package jenkins.plugins.http_request;

import hudson.util.ListBoxModel;

/**
 * @author Janario Oliveira
 */
public enum HttpPostMode {
    POST,
    PUT,
    DELETE,
    PATCH;

    public static ListBoxModel getFillItems() {
        ListBoxModel items = new ListBoxModel();
        for (HttpPostMode httpMode : values()) {
            items.add(httpMode.name());
        }
        return items;
    }
}
