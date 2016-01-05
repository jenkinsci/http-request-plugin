package jenkins.plugins.http_request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import hudson.model.Result;
import hudson.model.FreeStyleProject;
import hudson.tasks.Builder;

import org.junit.Rule;
import org.junit.Test;
import org.junit.Ignore;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jenkins.plugins.http_request.util.NameValuePair;
import jenkins.plugins.http_request.util.RequestAction;
import jenkins.plugins.http_request.auth.BasicDigestAuthentication;
import jenkins.plugins.http_request.auth.FormAuthentication;

public class HttpRequestRoundTripTest {

    HttpRequest httpRequest;

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void configRoundTripBasic() throws Exception {
        configRoundTrip(new HttpRequest("http://domain/", HttpMode.POST, "", MimeType.NOT_SET,   MimeType.NOT_SET,   "",           null, false, false, null, 0, "", ""));
        configRoundTrip(new HttpRequest("http://foo.bar", HttpMode.POST, "", MimeType.NOT_SET,   MimeType.NOT_SET,   "",           null, false, false, null, 0, "", ""));
        configRoundTrip(new HttpRequest("http://foo.bar", HttpMode.GET,  "", MimeType.NOT_SET,   MimeType.NOT_SET,   "",           null, false, false, null, 0, "", ""));
        configRoundTrip(new HttpRequest("http://foo.bar", HttpMode.POST, "", MimeType.TEXT_HTML, MimeType.NOT_SET,   "",           null, false, false, null, 0, "", ""));
        configRoundTrip(new HttpRequest("http://foo.bar", HttpMode.POST, "", MimeType.NOT_SET,   MimeType.TEXT_HTML, "",           null, false, false, null, 0, "", ""));
        configRoundTrip(new HttpRequest("http://foo.bar", HttpMode.POST, "", MimeType.NOT_SET,   MimeType.NOT_SET,   "myfile.txt", null, false, false, null, 0, "", ""));
        configRoundTrip(new HttpRequest("http://foo.bar", HttpMode.POST, "", MimeType.NOT_SET,   MimeType.NOT_SET,   "",           null, true,  false, null, 0, "", ""));
        configRoundTrip(new HttpRequest("http://foo.bar", HttpMode.POST, "", MimeType.NOT_SET,   MimeType.NOT_SET,   "",           null, false, true,  null, 0, "", ""));
        configRoundTrip(new HttpRequest("http://foo.bar", HttpMode.POST, "", MimeType.NOT_SET,   MimeType.NOT_SET,   "",           null, false, false, null, 1, "", ""));
        configRoundTrip(new HttpRequest("http://foo.bar", HttpMode.POST, "", MimeType.NOT_SET,   MimeType.NOT_SET,   "",           null, false, false, null, 0, "100:599", ""));
        configRoundTrip(new HttpRequest("http://foo.bar", HttpMode.POST, "", MimeType.NOT_SET,   MimeType.NOT_SET,   "",           null, false, false, null, 0, "", ""));
        configRoundTrip(new HttpRequest("http://foo.bar", HttpMode.POST, "", MimeType.NOT_SET,   MimeType.NOT_SET,   "",           null, false, false, null, 0, "", "content"));
        configRoundTrip(new HttpRequest("http://domain/", HttpMode.POST, "", MimeType.NOT_SET,   MimeType.NOT_SET,   "",           null, false, false, null, 0, "", ""));
    }

    @Test
    public void configRoundTripBasicAuthentication() throws Exception {
        List<BasicDigestAuthentication> bda = new ArrayList<BasicDigestAuthentication>();
        bda.add(new BasicDigestAuthentication("keyname","username","password"));
        HttpRequest before = new HttpRequest("http://foo.bar", HttpMode.POST, "keyname", MimeType.NOT_SET, MimeType.NOT_SET, "", null, false, false, null, 0, "100:399", "");
        before.getDescriptor().setBasicDigestAuthentications(bda);
        configRoundTrip(before);
    }

    @Test
    public void configRoundTripFormAuthentication() throws Exception {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new NameValuePair("param1","value1"));

        RequestAction action = new RequestAction(new URL("http://www.domain.com/"),HttpMode.GET,params);
        List<RequestAction> actions = new ArrayList<RequestAction>();
        actions.add(action);

        FormAuthentication formAuth = new FormAuthentication("keyname",actions);
        List<FormAuthentication> formAuthList = new ArrayList<FormAuthentication>();
        formAuthList.add(formAuth);

        HttpRequest before = new HttpRequest("http://foo.bar", HttpMode.POST, "keyname", MimeType.NOT_SET, MimeType.NOT_SET, "", null, false, false, null, 0, "100:399", "");
        before.getDescriptor().setFormAuthentications(formAuthList);
        configRoundTrip(before);
    }

    @Test
    public void configRoundTripCustomHeaders() throws Exception {
        List<NameValuePair> customHeaders = new ArrayList<NameValuePair>();
        customHeaders.add(new NameValuePair("param1","value1"));
        configRoundTrip(new HttpRequest("http://foo.bar", HttpMode.POST, "", MimeType.NOT_SET, MimeType.NOT_SET, "", null, false, false, customHeaders, 0, "100:399", ""));
    }

    @Ignore("Field returnCodeBuildRelevant is deprecated by author - do I really need to test it?")
    @Test
    public void configRoundTripReturnCodeBuildRelevant() throws Exception {
        configRoundTrip(new HttpRequest("http://foo.bar", HttpMode.POST, "", MimeType.NOT_SET, MimeType.NOT_SET, "", true,  false, false, null, 0, "100:399", ""));
        configRoundTrip(new HttpRequest("http://foo.bar", HttpMode.POST, "", MimeType.NOT_SET, MimeType.NOT_SET, "", false, false, false, null, 0, "100:399", ""));
    }

    private void configRoundTrip(HttpRequest before) throws Exception {
        HttpRequest after = doRoundTrip(before, HttpRequest.class);
        j.assertEqualBeans(before, after,"url,httpMode,contentType,acceptType,outputFile");
        j.assertEqualBeans(before, after,"authentication,consoleLogResponseBody,passBuildParameters,timeout");
        j.assertEqualBeans(before, after,"validResponseCodes,validResponseContent");
        // See @Ignore j.assertEqualBeans(before, after,"returnCodeBuildRelevant");
        if (before.getCustomHeaders() != null) {
            assertNotNull(after.getCustomHeaders());
            assertEquals(before.getCustomHeaders().size(),after.getCustomHeaders().size());
            for (int idx = 0; idx < before.getCustomHeaders().size(); idx++) {
              NameValuePair bnvp = before.getCustomHeaders().get(idx);
              NameValuePair anvp = after.getCustomHeaders().get(idx);
              assertEquals(bnvp.getName(),anvp.getName());
              assertEquals(bnvp.getValue(),anvp.getValue());
            }
        }
    }

    private <T extends Builder> T doRoundTrip(T before, Class<T> clazz) throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(before);

        j.submit(j.createWebClient().getPage(p,"configure").getFormByName("config"));
        T after = p.getBuildersList().get(clazz);
        return after;
    }
}
