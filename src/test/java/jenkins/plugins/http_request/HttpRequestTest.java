package jenkins.plugins.http_request;

import static jenkins.plugins.http_request.Registers.registerAcceptedTypeRequestChecker;
import static jenkins.plugins.http_request.Registers.registerBasicAuth;
import static jenkins.plugins.http_request.Registers.registerCheckBuildParameters;
import static jenkins.plugins.http_request.Registers.registerCheckRequestBody;
import static jenkins.plugins.http_request.Registers.registerCheckRequestBodyWithTag;
import static jenkins.plugins.http_request.Registers.registerContentTypeRequestChecker;
import static jenkins.plugins.http_request.Registers.registerCustomHeaders;
import static jenkins.plugins.http_request.Registers.registerCustomHeadersResolved;
import static jenkins.plugins.http_request.Registers.registerFileUpload;
import static jenkins.plugins.http_request.Registers.registerFormAuth;
import static jenkins.plugins.http_request.Registers.registerFormAuthBad;
import static jenkins.plugins.http_request.Registers.registerInvalidStatusCode;
import static jenkins.plugins.http_request.Registers.registerReqAction;
import static jenkins.plugins.http_request.Registers.registerRequestChecker;
import static jenkins.plugins.http_request.Registers.registerTimeout;
import static jenkins.plugins.http_request.Registers.registerUnwrappedPutFileUpload;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.ServletException;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpResponseAdapter;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Fields;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import hudson.Functions;
import hudson.model.Cause.UserIdCause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;

import jenkins.plugins.http_request.auth.FormAuthentication;
import jenkins.plugins.http_request.util.HttpRequestNameValuePair;
import jenkins.plugins.http_request.util.RequestAction;

/**
 * @author Martin d'Anjou
 */
@WithJenkins
class HttpRequestTest extends HttpRequestTestBase {

    @TempDir
    private File folder;

    @Test
    void simpleGetTest() throws Exception {
        // Prepare the server
        registerRequestChecker(HttpMode.GET);

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/doGET");
        httpRequest.setConsoleLogResponseBody(true);

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogContains(ALL_IS_WELL, build);
        this.j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", build);
    }

    @Test
    void quietTest() throws Exception {
        // Prepare the server
        registerRequestChecker(HttpMode.GET);

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/doGET");
        httpRequest.setQuiet(true);

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogNotContains("HttpMethod:", build);
        this.j.assertLogNotContains("URL:", build);
        this.j.assertLogNotContains("Sending request to url:", build);
        this.j.assertLogNotContains("Response Code:", build);
    }

    @Test
    void canDetectActualContent() throws Exception {
        // Setup the expected pattern
        String findMe = ALL_IS_WELL;

        // Prepare the server
        registerRequestChecker(HttpMode.GET);

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/doGET");
        httpRequest.setConsoleLogResponseBody(true);
        httpRequest.setValidResponseContent(findMe);

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogContains(findMe, build);
        this.j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", build);
    }

    @Test
    void badContentFailsTheBuild() throws Exception {
        // Prepare the server
        registerRequestChecker(HttpMode.GET);

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/doGET");
        httpRequest.setConsoleLogResponseBody(true);
        httpRequest.setValidResponseContent("bad content");

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatus(Result.FAILURE, build);
        this.j.assertLogContains("Fail: Response doesn't contain expected content 'bad content'", build);
        this.j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", build);
    }

    @Test
    void responseMatchAcceptedMimeType() throws Exception {
        // Prepare the server
        registerRequestChecker(HttpMode.GET);

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/doGET");
        httpRequest.setConsoleLogResponseBody(true);

        // Expect a mime type that matches the response
        httpRequest.setAcceptType(MimeType.TEXT_PLAIN);

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogContains(ALL_IS_WELL, build);
        this.j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", build);
    }

    @Test
    void responseDoesNotMatchAcceptedMimeTypeDoesNotFailTheBuild() throws Exception {
        // Prepare the server
        registerRequestChecker(HttpMode.GET);

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/doGET");
        httpRequest.setConsoleLogResponseBody(true);

        // Expect a mime type that does not match the response
        httpRequest.setAcceptType(MimeType.TEXT_HTML);

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogContains(ALL_IS_WELL, build);
        this.j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", build);
    }

