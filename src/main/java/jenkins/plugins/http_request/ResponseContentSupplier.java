package jenkins.plugins.http_request;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

import org.apache.commons.io.IOUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * A container for the Http Response.
 * <p>
 * The container is returned as is to the Pipeline.
 * For the normal plugin, the container is consumed internally (since it cannot be returned).
 *
 * @author Martin d'Anjou
 */
public class ResponseContentSupplier implements Serializable, AutoCloseable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int status;
    private final Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private String charset;

    private ResponseHandle responseHandle;
    private String content;
    @SuppressFBWarnings("SE_TRANSIENT_FIELD_NOT_RESTORED")
    private transient InputStream contentStream;
    @SuppressFBWarnings("SE_TRANSIENT_FIELD_NOT_RESTORED")
    private transient CloseableHttpClient httpclient;

    public ResponseContentSupplier(String content, int status) {
        this.content = content;
        this.status = status;
    }

    public ResponseContentSupplier(ResponseHandle responseHandle, CloseableHttpResponse response) {
        this.status = response.getCode();
        this.responseHandle = responseHandle;
        readHeaders(response);
        readCharset(response);

        try {
            HttpEntity entity = response.getEntity();
            InputStream entityContent = entity != null ? entity.getContent() : null;

            if (responseHandle == ResponseHandle.STRING && entityContent != null) {
                byte[] bytes = IOUtils.toByteArray(entityContent);
                contentStream = new ByteArrayInputStream(bytes);
                content = new String(bytes, charset == null || charset.isEmpty() ?
                        Charset.defaultCharset().name() : charset);
            } else {
                contentStream = entityContent;
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Whitelisted
    public int getStatus() {
        return this.status;
    }

    @Whitelisted
    public Map<String, List<String>> getHeaders() {
        return this.headers;
    }

    @Whitelisted
    public String getCharset() {
        return charset;
    }

    @Whitelisted
    public String getContent() {
        if (responseHandle == ResponseHandle.STRING) {
            return content;
        }
        if (content != null) {
            return content;
        }
        if (contentStream == null) {
            return null;
        }

        try (InputStreamReader in = new InputStreamReader(contentStream,
                charset == null || charset.isEmpty() ? Charset.defaultCharset().name() : charset)) {
            content = IOUtils.toString(in);
            return content;
        } catch (IOException e) {
            throw new IllegalStateException("Error reading response. " +
                    "If you are reading the content in pipeline you should pass responseHandle: 'LEAVE_OPEN' and " +
                    "close the response(response.close()) after consume it.", e);
        }
    }

    @Whitelisted
    public InputStream getContentStream() {
        return contentStream;
    }

    private void readCharset(ClassicHttpResponse response) {
        Charset charset = null;

        Header contentTypeHeader = response.getFirstHeader(HttpHeaders.CONTENT_TYPE);
        ContentType contentType = ContentType.parse(response.getEntity() != null ?
                        response.getEntity().getContentType() :
                        (contentTypeHeader != null ? contentTypeHeader.getValue() : null));
        if (contentType != null) {
            charset = contentType.getCharset();
            if (charset == null) {
                ContentType defaultContentType = ContentType.create(contentType.getMimeType());
                charset = defaultContentType.getCharset();
            }
        }
        if (charset != null) {
            this.charset = charset.name();
        }
    }

    private void readHeaders(HttpResponse response) {
        Header[] respHeaders = response.getHeaders();
        for (Header respHeader : respHeaders) {
            List<String> hs = headers.computeIfAbsent(respHeader.getName(), k -> new ArrayList<>());
            hs.add(respHeader.getValue());
        }
    }

    @Override
    public String toString() {
        return "Status: " + this.status;
    }

    @Override
    public void close() throws IOException {
        if (httpclient != null) {
            httpclient.close();
        }
        if (contentStream != null) {
            contentStream.close();
        }
    }

    void setHttpClient(CloseableHttpClient httpclient) {
        this.httpclient = httpclient;
    }
}
