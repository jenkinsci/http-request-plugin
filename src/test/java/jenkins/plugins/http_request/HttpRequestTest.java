package jenkins.plugins.http_request;

import hudson.model.Cause.UserIdCause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.StringParameterValue;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jenkins.plugins.http_request.auth.BasicDigestAuthentication;
import jenkins.plugins.http_request.auth.FormAuthentication;
import jenkins.plugins.http_request.util.NameValuePair;
import jenkins.plugins.http_request.util.RequestAction;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpHost;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

public class HttpRequestTest extends HttpRequestTestBase {

    @Test
    public void canBeSerialized() throws Exception {
        HttpRequest httpRequest = new HttpRequest("http://localhost");
        httpRequest.readResolve();
    }

    @Test
    public void simpleGetTest() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/doGET");
        httpRequest.setHttpMode(HttpMode.GET);
        httpRequest.setConsoleLogResponseBody(true);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(build);
        j.assertLogContains(allIsWellMessage,build);
    }

    @Test
    public void canDetectActualContent() throws Exception {
        // Setup the expected pattern
        String findMe = allIsWellMessage;
        String findMePattern = Pattern.quote(findMe);

        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/doGET");
        httpRequest.setHttpMode(HttpMode.GET);
        httpRequest.setConsoleLogResponseBody(true);
        httpRequest.setValidResponseContent(findMe);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(build);
        j.assertLogContains(findMe,build);
    }

    @Test
    public void badContentFailsTheBuild() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/doGET");
        httpRequest.setHttpMode(HttpMode.GET);
        httpRequest.setConsoleLogResponseBody(true);
        httpRequest.setValidResponseContent("bad content");

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.FAILURE, build);
        String s = FileUtils.readFileToString(build.getLogFile());
        Pattern p = Pattern.compile("Fail: Response with length \\d+ doesn't contain 'bad content'");
        Matcher m = p.matcher(s);
        assertTrue(m.find());
    }

    @Test
    public void responseMatchAcceptedMimeType() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/doGET");
        httpRequest.setHttpMode(HttpMode.GET);
        httpRequest.setConsoleLogResponseBody(true);

        // Expect a mime type that matches the response
        httpRequest.setAcceptType(MimeType.TEXT_PLAIN);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(build);
        j.assertLogContains(allIsWellMessage,build);
    }

    @Test
    public void responseDoesNotMatchAcceptedMimeTypeDoesNotFailTheBuild() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/doGET");
        httpRequest.setHttpMode(HttpMode.GET);
        httpRequest.setConsoleLogResponseBody(true);

        // Expect a mime type that does not match the response
        httpRequest.setAcceptType(MimeType.TEXT_HTML);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(build);
        j.assertLogContains(allIsWellMessage,build);
    }

    @Test
    public void passBuildParametersWhenAskedAndParamtersArePresent() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/checkBuildParameters");
        httpRequest.setHttpMode(HttpMode.GET);
        httpRequest.setConsoleLogResponseBody(true);

        // Activate passBuildParameters
        httpRequest.setPassBuildParameters(true);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0,
            new UserIdCause(),
            new ParametersAction(new StringParameterValue("foo","value"))
        ).get();

        // Check expectations
        j.assertBuildStatusSuccess(build);
        j.assertLogContains(allIsWellMessage,build);
    }

    @Test
    public void silentlyIgnoreNonExistentBuildParameters() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/doGET");
        httpRequest.setHttpMode(HttpMode.GET);
        httpRequest.setConsoleLogResponseBody(true);

        // Activate passBuildParameters without parameters present
        httpRequest.setPassBuildParameters(true);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(build);
        j.assertLogContains(allIsWellMessage,build);
    }

    @Test
    public void doNotPassBuildParametersWithBuildParameters() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/doGET");
        httpRequest.setHttpMode(HttpMode.GET);
        httpRequest.setConsoleLogResponseBody(true);

        // Activate passBuildParameters
        httpRequest.setPassBuildParameters(false);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0,
            new UserIdCause(),
            new ParametersAction(new StringParameterValue("foo","value"))
            ).get();

        // Check expectations
        j.assertBuildStatusSuccess(build);
        j.assertLogContains(allIsWellMessage,build);
    }

    @Test
    public void doAllRequestTypes() throws Exception {
        for (HttpMode mode: HttpMode.values()) {
            doRequest(mode);
        }
    }

    public void doRequest(final HttpMode mode) throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/do"+mode.toString());
        httpRequest.setHttpMode(mode);
        httpRequest.setConsoleLogResponseBody(true);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(build);

        if (mode == HttpMode.HEAD) return;

        j.assertLogContains(allIsWellMessage,build);
    }

    @Test
    public void invalidResponseCodeFailsTheBuild() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/invalidStatusCode");
        httpRequest.setHttpMode(HttpMode.GET);
        httpRequest.setConsoleLogResponseBody(true);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.FAILURE, build);
        j.assertLogContains("Throwing status 400 for test",build);
    }

    @Test
    public void invalidResponseCodeIsAccepted() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/invalidStatusCode");
        httpRequest.setValidResponseCodes("100:599");
        httpRequest.setHttpMode(HttpMode.GET);
        httpRequest.setConsoleLogResponseBody(true);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(build);
        j.assertLogContains("Throwing status 400 for test",build);
    }

    @Test
    public void sendAllContentTypes() throws Exception {
        for (MimeType mimeType : MimeType.values()) {
            sendContentType(mimeType);
        }
    }

    public void sendContentType(final MimeType mimeType) throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/incoming_"+mimeType.toString());
        httpRequest.setHttpMode(HttpMode.GET);
        httpRequest.setConsoleLogResponseBody(true);
        httpRequest.setContentType(mimeType);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(build);
        j.assertLogContains(allIsWellMessage,build);
    }

    @Test
    public void sendAllAcceptTypes() throws Exception {
        for (MimeType mimeType : MimeType.values()) {
            sendAcceptType(mimeType);
        }
    }

    public void sendAcceptType(final MimeType mimeType) throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/accept_"+mimeType.toString());
        httpRequest.setHttpMode(HttpMode.GET);
        httpRequest.setConsoleLogResponseBody(true);
        httpRequest.setAcceptType(mimeType);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(build);
        j.assertLogContains(allIsWellMessage,build);
    }

    @Test
    public void canPutResponseInOutputFile() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/doGET");
        httpRequest.setOutputFile("file.txt");
        httpRequest.setConsoleLogResponseBody(true);
        httpRequest.setHttpMode(HttpMode.GET);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(build);

        // By default, the response is printed to the console even if an outputFile is used
        j.assertLogContains(allIsWellMessage,build);

        // The response is in the output file as well
        String outputFile = build.getWorkspace().child("file.txt").readToString();
        Pattern p = Pattern.compile(allIsWellMessage);
        Matcher m = p.matcher(outputFile);
        assertTrue(m.find());
    }

    @Test
    public void canPutResponseInOutputFileWhenNotSetToGoToConsole() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/doGET");
        httpRequest.setOutputFile("file.txt");
        httpRequest.setHttpMode(HttpMode.GET);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(build);

        // Check that the console does NOT have the response body
        j.assertLogNotContains(allIsWellMessage,build);

        // The response is in the output file
        String outputFile = build.getWorkspace().child("file.txt").readToString();
        Pattern p = Pattern.compile(allIsWellMessage);
        Matcher m = p.matcher(outputFile);
        assertTrue(m.find());
    }

    @Test
    public void timeoutFailsTheBuild() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/timeout");
        httpRequest.setTimeout(2);
        httpRequest.setHttpMode(HttpMode.GET);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.FAILURE, build);
    }

    @Test
    public void canDoCustomHeaders() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        List<NameValuePair> customHeaders = new ArrayList<NameValuePair>();
        customHeaders.add(new NameValuePair("customHeader","value1"));
        customHeaders.add(new NameValuePair("customHeader","value2"));
        HttpRequest httpRequest = new HttpRequest(baseURL+"/customHeaders");
        httpRequest.setHttpMode(HttpMode.GET);
        httpRequest.setCustomHeaders(customHeaders);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.SUCCESS, build);
    }

    @Test
    public void nonExistentBasicAuthFailsTheBuild() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/basicAuth");
        httpRequest.setHttpMode(HttpMode.GET);
        httpRequest.setAuthentication("non-existent-key");

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.FAILURE, build);
    }

    @Test
    public void canDoBasicDigestAuthentication() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare the authentication
        List<BasicDigestAuthentication> bda = new ArrayList<BasicDigestAuthentication>();
        bda.add(new BasicDigestAuthentication("keyname1","username1","password1"));
        bda.add(new BasicDigestAuthentication("keyname2","username2","password2"));

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/basicAuth");
        httpRequest.setHttpMode(HttpMode.GET);
        HttpRequestGlobalConfig.get().setBasicDigestAuthentications(bda);
        httpRequest.setAuthentication("keyname1");

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.SUCCESS, build);
    }

    @Test
    public void canDoFormAuthentication() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare the authentication
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new NameValuePair("param1","value1"));
        params.add(new NameValuePair("param2","value2"));

        RequestAction action = new RequestAction(new URL(baseURL+"/reqAction"),HttpMode.GET,params);
        List<RequestAction> actions = new ArrayList<RequestAction>();
        actions.add(action);

        FormAuthentication formAuth = new FormAuthentication("keyname",actions);
        List<FormAuthentication> formAuthList = new ArrayList<FormAuthentication>();
        formAuthList.add(formAuth);

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/formAuth");
        httpRequest.setHttpMode(HttpMode.GET);
        HttpRequestGlobalConfig.get().setFormAuthentications(formAuthList);
        httpRequest.setAuthentication("keyname");

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.SUCCESS, build);
    }

    @Test
    public void rejectedFormCredentialsFailTheBuild() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare the authentication
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new NameValuePair("param1","value1"));
        params.add(new NameValuePair("param2","value2"));

        RequestAction action = new RequestAction(new URL(baseURL+"/formAuthBad"),HttpMode.GET,params);
        List<RequestAction> actions = new ArrayList<RequestAction>();
        actions.add(action);

        FormAuthentication formAuth = new FormAuthentication("keyname",actions);
        List<FormAuthentication> formAuthList = new ArrayList<FormAuthentication>();
        formAuthList.add(formAuth);

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/formAuthBad");
        httpRequest.setHttpMode(HttpMode.GET);
        httpRequest.setConsoleLogResponseBody(true);
        HttpRequestGlobalConfig.get().setFormAuthentications(formAuthList);
        httpRequest.setAuthentication("keyname");

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.FAILURE, build);
        j.assertLogContains("Error doing authentication",build);
    }

    @Test
    public void invalidKeyFormAuthenticationFailsTheBuild() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare the authentication
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new NameValuePair("param1","value1"));
        params.add(new NameValuePair("param2","value2"));

        // The request action won't be sent but we need to prepare it
        RequestAction action = new RequestAction(new URL(baseURL+"/non-existent"),HttpMode.GET,params);
        List<RequestAction> actions = new ArrayList<RequestAction>();
        actions.add(action);

        FormAuthentication formAuth = new FormAuthentication("keyname",actions);
        List<FormAuthentication> formAuthList = new ArrayList<FormAuthentication>();
        formAuthList.add(formAuth);

        // Prepare HttpRequest - the actual request won't be sent
        HttpRequest httpRequest = new HttpRequest(baseURL+"/non-existent");
        httpRequest.setHttpMode(HttpMode.GET);
        httpRequest.setConsoleLogResponseBody(true);
        HttpRequestGlobalConfig.get().setFormAuthentications(formAuthList);

        // Select a non-existent form authentication, this will error the build before any request is made
        httpRequest.setAuthentication("non-existent");

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.FAILURE, build);
        j.assertLogContains("Authentication 'non-existent' doesn't exist anymore",build);
    }
}
