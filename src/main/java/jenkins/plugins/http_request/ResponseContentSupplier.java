package jenkins.plugins.http_request;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

import org.apache.commons.io.IOUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * A container for the Http Response.
 *
 * The container is returned as is to the Pipeline.
 * For the normal plugin, the container is consumed internally (since it cannot be returned).
 *
 * @author Martin d'Anjou
 */
public class ResponseContentSupplier implements Serializable, AutoCloseable {

	private static final long serialVersionUID = 1L;

	private int status;
	private Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
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

	public ResponseContentSupplier(ResponseHandle responseHandle, HttpResponse response) {
		this.status = response.getStatusLine().getStatusCode();
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
		if(contentStream == null) {
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

	private void readCharset(HttpResponse response) {
		Charset charset = null;
		ContentType contentType = ContentType.get(response.getEntity());
		if (contentType != null) {
			charset = contentType.getCharset();
			if (charset == null) {
				ContentType defaultContentType = ContentType.getByMimeType(contentType.getMimeType());
				if (defaultContentType != null) {
					charset = defaultContentType.getCharset();
				}
			}
		}
		if (charset != null) {
			this.charset = charset.name();
		}
	}

	private void readHeaders(HttpResponse response) {
		Header[] respHeaders = response.getAllHeaders();
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
