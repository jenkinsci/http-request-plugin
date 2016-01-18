package jenkins.plugins.http_request;

import hudson.model.FreeStyleProject;
import hudson.tasks.Builder;

import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

import org.jvnet.hudson.test.JenkinsRule;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import jenkins.plugins.http_request.auth.BasicDigestAuthentication;
import jenkins.plugins.http_request.auth.FormAuthentication;
import jenkins.plugins.http_request.util.NameValuePair;
import jenkins.plugins.http_request.util.RequestAction;

public class HttpRequestRoundTripTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void defaults() throws Exception {
        HttpRequest httpRequest;

        httpRequest = new HttpRequest("http://domain/");
        configRoundTrip(httpRequest);

        httpRequest.setHttpMode(HttpMode.GET);
        configRoundTrip(httpRequest);

        httpRequest.setPassBuildParameters(true);
        configRoundTrip(httpRequest);

        httpRequest.setPassBuildParameters(false);
        configRoundTrip(httpRequest);

        httpRequest.setValidResponseCodes("100:599");
        configRoundTrip(httpRequest);

        httpRequest.setValidResponseContent("some content we want to see");
        configRoundTrip(httpRequest);

        httpRequest.setAcceptType(MimeType.TEXT_HTML);
        configRoundTrip(httpRequest);

        httpRequest.setContentType(MimeType.TEXT_HTML);
        configRoundTrip(httpRequest);

        httpRequest.setOutputFile("myfile.txt");
        configRoundTrip(httpRequest);

        httpRequest.setTimeout(12);
        configRoundTrip(httpRequest);

        httpRequest.setConsoleLogResponseBody(true);
        configRoundTrip(httpRequest);

        httpRequest.setConsoleLogResponseBody(false);
        configRoundTrip(httpRequest);
    }

    @Test
    public void basicAuthentication() throws Exception {
        List<BasicDigestAuthentication> bda = new ArrayList<BasicDigestAuthentication>();
        bda.add(new BasicDigestAuthentication("keyname1","username1","password1"));
        bda.add(new BasicDigestAuthentication("keyname2","username2","password2"));
        HttpRequest before = new HttpRequest("http://foo.bar");
        before.getDescriptor().setBasicDigestAuthentications(bda);
        configRoundTrip(before);
    }

    @Test
    public void formAuthentication() throws Exception {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new NameValuePair("param1","value1"));
        params.add(new NameValuePair("param2","value2"));

        RequestAction action = new RequestAction(new URL("http://www.domain.com/"),HttpMode.GET,params);
        List<RequestAction> actions = new ArrayList<RequestAction>();
        actions.add(action);

        FormAuthentication formAuth = new FormAuthentication("keyname",actions);
        List<FormAuthentication> formAuthList = new ArrayList<FormAuthentication>();
        formAuthList.add(formAuth);

        HttpRequest before = new HttpRequest("http://foo.bar");
        before.getDescriptor().setFormAuthentications(formAuthList);
        configRoundTrip(before);
    }

    @Test
    public void customHeaders() throws Exception {
        HttpRequest httpRequest;
        List<NameValuePair> customHeaders = new ArrayList<NameValuePair>();
        customHeaders.add(new NameValuePair("param1","value1"));
        httpRequest = new HttpRequest("http://foo.bar");
        httpRequest.setCustomHeaders(customHeaders);
        configRoundTrip(httpRequest);
    }

    private void configRoundTrip(HttpRequest before) throws Exception {
        HttpRequest after = doRoundTrip(before, HttpRequest.class);
        j.assertEqualBeans(before, after, "httpMode,passBuildParameters");
        j.assertEqualBeans(before, after, "url");
        j.assertEqualBeans(before, after, "validResponseCodes,validResponseContent");
        j.assertEqualBeans(before, after, "acceptType,contentType");
        j.assertEqualBeans(before, after, "outputFile,timeout");
        j.assertEqualBeans(before, after, "consoleLogResponseBody");
        j.assertEqualBeans(before, after, "authentication");

        // Custom header check
        assertEquals(before.getCustomHeaders().size(),after.getCustomHeaders().size());
        for (int idx = 0; idx < before.getCustomHeaders().size(); idx++) {
          NameValuePair bnvp = before.getCustomHeaders().get(idx);
          NameValuePair anvp = after.getCustomHeaders().get(idx);
          assertEquals(bnvp.getName(),anvp.getName());
          assertEquals(bnvp.getValue(),anvp.getValue());
        }

        // Basic authentication check
        List<BasicDigestAuthentication> beforeBdas = before.getDescriptor().getBasicDigestAuthentications();
        List<BasicDigestAuthentication> afterBdas  = after.getDescriptor().getBasicDigestAuthentications();
        assertEquals(beforeBdas.size(), afterBdas.size());
        for (int idx = 0; idx < beforeBdas.size(); idx++) {
            BasicDigestAuthentication beforeBda = beforeBdas.get(idx);
            BasicDigestAuthentication afterBda = afterBdas.get(idx);
            assertEquals(beforeBda.getKeyName(), afterBda.getKeyName());
            assertEquals(beforeBda.getUserName(),afterBda.getUserName());
            assertEquals(beforeBda.getPassword(),afterBda.getPassword());
        }

        // Form authentication check
        List<FormAuthentication> beforeFas = before.getDescriptor().getFormAuthentications();
        List<FormAuthentication> afterFas  = after.getDescriptor().getFormAuthentications();
        assertEquals(beforeFas.size(), afterFas.size());
        for (int idx = 0; idx < beforeFas.size(); idx++) {
            FormAuthentication beforeFa = beforeFas.get(idx);
            FormAuthentication afterFa  = afterFas.get(idx);
            assertEquals(beforeFa.getKeyName(), afterFa.getKeyName());
            List<RequestAction> beforeActions = beforeFa.getActions();
            List<RequestAction> afterActions  = afterFa.getActions();
            assertEquals(beforeActions.size(), afterActions.size());
            for (int jdx = 0; jdx < beforeActions.size(); jdx ++) {
                RequestAction beforeAction = beforeActions.get(jdx);
                RequestAction afterAction  = afterActions.get(jdx);
                assertEquals(beforeAction.getUrl(), afterAction.getUrl());
                assertEquals(beforeAction.getMode(), afterAction.getMode());
                List<NameValuePair> beforeParams = beforeAction.getParams();
                List<NameValuePair> afterParams  = afterAction.getParams();
                assertEquals(beforeParams.size(), afterParams.size());
                for (int kdx = 0; kdx < beforeParams.size(); kdx++) {
                    NameValuePair beforeNvp = beforeParams.get(kdx);
                    NameValuePair afterNvp  = afterParams.get(kdx);
                    assertEquals(beforeNvp.getName(), afterNvp.getName());
                    assertEquals(beforeNvp.getValue(), afterNvp.getValue());
                }
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