    @Test
    void passBuildParametersWhenAskedAndParametersArePresent() throws Exception {
        // Prepare the server
        registerCheckBuildParameters();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/checkBuildParameters");
        httpRequest.setConsoleLogResponseBody(true);

        // Activate passBuildParameters
        httpRequest.setPassBuildParameters(true);

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("foo", "default")
        ));
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0, new UserIdCause(), new ParametersAction(new StringParameterValue("foo", "value"))).get();

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogContains(ALL_IS_WELL, build);
        this.j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", build);
    }

    @Test
    void replaceParametersInRequestBody() throws Exception {

        // Prepare the server
        registerCheckRequestBodyWithTag();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/checkRequestBodyWithTag");
        httpRequest.setConsoleLogResponseBody(true);

        // Activate requsetBody
        httpRequest.setHttpMode(HttpMode.POST);

        // Use some random body content that contains a parameter
        httpRequest.setRequestBody("cleanupDir=D:/continuousIntegration/deployments/Daimler/${Tag}/standalone");

        // Build parameters have to be passed
        httpRequest.setPassBuildParameters(true);

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("Tag", "default")
        ));
        project.getBuildersList().add(httpRequest);

        FreeStyleBuild build = project.scheduleBuild2(0, new UserIdCause(), new ParametersAction(new StringParameterValue("Tag", "trunk"))).get();

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogContains(ALL_IS_WELL, build);
        this.j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", build);
    }

    @Test
    void silentlyIgnoreNonExistentBuildParameters() throws Exception {
        // Prepare the server
        registerRequestChecker(HttpMode.GET);

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/doGET");
        httpRequest.setConsoleLogResponseBody(true);

        // Activate passBuildParameters without parameters present
        httpRequest.setPassBuildParameters(true);

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogContains(ALL_IS_WELL, build);
        this.j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", build);
    }

    @Test
    void doNotPassBuildParametersWithBuildParameters() throws Exception {
        // Prepare the server
        registerRequestChecker(HttpMode.GET);

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/doGET");
        httpRequest.setConsoleLogResponseBody(true);

        // Activate passBuildParameters
        httpRequest.setPassBuildParameters(false);

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0, new UserIdCause(), new ParametersAction(new StringParameterValue("foo", "value"))).get();

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogContains(ALL_IS_WELL, build);
        this.j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", build);
    }

    @Test
    void passRequestBodyWhenRequestIsPostAndBodyIsPresent() throws Exception {
        // Prepare the server
        registerCheckRequestBody();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/checkRequestBody");
        httpRequest.setConsoleLogResponseBody(true);

        // Activate requsetBody
        httpRequest.setHttpMode(HttpMode.POST);
        httpRequest.setRequestBody("TestRequestBody");

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogContains(ALL_IS_WELL, build);
        this.j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", build);
    }

    @Test
    void doNotPassRequestBodyWhenMethodIsGet() throws Exception {
        // Prepare the server
        registerRequestChecker(HttpMode.GET);

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/doGET");
        httpRequest.setConsoleLogResponseBody(true);

        // Activate passBuildParameters
        httpRequest.setRequestBody("TestRequestBody");

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogContains(ALL_IS_WELL, build);
        this.j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", build);
    }

    @Test
    void doAllRequestTypes() throws Exception {
        for (HttpMode method : HttpMode.values()) {
            // Prepare the server
            registerRequestChecker(method);
            doRequest(method);

            cleanHandlers();
        }
    }

    private void doRequest(final HttpMode method) throws Exception {
        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/do" + method.toString());
        httpRequest.setHttpMode(method);
        httpRequest.setConsoleLogResponseBody(true);

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatusSuccess(build);

        if (method == HttpMode.HEAD) {
            return;
        }

        this.j.assertLogContains(ALL_IS_WELL, build);
        this.j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", build);
    }

    @Test
    void invalidResponseCodeFailsTheBuild() throws Exception {
        // Prepare the server
        registerInvalidStatusCode();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/invalidStatusCode");
        httpRequest.setConsoleLogResponseBody(true);

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatus(Result.FAILURE, build);
        this.j.assertLogContains("Throwing status 400 for test", build);
        this.j.assertLogContains("Fail: Status code 400 is not in the accepted range: 100:399", build);
    }

    @Test
    void invalidResponseCodeIsAccepted() throws Exception {
        // Prepare the server
        registerInvalidStatusCode();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/invalidStatusCode");
        httpRequest.setValidResponseCodes("100:599");
        httpRequest.setConsoleLogResponseBody(true);

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogContains("Throwing status 400 for test", build);
        this.j.assertLogContains("Success: Status code 400 is in the accepted range: 100:599", build);
    }

    @Test
    void reverseRangeFailsTheBuild() throws Exception {
        // Prepare the server


        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/doesNotMatter");
        httpRequest.setValidResponseCodes("599:100");
        httpRequest.setConsoleLogResponseBody(true);

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatus(Result.FAILURE, build);
        this.j.assertLogContains("Interval 599:100 should be FROM less than TO", build);
    }

    @Test
    void notANumberRangeValueFailsTheBuild() throws Exception {
        // Prepare the server


        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/doesNotMatter");
        httpRequest.setValidResponseCodes("text");
        httpRequest.setConsoleLogResponseBody(true);

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatus(Result.FAILURE, build);
        this.j.assertLogContains("Invalid number text", build);
    }

    @Test
    void rangeWithTextFailsTheBuild() throws Exception {
        // Prepare the server


        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/doesNotMatter");
        httpRequest.setValidResponseCodes("1:text");
        httpRequest.setConsoleLogResponseBody(true);

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatus(Result.FAILURE, build);
        this.j.assertLogContains("Invalid number text", build);
    }

    @Test
    void invalidRangeFailsTheBuild() throws Exception {
        // Prepare the server


        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/doesNotMatter");
        httpRequest.setValidResponseCodes("1:2:3");
        httpRequest.setConsoleLogResponseBody(true);

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatus(Result.FAILURE, build);
        this.j.assertLogContains("Code 1:2:3 should be an interval from:to or a single value", build);
    }

    @Test
    void sendAllContentTypes() {
        for (MimeType mimeType : MimeType.values()) {
            sendContentType(mimeType);
        }
    }

    private void sendContentType(final MimeType mimeType) {
        registerContentTypeRequestChecker(mimeType, HttpMode.GET, ALL_IS_WELL);
    }

	private void sendContentType(final MimeType mimeType, String checkMessage, String body) throws Exception {
        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/incoming_" + mimeType.toString());
        httpRequest.setConsoleLogResponseBody(true);
        httpRequest.setContentType(mimeType);
        if (body != null) {
            httpRequest.setHttpMode(HttpMode.POST);
            httpRequest.setRequestBody(body);
        }

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogContains(checkMessage, build);
        this.j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", build);
    }

    @Test
    void sendNonAsciiRequestBody() throws Exception {
        registerContentTypeRequestChecker(MimeType.APPLICATION_JSON, HttpMode.POST, null);
        sendContentType(MimeType.APPLICATION_JSON, ALL_IS_WELL, ALL_IS_WELL);
    }

    @Test
    void sendUTF8RequestBody() throws Exception {
        Assumptions.assumeFalse(Functions.isWindows(), "TODO does not currently work on Windows");
        String notAsciiUTF8Message = "ἱερογλύφος";
        registerContentTypeRequestChecker(MimeType.APPLICATION_JSON_UTF8, HttpMode.POST, null);
        sendContentType(MimeType.APPLICATION_JSON_UTF8, notAsciiUTF8Message, notAsciiUTF8Message);
    }

    @Test
    void sendAllAcceptTypes() throws Exception {
        for (MimeType mimeType : MimeType.values()) {
            // Prepare the server
            registerAcceptedTypeRequestChecker(mimeType);
            sendAcceptType(mimeType);

            cleanHandlers();
        }
    }

	private void sendAcceptType(final MimeType mimeType) throws Exception {
        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/accept_" + mimeType.toString());
        httpRequest.setConsoleLogResponseBody(true);
        httpRequest.setAcceptType(mimeType);

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogContains(ALL_IS_WELL, build);
        this.j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", build);
    }

    @Test
    void canPutResponseInOutputFile() throws Exception {
        // Prepare the server
        registerRequestChecker(HttpMode.GET);

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/doGET");
        httpRequest.setOutputFile("file.txt");
        httpRequest.setConsoleLogResponseBody(true);

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatusSuccess(build);

        // By default, the response is printed to the console even if an outputFile is used
        this.j.assertLogContains(ALL_IS_WELL, build);

        // The response is in the output file as well
        String outputFile = build.getWorkspace().child("file.txt").readToString();
        Pattern p = Pattern.compile(ALL_IS_WELL);
        Matcher m = p.matcher(outputFile);
        assertTrue(m.find());
        this.j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", build);
    }

    @Test
    void canPutResponseInOutputFileWhenNotSetToGoToConsole() throws Exception {
        // Prepare the server
        registerRequestChecker(HttpMode.GET);

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/doGET");
        httpRequest.setOutputFile("file.txt");

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatusSuccess(build);

        // Check that the console does NOT have the response body
        this.j.assertLogNotContains(ALL_IS_WELL, build);

        // The response is in the output file
        String outputFile = build.getWorkspace().child("file.txt").readToString();
        Pattern p = Pattern.compile(ALL_IS_WELL);
        Matcher m = p.matcher(outputFile);
        assertTrue(m.find());
        this.j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", build);
    }

    @Test
    void timeoutFailsTheBuild() throws Exception {
        // Prepare the server
        registerTimeout();


        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/timeout");
        httpRequest.setTimeout(2);

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatus(Result.FAILURE, build);
        this.j.assertLogContains("Fail: Status code 408 is not in the accepted range: 100:399", build);
    }

    @Test
    void canDoCustomHeaders() throws Exception {
        // Prepare the server
        registerCustomHeaders();

        List<HttpRequestNameValuePair> customHeaders = new ArrayList<>();
        customHeaders.add(new HttpRequestNameValuePair("customHeader", "value1"));
        customHeaders.add(new HttpRequestNameValuePair("customHeader", "value2"));
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/customHeaders");
        httpRequest.setCustomHeaders(customHeaders);

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", build);
    }

    @Test
    void replaceParametersInCustomHeaders() throws Exception {
        // Prepare the server
        registerCustomHeadersResolved();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/customHeadersResolved");
        httpRequest.setConsoleLogResponseBody(true);

        // Activate requsetBody
        httpRequest.setHttpMode(HttpMode.POST);

        // Add some custom headers
        List<HttpRequestNameValuePair> customHeaders = new ArrayList<>();
        customHeaders.add(new HttpRequestNameValuePair("resolveCustomParam", "${Tag}"));
        customHeaders.add(new HttpRequestNameValuePair("resolveEnvParam", "${WORKSPACE}"));
        httpRequest.setCustomHeaders(customHeaders);

        // Activate passBuildParameters
        httpRequest.setPassBuildParameters(true);

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("Tag", "default"),
                new StringParameterDefinition("WORKSPACE", "default")
        ));
        project.getBuildersList().add(httpRequest);

        FreeStyleBuild build = project.scheduleBuild2(0, new UserIdCause(),
                new ParametersAction(new StringParameterValue("Tag", "trunk"), new StringParameterValue("WORKSPACE", "C:/path/to/my/workspace"))).get();

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogContains(ALL_IS_WELL, build);
        this.j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", build);
    }

    @Test
    void nonExistentBasicAuthFailsTheBuild() throws Exception {
        // Prepare the server
        registerBasicAuth();


        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/basicAuth");
        httpRequest.setAuthentication("non-existent-key");

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatus(Result.FAILURE, build);
        this.j.assertLogContains("Authentication 'non-existent-key' doesn't exist anymore", build);
    }

    @Test
    void canDoBasicDigestAuthentication() throws Exception {
        // Prepare the server
        registerBasicAuth();

        // Prepare the authentication
        registerBasicCredential("keyname1", "username1", "password1");
        registerBasicCredential("keyname2", "username2", "password2");

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/basicAuth");
        httpRequest.setAuthentication("keyname1");

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", build);
    }

    @Test
    void testFormAuthentication() throws Exception {
        final String paramUsername = "username";
        final String valueUsername = "user";
        final String paramPassword = "password";
        final String valuePassword = "pass";
        final String sessionName = "VALID_SESSIONID";

        registerHandler("/form-auth", HttpMode.POST, new SimpleHandler() {
            @Override
            boolean doHandle(Request request, Response response, Callback callback) throws ServletException {
                Fields parameters;
                try {
                    parameters = Request.getParameters(request);
                } catch (Exception e) {
                    throw new ServletException(e);
                }
                String username = parameters.getValue(paramUsername);
                String password = parameters.getValue(paramPassword);

                if (!username.equals(valueUsername) || !password.equals(valuePassword)) {
                    Response.writeError(request, response, callback, HttpStatus.UNAUTHORIZED_401);
                    return true;
                }
                HttpCookie cookie = HttpCookie.build(sessionName, "ok").build();
                Response.addCookie(response, cookie);
                return okAllIsWell(response, callback);
            }
        });
        registerHandler("/test-auth", HttpMode.GET, new SimpleHandler() {
            @Override
            boolean doHandle(Request request, Response response, Callback callback) {
                String jsessionValue = "";
                List<HttpCookie> cookies = Request.getCookies(request);
                for (HttpCookie cookie : cookies) {
                    if (cookie.getName().equals(sessionName)) {
                        jsessionValue = cookie.getValue();
                        break;
                    }
                }

                if (!jsessionValue.equals("ok")) {
                    Response.writeError(request, response, callback, HttpStatus.UNAUTHORIZED_401);
                    return true;
                }
                return okAllIsWell(response, callback);
            }
        });


        // Prepare the authentication
        List<HttpRequestNameValuePair> params = new ArrayList<>();
        params.add(new HttpRequestNameValuePair(paramUsername, valueUsername));
        params.add(new HttpRequestNameValuePair(paramPassword, valuePassword));

        RequestAction action = new RequestAction(new URL(baseURL() + "/form-auth"), HttpMode.POST, null, params);
        List<RequestAction> actions = new ArrayList<>();
        actions.add(action);

        FormAuthentication formAuth = new FormAuthentication("Form", actions);
        List<FormAuthentication> formAuthList = new ArrayList<>();
        formAuthList.add(formAuth);

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/test-auth");
        HttpRequestGlobalConfig.get().setFormAuthentications(formAuthList);
        httpRequest.setAuthentication("Form");

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", build);
    }

    @Test
    void canDoFormAuthentication() throws Exception {
        // Prepare the server
        registerFormAuth();
        registerReqAction();

        // Prepare the authentication
        List<HttpRequestNameValuePair> params = new ArrayList<>();
        params.add(new HttpRequestNameValuePair("param1", "value1"));
        params.add(new HttpRequestNameValuePair("param2", "value2"));

        RequestAction action = new RequestAction(new URL(baseURL() + "/reqAction"), HttpMode.GET, null, params);
        List<RequestAction> actions = new ArrayList<>();
        actions.add(action);

        FormAuthentication formAuth = new FormAuthentication("keyname", actions);
        List<FormAuthentication> formAuthList = new ArrayList<>();
        formAuthList.add(formAuth);

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/formAuth");
        HttpRequestGlobalConfig.get().setFormAuthentications(formAuthList);
        httpRequest.setAuthentication("keyname");

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", build);
    }

    @Test
    void rejectedFormCredentialsFailTheBuild() throws Exception {
        // Prepare the server
        registerFormAuthBad();

        // Prepare the authentication
        List<HttpRequestNameValuePair> params = new ArrayList<>();
        params.add(new HttpRequestNameValuePair("param1", "value1"));
        params.add(new HttpRequestNameValuePair("param2", "value2"));

        RequestAction action = new RequestAction(new URL(baseURL() + "/formAuthBad"), HttpMode.GET, null, params);
        List<RequestAction> actions = new ArrayList<>();
        actions.add(action);

        FormAuthentication formAuth = new FormAuthentication("keyname", actions);
        List<FormAuthentication> formAuthList = new ArrayList<>();
        formAuthList.add(formAuth);

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/formAuthBad");
        httpRequest.setConsoleLogResponseBody(true);
        HttpRequestGlobalConfig.get().setFormAuthentications(formAuthList);
        httpRequest.setAuthentication("keyname");

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatus(Result.FAILURE, build);
        this.j.assertLogContains("Error doing authentication", build);
    }

    @Test
    void invalidKeyFormAuthenticationFailsTheBuild() throws Exception {
        // Prepare the server


        // Prepare the authentication
        List<HttpRequestNameValuePair> params = new ArrayList<>();
        params.add(new HttpRequestNameValuePair("param1", "value1"));
        params.add(new HttpRequestNameValuePair("param2", "value2"));

        // The request action won't be sent but we need to prepare it
        RequestAction action = new RequestAction(new URL(baseURL() + "/non-existent"), HttpMode.GET, null, params);
        List<RequestAction> actions = new ArrayList<>();
        actions.add(action);

        FormAuthentication formAuth = new FormAuthentication("keyname", actions);
        List<FormAuthentication> formAuthList = new ArrayList<>();
        formAuthList.add(formAuth);

        // Prepare HttpRequest - the actual request won't be sent
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/non-existent");
        httpRequest.setConsoleLogResponseBody(true);
        HttpRequestGlobalConfig.get().setFormAuthentications(formAuthList);

        // Select a non-existent form authentication, this will error the build before any request is made
        httpRequest.setAuthentication("non-existent");

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatus(Result.FAILURE, build);
        this.j.assertLogContains("Authentication 'non-existent' doesn't exist anymore", build);
    }

    @Test
    void responseContentSupplierHeadersFilling() throws Exception {
        // Prepare test context
		CloseableHttpResponse response = HttpResponseAdapter.adapt(new BasicClassicHttpResponse(200, "OK"));
		response.setEntity(new StringEntity("TEST"));
        response.setHeader("Server", "Jenkins");
        response.setHeader("Set-Cookie", "JSESSIONID=123456789");
        response.addHeader("Set-Cookie", "JSESSIONID=abcdefghijk");
        // Run test
        ResponseContentSupplier respSupplier = new ResponseContentSupplier(ResponseHandle.STRING, response);
        // Check expectations
        assertEquals(2, respSupplier.getHeaders().size());
        assertTrue(respSupplier.getHeaders().containsKey("Server"));
        assertTrue(respSupplier.getHeaders().containsKey("Set-Cookie"));
        assertEquals(1, respSupplier.getHeaders().get("Server").size());
        assertEquals(2, respSupplier.getHeaders().get("Set-Cookie").size());
        assertEquals("Jenkins", respSupplier.getHeaders().get("Server").get(0));
        int valuesFoundCounter = 0;
        for (String s : respSupplier.getHeaders().get("Set-Cookie")) {
            if ("JSESSIONID=123456789".equals(s)) {
                valuesFoundCounter++;
            } else if ("JSESSIONID=abcdefghijk".equals(s)) {
                valuesFoundCounter++;
            }
        }
        assertEquals(2, valuesFoundCounter);
        respSupplier.close();
    }

    @Test
    void responseContentSupplierHeadersCaseInsensitivity() throws Exception {
        // Prepare test context
		CloseableHttpResponse response = HttpResponseAdapter.adapt(new BasicClassicHttpResponse(200, "OK"));
		response.setEntity(new StringEntity("TEST"));
        response.setHeader("Server", "Jenkins");
        // Run test
        ResponseContentSupplier respSupplier = new ResponseContentSupplier(ResponseHandle.STRING, response);
        // Check expectations
        assertEquals(1, respSupplier.getHeaders().size());
        assertTrue(respSupplier.getHeaders().containsKey("Server"));
        assertTrue(respSupplier.getHeaders().containsKey("SERVER"));
        assertTrue(respSupplier.getHeaders().containsKey("server"));
        respSupplier.close();
    }

    @Test
    void responseContentSupplierHandlesNoContentTypeHeader() throws Exception {
        // Prepare test context - 204 No Content response without Content-Type header
        CloseableHttpResponse response = HttpResponseAdapter.adapt(new BasicClassicHttpResponse(204, "No Content"));
        // Don't set entity or Content-Type header to simulate GitHub API 204 response
        // Run test
        ResponseContentSupplier respSupplier = new ResponseContentSupplier(ResponseHandle.NONE, response);
        // Check expectations - should not throw NullPointerException
        assertEquals(204, respSupplier.getStatus());
        respSupplier.close();
    }

    @Test
    void testFileUpload() throws Exception {
        // Prepare the server
        final File testFolder = newFolder(folder, "junit");
        File uploadFile = File.createTempFile("upload", ".zip", testFolder);
        String responseText = "File upload successful!";
        registerFileUpload(uploadFile, responseText);

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/uploadFile");
        httpRequest.setHttpMode(HttpMode.POST);
        httpRequest.setValidResponseCodes("201");
        httpRequest.setConsoleLogResponseBody(true);
        httpRequest.setUploadFile(uploadFile.getAbsolutePath());
        httpRequest.setMultipartName("file-name");
        httpRequest.setContentType(MimeType.APPLICATION_ZIP);
        httpRequest.setAcceptType(MimeType.TEXT_PLAIN);

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogContains(responseText, build);
        this.j.assertLogContains("Success: Status code 201 is in the accepted range: 201", build);
    }

    @Test
    void testUnwrappedPutFileUpload() throws Exception {
        // Prepare the server
        final File testFolder = newFolder(folder, "junit");
        File uploadFile = File.createTempFile("upload", ".zip", testFolder);
        String responseText = "File upload successful!";
        registerUnwrappedPutFileUpload(uploadFile, responseText);

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/uploadFile/" + uploadFile.getName());
        httpRequest.setHttpMode(HttpMode.PUT);
        httpRequest.setValidResponseCodes("201");
        httpRequest.setConsoleLogResponseBody(true);
        httpRequest.setUploadFile(uploadFile.getAbsolutePath());
        httpRequest.setWrapAsMultipart(false);
        httpRequest.setContentType(MimeType.APPLICATION_ZIP);
        httpRequest.setAcceptType(MimeType.TEXT_PLAIN);

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogContains(responseText, build);
        this.j.assertLogContains("Success: Status code 201 is in the accepted range: 201", build);
    }

    @Test
    void nonExistentProxyAuthFailsTheBuild() throws Exception {
        // Prepare the server
        registerBasicAuth();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL() + "/basicAuth");
        httpRequest.setHttpProxy("http://proxy.example.com:8888");
        httpRequest.setProxyAuthentication("non-existent-key");

        // Run build
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        this.j.assertBuildStatus(Result.FAILURE, build);
        this.j.assertLogContains("Proxy authentication 'non-existent-key' doesn't exist anymore or is not a username/password credential type", build);
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }

	private static class RedirectStatus {
		boolean called = false;
		boolean redirected = false;
	};

	@Test
	void noRedirectOnPOSTOn302() throws Exception {
		final RedirectStatus redirectStatus = new RedirectStatus();

		registerHandler("/redirected", HttpMode.GET, new SimpleHandler() {
			@Override
			boolean doHandle(Request request, Response response, Callback callback) {
				redirectStatus.called = true;
				return okAllIsWell(response, callback);
			}
		});
		registerHandler("/redirectPOST", HttpMode.POST, new SimpleHandler() {
			@Override
			boolean doHandle(Request request, Response response, Callback callback) {
				redirectStatus.redirected = true;
				Response.sendRedirect(request, response, callback, 302, "/redirected", false);
				return true;
			}
		});
		String body = "send-body-workflow";
		WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "postBody");
		proj.setDefinition(new CpsFlowDefinition(
				"def response = httpRequest" +
						" httpMode: 'POST'," +
						" requestBody: '" + body + "'," +
						" url: '" + baseURL() + "/redirectPOST'\n" +
						"println('Response: ' + response.content)\n",
				true));

		WorkflowRun run = proj.scheduleBuild2(0).get();

		// Check expectations
		j.assertBuildStatusSuccess(run);
		assertTrue(redirectStatus.redirected);
		assertFalse(redirectStatus.called);
	}

	@Test
	void redirectOnGETOn302() throws Exception {
		final RedirectStatus redirectStatus = new RedirectStatus();

		registerHandler("/redirected", HttpMode.GET, new SimpleHandler() {
			@Override
			boolean doHandle(Request request, Response response, Callback callback) {
				redirectStatus.called = true;
				return okAllIsWell(response, callback);
			}
		});
		registerHandler("/redirectGET", HttpMode.GET, new SimpleHandler() {
			@Override
			boolean doHandle(Request request, Response response, Callback callback) {
				redirectStatus.redirected = true;
				Response.sendRedirect(request, response, callback, 302, "/redirected", false);
				return true;
			}
		});
		String body = "send-body-workflow";
		WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "postBody");
		proj.setDefinition(new CpsFlowDefinition(
				"def response = httpRequest" +
						" httpMode: 'GET'," +
						" requestBody: '" + body + "'," +
						" url: '" + baseURL() + "/redirectGET'\n" +
						"println('Response: ' + response.content)\n",
				true));

		WorkflowRun run = proj.scheduleBuild2(0).get();

		// Check expectations
		j.assertBuildStatusSuccess(run);
		assertTrue(redirectStatus.redirected);
		assertTrue(redirectStatus.called);
	}
}
