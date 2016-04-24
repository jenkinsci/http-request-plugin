package jenkins.plugins.http_request;

import java.io.IOException;
import java.io.Serializable;

import org.apache.http.HttpResponse;
import org.apache.http.HttpEntity;
import org.apache.http.util.EntityUtils;

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

/**
 * @author Martin d'Anjou
 *
 * A container for the Http Response.
 * The container is returned as is to the Pipeline. For the normal
 * plugin, the container is consumed internally (since it cannot be returned).
 */
class ResponseContentSupplier implements Serializable {

    private static final long serialVersionUID = 1L;

    private String content;
    private int status;

    public ResponseContentSupplier(HttpResponse response) {
        this.status = response.getStatusLine().getStatusCode();
        setContent(response);
    }

    @Whitelisted
    public int getStatus() {
        return status;
    }

    @Whitelisted
    public String getContent() {
        return content;
    }

    private void setContent(HttpResponse response) {
        try {
            HttpEntity entity = response.getEntity();
            if (entity == null) {
                return;
            }
            content = EntityUtils.toString(entity);
            EntityUtils.consume(response.getEntity());
        } catch (IOException e) {
            content = "IOException while reading HttpResponse: "+e.getMessage();
        }
    }

    @Override
    public String toString() {
        return "Status: "+status+", Response: "+content;
    }
}
