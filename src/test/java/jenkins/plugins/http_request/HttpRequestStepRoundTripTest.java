package jenkins.plugins.http_request;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import jenkins.plugins.http_request.auth.FormAuthentication;
import jenkins.plugins.http_request.util.HttpRequestNameValuePair;
import jenkins.plugins.http_request.util.RequestAction;

import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Martin d'Anjou
 */
public class HttpRequestStepRoundTripTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    public static HttpRequestStep before = new HttpRequestStep("http://domain/");

    @Test
    public void configRoundTripGroup1() throws Exception {
        configRoundTrip(before);
        before.setHttpMode(HttpMode.GET);
        configRoundTrip(before);
        before.setValidResponseCodes("100:599");
        configRoundTrip(before);
        before.setValidResponseContent("some content we want to see");
        configRoundTrip(before);
        before.setAcceptType(MimeType.TEXT_HTML);
        configRoundTrip(before);
        before.setContentType(MimeType.TEXT_HTML);
        configRoundTrip(before);
    }

    @Test
    public void configRoundtripGroup2() throws Exception {
        before.setTimeout(12);
        configRoundTrip(before);
        before.setConsoleLogResponseBody(true);
        configRoundTrip(before);
        before.setConsoleLogResponseBody(false);
        configRoundTrip(before);
    }

    @Test
    public void configRoundtripGroup3() throws Exception {
        configRoundTrip(before);

        List<HttpRequestNameValuePair> params = new ArrayList<>();
        params.add(new HttpRequestNameValuePair("param1","value1"));
        params.add(new HttpRequestNameValuePair("param2","value2"));

        RequestAction action = new RequestAction(new URL("http://www.domain.com/"),HttpMode.GET,null,params);
        List<RequestAction> actions = new ArrayList<>();
        actions.add(action);

        FormAuthentication formAuth = new FormAuthentication("keyname",actions);
        List<FormAuthentication> formAuthList = new ArrayList<>();
        formAuthList.add(formAuth);

        HttpRequestGlobalConfig.get().setFormAuthentications(formAuthList);
        configRoundTrip(before);

        List<HttpRequestNameValuePair> customHeaders = new ArrayList<>();
        customHeaders.add(new HttpRequestNameValuePair("param1","value1"));
        before.setCustomHeaders(customHeaders);
        configRoundTrip(before);
    }

    @Test
    public void configRoundtripGroup4() throws Exception {
        before.setUploadFile("upload.txt");
        configRoundTrip(before);
        before.setMultipartName("filename");
        configRoundTrip(before);
    }

    private void configRoundTrip(HttpRequestStep before) throws Exception {
        HttpRequestStep after  = new StepConfigTester(j).configRoundTrip(before);
        j.assertEqualBeans(before, after, "httpMode");
        j.assertEqualBeans(before, after, "url");
        j.assertEqualBeans(before, after, "validResponseCodes");
        j.assertEqualBeans(before, after, "validResponseContent");
        j.assertEqualBeans(before, after, "acceptType");
        j.assertEqualBeans(before, after, "contentType");
        j.assertEqualBeans(before, after, "uploadFile");
        j.assertEqualBeans(before, after, "multipartName");
        j.assertEqualBeans(before, after, "timeout");
        j.assertEqualBeans(before, after, "consoleLogResponseBody");
        j.assertEqualBeans(before, after, "authentication");

        // Custom header check
        assertEquals(before.getCustomHeaders().size(),after.getCustomHeaders().size());
        for (int idx = 0; idx < before.getCustomHeaders().size(); idx++) {
          HttpRequestNameValuePair bnvp = before.getCustomHeaders().get(idx);
          HttpRequestNameValuePair anvp = after.getCustomHeaders().get(idx);
          assertEquals(bnvp.getName(),anvp.getName());
          assertEquals(bnvp.getValue(),anvp.getValue());
        }

        // Form authentication check
        List<FormAuthentication> beforeFas = HttpRequestGlobalConfig.get().getFormAuthentications();
        List<FormAuthentication> afterFas  = HttpRequestGlobalConfig.get().getFormAuthentications();
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
                List<HttpRequestNameValuePair> beforeParams = beforeAction.getParams();
                List<HttpRequestNameValuePair> afterParams  = afterAction.getParams();
                assertEquals(beforeParams.size(), afterParams.size());
                for (int kdx = 0; kdx < beforeParams.size(); kdx++) {
                    HttpRequestNameValuePair beforeNvp = beforeParams.get(kdx);
                    HttpRequestNameValuePair afterNvp  = afterParams.get(kdx);
                    assertEquals(beforeNvp.getName(), afterNvp.getName());
                    assertEquals(beforeNvp.getValue(), afterNvp.getValue());
                }
            }
        }
    }
}
