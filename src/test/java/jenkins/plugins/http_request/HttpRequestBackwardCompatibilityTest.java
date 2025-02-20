package jenkins.plugins.http_request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

import hudson.model.FreeStyleProject;
import hudson.tasks.Builder;

import jenkins.plugins.http_request.auth.FormAuthentication;
import jenkins.plugins.http_request.util.HttpRequestNameValuePair;
import jenkins.plugins.http_request.util.RequestAction;

/**
 * @author Martin d'Anjou
 */
@WithJenkins
class HttpRequestBackwardCompatibilityTest {

    @LocalData
    @Test
    void defaultGlobalConfig(JenkinsRule j) {
        // Test that config from 1.8.6 can be loaded
        HttpRequestGlobalConfig cfg = HttpRequestGlobalConfig.get();
        assertEquals(Collections.emptyList(), cfg.getFormAuthentications());
        assertEquals("jenkins.plugins.http_request.HttpRequest.xml", cfg.getConfigFile().getFile().getName());
    }

    @LocalData
    @Test
    void populatedGlobalConfig(JenkinsRule j) {
        // Test that global config from 1.8.6 can be loaded
        // Specifically tests the HttpRequestGlobalConfig.xStreamCompatibility() method
        // and the HttpRequestGlobalConfig.getConfigFile() method
        HttpRequestGlobalConfig cfg = HttpRequestGlobalConfig.get();

        List<FormAuthentication> fas = cfg.getFormAuthentications();
        assertEquals(1,fas.size());

        FormAuthentication fa = fas.iterator().next();
        assertEquals("k3", fa.getKeyName());
        List<RequestAction> ras = fa.getActions();
        assertEquals(1,ras.size());

        RequestAction ra = ras.iterator().next();
        assertEquals("http://localhost1",ra.getUrl().toString());
        assertEquals("GET",ra.getMode().toString());
        List<HttpRequestNameValuePair> nvps = ra.getParams();
        assertEquals(1,nvps.size());

        HttpRequestNameValuePair nvp = nvps.iterator().next();
        assertEquals("name1",nvp.getName());
        assertEquals("value1",nvp.getValue());
    }

    @LocalData
    @Test
    void oldConfigWithoutCustomHeadersShouldLoad(JenkinsRule j) {
        // Test that a job config from 1.8.6 can be loaded
        // Specifically tests the HttpRequest.readResolve() method
        FreeStyleProject p = (FreeStyleProject) j.getInstance().getItem("old");

        List<Builder> builders = p.getBuilders();

        HttpRequest httpRequest = (HttpRequest) builders.get(0);
        assertEquals("url", httpRequest.getUrl());
        assertNotNull(httpRequest.getCustomHeaders());
        assertNotNull(httpRequest.getValidResponseCodes());
        assertEquals("100:399", httpRequest.getValidResponseCodes());
    }

    @LocalData
    @Test
    void oldConfigWithCustomHeadersShouldLoad(JenkinsRule j) {
        // Test that a job config from 1.8.8 can be loaded
        // Specifically tests the HttpRequest.xStreamCompatibility() method
        FreeStyleProject p = (FreeStyleProject) j.getInstance().getItem("old");

        List<Builder> builders = p.getBuilders();

        HttpRequest httpRequest = (HttpRequest) builders.get(0);
        assertEquals("url", httpRequest.getUrl());

        assertNotNull(httpRequest.getCustomHeaders());
        List<HttpRequestNameValuePair> customHeaders = httpRequest.getCustomHeaders();
        assertEquals(1,customHeaders.size());
        Iterator<HttpRequestNameValuePair> itr = customHeaders.iterator();
        HttpRequestNameValuePair nvp = itr.next();
        assertEquals("h1",nvp.getName());
        assertEquals("v1",nvp.getValue());

        assertNotNull(httpRequest.getValidResponseCodes());
        assertEquals("100:399", httpRequest.getValidResponseCodes());
    }
}
