package jenkins.plugins.http_request.util;

import java.net.MalformedURLException;
import java.net.URL;

import hudson.util.FormValidation;

/**
 * @author Janario Oliveira
 */
public class HttpRequestValidation {

    public static FormValidation checkUrl(String value) {
        try {
            new URL(value);
            return FormValidation.ok();
        } catch (MalformedURLException ex) {
            return FormValidation.error("Invalid url");
        }
    }
}
