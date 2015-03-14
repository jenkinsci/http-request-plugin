package jenkins.plugins.http_request;

import hudson.util.ListBoxModel;

/**
 * @author Janario Oliveira
 */
public enum HttpMode {

    HEAD, GET, POST, PUT, DELETE;

    public static ListBoxModel getFillItems() {
        ListBoxModel items = new ListBoxModel();
        for (HttpMode httpMode : values()) {
            items.add(httpMode.name());
        }
        return items;
    }
}
