package jenkins.plugins.http_request;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.apache.commons.io.FileUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThat;

import org.junit.Test;
import org.junit.Rule;

import org.jvnet.hudson.test.JenkinsRule;

public class HttpRequestTest {

    HttpRequest httpRequest;

    // Note: The JenkinsRule is required, otherwise the call to getDescriptor causes a null pointer exception
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void simpleGetTest() throws Exception {
        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(j.getURL().toString()+"api/json");
        httpRequest.setHttpMode(HttpMode.GET);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(build);
        String s = FileUtils.readFileToString(build.getLogFile());
        Pattern p = Pattern.compile("HttpMode: GET");
        Matcher m = p.matcher(s);
        assertTrue(m.find());
    }

    @Test
    public void detectActualContent() throws Exception {
        // Setup
        String findMe = "\"views\":[{\"name\":\"All\",\"url\":\"http://localhost:";
        String findMePattern = Pattern.quote(findMe);

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(j.getURL().toString()+"api/json");
        httpRequest.setHttpMode(HttpMode.GET);
        httpRequest.setValidResponseContent(findMe);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(build);
        String s = FileUtils.readFileToString(build.getLogFile());
        Pattern p = Pattern.compile(findMePattern);
        Matcher m = p.matcher(s);
        assertTrue(m.find());
    }

    @Test
    public void detectBadContent() throws Exception {
        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(j.getURL().toString()+"api/json");
        httpRequest.setHttpMode(HttpMode.GET);
        httpRequest.setValidResponseContent("bad content");

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.FAILURE, build);
        String s = FileUtils.readFileToString(build.getLogFile());
        assertThat(s, org.hamcrest.CoreMatchers.containsString("Expected content is not found. Aborting."));
        Pattern p = Pattern.compile("Fail: Response with length \\d+ doesn't contain 'bad content'");
        Matcher m = p.matcher(s);
        assertTrue(m.find());
    }

    @Test
    public void responseMatchAcceptedMimeType() throws Exception {
        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(j.getURL().toString()+"api/json");
        httpRequest.setHttpMode(HttpMode.GET);
        httpRequest.setAcceptType(MimeType.APPLICATION_JSON);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(build);
        String s = FileUtils.readFileToString(build.getLogFile());
        Pattern p = Pattern.compile("HttpMode: GET");
        Matcher m = p.matcher(s);
        assertTrue(m.find());
    }

    @Test
    public void responseDoesNotMatchAcceptedMimeType() throws Exception {
        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(j.getURL().toString()+"api/json");
        httpRequest.setHttpMode(HttpMode.GET);
        httpRequest.setAcceptType(MimeType.TEXT_HTML);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(build);
        String s = FileUtils.readFileToString(build.getLogFile());
        Pattern p = Pattern.compile("HttpMode: GET");
        Matcher m = p.matcher(s);
        assertTrue(m.find());
    }

    // TODO: for all these future tests, it would be ideal to have a mock server that receives and checks the actual http packets
    //    Different HttpModes (need mocking server?)
    //    Pass build parameters set to false in the presence of build parameters
    //    valid response codes, invalid response codes
    //    send different content types, check that they are effectively produced in the outgoing packets
    //    output file
    //    timeout
    //    set consoleLogResponseBody to false and check that nothing goes to console
    //    authentication (basic, form)
}
