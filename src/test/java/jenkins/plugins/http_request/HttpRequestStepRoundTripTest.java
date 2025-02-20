package jenkins.plugins.http_request;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import jenkins.plugins.http_request.auth.FormAuthentication;
import jenkins.plugins.http_request.util.HttpRequestNameValuePair;
import jenkins.plugins.http_request.util.RequestAction;

import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.junit.jupiter.api.Test;

import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Martin d'Anjou
 */
@WithJenkins
class HttpRequestStepRoundTripTest {

    private static final HttpRequestStep before = new HttpRequestStep("http://domain/");

    @Test
    void configRoundTripGroup1(JenkinsRule j) throws Exception {
        configRoundTrip(j);
        before.setHttpMode(HttpMode.GET);
        configRoundTrip(j);
        before.setValidResponseCodes("100:599");
        configRoundTrip(j);
        before.setValidResponseContent("some content we want to see");
        configRoundTrip(j);
        before.setAcceptType(MimeType.TEXT_HTML);
        configRoundTrip(j);
        before.setContentType(MimeType.TEXT_HTML);
        configRoundTrip(j);
    }

    @Test
    void configRoundtripGroup2(JenkinsRule j) throws Exception {
        before.setTimeout(12);
        configRoundTrip(j);
        before.setConsoleLogResponseBody(true);
        configRoundTrip(j);
        before.setConsoleLogResponseBody(false);
        configRoundTrip(j);
    }

    @Test
    void configRoundtripGroup3(JenkinsRule j) throws Exception {
        configRoundTrip(j);

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
        configRoundTrip(j);

        List<HttpRequestNameValuePair> customHeaders = new ArrayList<>();
        customHeaders.add(new HttpRequestNameValuePair("param1","value1"));
        before.setCustomHeaders(customHeaders);
        configRoundTrip(j);
    }

    @Test
    void configRoundtripGroup4(JenkinsRule j) throws Exception {
        before.setUploadFile("upload.txt");
        configRoundTrip(j);
        before.setMultipartName("filename");
        configRoundTrip(j);
    }

    private static void configRoundTrip(JenkinsRule j) throws Exception {
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
