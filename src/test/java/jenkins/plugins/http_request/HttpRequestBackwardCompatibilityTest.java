package jenkins.plugins.http_request;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import jenkins.plugins.http_request.auth.BasicDigestAuthentication;
import jenkins.plugins.http_request.auth.FormAuthentication;
import jenkins.plugins.http_request.util.RequestAction;
import jenkins.plugins.http_request.util.HttpRequestNameValuePair;

import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * @author Martin d'Anjou
 */
public class HttpRequestBackwardCompatibilityTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @LocalData
    @Test
    public void defaultConfig() {
        HttpRequestGlobalConfig cfg = HttpRequestGlobalConfig.get();
        assertEquals(Collections.emptyList(), cfg.getBasicDigestAuthentications());
        assertEquals(Collections.emptyList(), cfg.getFormAuthentications());
    }

    @LocalData
    @Test
    public void populatedConfig() {
        HttpRequestGlobalConfig cfg = HttpRequestGlobalConfig.get();

        List<BasicDigestAuthentication> bdas = cfg.getBasicDigestAuthentications();
        assertEquals(2,bdas.size());
        Iterator itr = bdas.iterator();
        BasicDigestAuthentication bda = (BasicDigestAuthentication)itr.next();
        assertEquals("k1",bda.getKeyName());
        assertEquals("u1",bda.getUserName());
        assertEquals("p1",bda.getPassword());
        bda = (BasicDigestAuthentication)itr.next();
        assertEquals("k2",bda.getKeyName());
        assertEquals("u2",bda.getUserName());
        assertEquals("p2",bda.getPassword());

        List<FormAuthentication> fas = cfg.getFormAuthentications();
        assertEquals(1,fas.size());
        itr = fas.iterator();
        FormAuthentication fa = (FormAuthentication)itr.next();
        assertEquals("k3", fa.getKeyName());
        List<RequestAction> ras = fa.getActions();
        assertEquals(1,ras.size());
        itr = ras.iterator();
        RequestAction ra = (RequestAction)itr.next();
        assertEquals("http://localhost1",ra.getUrl().toString());
        assertEquals("GET",ra.getMode().toString());
        List<HttpRequestNameValuePair> nvps = ra.getParams();
        assertEquals(1,nvps.size());
        itr = nvps.iterator();
        HttpRequestNameValuePair nvp = (HttpRequestNameValuePair)itr.next();
        assertEquals("name1",nvp.getName());
        assertEquals("value1",nvp.getValue());
    }
}
