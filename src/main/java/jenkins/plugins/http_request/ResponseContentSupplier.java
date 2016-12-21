package jenkins.plugins.http_request;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

/**
 * @author Martin d'Anjou
 *
 *         A container for the Http Response. The container is returned as is to the Pipeline. For the normal plugin, the container is consumed internally (since it cannot be returned).
 */
class ResponseContentSupplier implements Serializable {

	private static final long serialVersionUID = 1L;

	private String content;
	private int status;
	private Map<String, ArrayList<String>> headers = new HashMap<String, ArrayList<String>>();

	public ResponseContentSupplier(String content, int status) {
		this.content = content;
		this.status = status;
	}

	public ResponseContentSupplier(HttpResponse response) {
		this.status = response.getStatusLine().getStatusCode();
		setHeaders(response);
		setContent(response);
	}

	@Whitelisted
	public int getStatus() {
		return this.status;
	}

	@Whitelisted
	public String getContent() {
		return this.content;
	}

	@Whitelisted
	public Map<String, ArrayList<String>> getHeaders() {
		return this.headers;
	}

	private void setContent(HttpResponse response) {
		try {
			HttpEntity entity = response.getEntity();
			if (entity == null) {
				return;
			}
			this.content = EntityUtils.toString(entity);
			EntityUtils.consume(response.getEntity());
		}
		catch (IOException e) {
			this.content = "IOException while reading HttpResponse: " + e.getMessage();
		}
	}

	private void setHeaders(HttpResponse response) {
		try {
			Header[] respHeaders = response.getAllHeaders();
			for (Header respHeader : respHeaders) {
				if (!this.headers.containsKey(respHeader.getName())) {
					this.headers.put(respHeader.getName(), new ArrayList<String>());
				}
				this.headers.get(respHeader.getName()).add(respHeader.getValue());
			}
		}
		catch (Exception e) {
			this.content = "Exception while reading HttpResponse headers: " + e.getMessage();
		}
	}

	@Override
	public String toString() {
		return "Status: " + this.status + ", Response: " + this.content;
	}
}
