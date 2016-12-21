package jenkins.plugins.http_request;

import hudson.util.ListBoxModel;
import org.apache.http.entity.ContentType;

/**
 * @author James Chapman
 */
public enum MimeType {

    NOT_SET(ContentType.create("")),
    TEXT_HTML(ContentType.TEXT_HTML),
    TEXT_PLAIN(ContentType.TEXT_PLAIN),
    APPLICATION_JSON(ContentType.create("application/json")),
    APPLICATION_JSON_UTF8(ContentType.APPLICATION_JSON),
    APPLICATION_TAR(ContentType.create("application/x-tar")),
    APPLICATION_ZIP(ContentType.create("application/zip")),
    APPLICATION_OCTETSTREAM(ContentType.APPLICATION_OCTET_STREAM);

    private final ContentType contentType;

    MimeType(ContentType contentType) {
        this.contentType = contentType;
    }

    public String getValue() {
        return contentType.getMimeType();
    }

    public ContentType getContentType() {
        return contentType;
    }

    public static ListBoxModel getContentTypeFillItems() {
        ListBoxModel items = new ListBoxModel();
        for (MimeType mimeType : values()) {
            items.add(mimeType.name());
        }
        return items;
    }

}
