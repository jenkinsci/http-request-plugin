package jenkins.plugins.http_request;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import hudson.util.ListBoxModel;

/**
 * @author Janario Oliveira
 */
public enum HttpMode {
    GET,
    HEAD,
    POST,
    PUT,
    DELETE,
    OPTIONS,
    PATCH,
    MKCOL;

	public static final Set<HttpMode> SUPPORTED_HTTP_MODES =
			Collections.unmodifiableSet(EnumSet.of(HttpMode.POST, HttpMode.PUT, HttpMode.DELETE, HttpMode.PATCH));

    public static ListBoxModel getFillItems() {
        ListBoxModel items = new ListBoxModel();
        for (HttpMode httpMode : values()) {
            items.add(httpMode.name());
        }
        return items;
    }

    public static ListBoxModel getPublisherFillItems() {
        ListBoxModel items = new ListBoxModel();
        for (HttpMode httpMode : values()) {
			if (SUPPORTED_HTTP_MODES.contains(httpMode)) {
				items.add(httpMode.name());
			}
        }
        return items;
    }
}
