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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Fields;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

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
public class HttpRequestTest extends HttpRequestTestBase {

	@Rule
    public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void simpleGetTest() throws Exception {
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
	public void quietTest() throws Exception {
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
	public void canDetectActualContent() throws Exception {
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
	public void badContentFailsTheBuild() throws Exception {
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
	public void responseMatchAcceptedMimeType() throws Exception {
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
	public void responseDoesNotMatchAcceptedMimeTypeDoesNotFailTheBuild() throws Exception {
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
	public void passBuildParametersWhenAskedAndParametersArePresent() throws Exception {
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
	public void replaceParametersInRequestBody() throws Exception {

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
	public void silentlyIgnoreNonExistentBuildParameters() throws Exception {
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
	public void doNotPassBuildParametersWithBuildParameters() throws Exception {
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
	public void passRequestBodyWhenRequestIsPostAndBodyIsPresent() throws Exception {
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
	public void doNotPassRequestBodyWhenMethodIsGet() throws Exception {
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
	public void doAllRequestTypes() throws Exception {
		for (HttpMode method : HttpMode.values()) {
			// Prepare the server
			registerRequestChecker(method);
			doRequest(method);

			cleanHandlers();
		}
	}

	public void doRequest(final HttpMode method) throws Exception {
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
	public void invalidResponseCodeFailsTheBuild() throws Exception {
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
	public void invalidResponseCodeIsAccepted() throws Exception {
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
	public void reverseRangeFailsTheBuild() throws Exception {
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
	public void notANumberRangeValueFailsTheBuild() throws Exception {
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
	public void rangeWithTextFailsTheBuild() throws Exception {
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
	public void invalidRangeFailsTheBuild() throws Exception {
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
	public void sendAllContentTypes() {
		for (MimeType mimeType : MimeType.values()) {
			sendContentType(mimeType);
		}
	}

	public void sendContentType(final MimeType mimeType) {
		registerContentTypeRequestChecker(mimeType, HttpMode.GET, ALL_IS_WELL);
	}

	public void sendContentType(final MimeType mimeType, String checkMessage, String body) throws Exception {
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
    public void sendNonAsciiRequestBody() throws Exception {
		registerContentTypeRequestChecker(MimeType.APPLICATION_JSON, HttpMode.POST, null);
        sendContentType(MimeType.APPLICATION_JSON, ALL_IS_WELL, ALL_IS_WELL);
    }

    @Test
    public void sendUTF8RequestBody() throws Exception {
        Assume.assumeFalse("TODO does not currently work on Windows", Functions.isWindows());
        String notAsciiUTF8Message = "ἱερογλύφος";
		registerContentTypeRequestChecker(MimeType.APPLICATION_JSON_UTF8, HttpMode.POST, null);
        sendContentType(MimeType.APPLICATION_JSON_UTF8, notAsciiUTF8Message, notAsciiUTF8Message);
    }

	@Test
	public void sendAllAcceptTypes() throws Exception {
		for (MimeType mimeType : MimeType.values()) {
			// Prepare the server
			registerAcceptedTypeRequestChecker(mimeType);
			sendAcceptType(mimeType);

			cleanHandlers();
		}
	}

	public void sendAcceptType(final MimeType mimeType) throws Exception {
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
	public void canPutResponseInOutputFile() throws Exception {
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
	public void canPutResponseInOutputFileWhenNotSetToGoToConsole() throws Exception {
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
	public void timeoutFailsTheBuild() throws Exception {
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
	public void canDoCustomHeaders() throws Exception {
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
	public void replaceParametersInCustomHeaders() throws Exception {
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
	public void nonExistentBasicAuthFailsTheBuild() throws Exception {
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
	public void canDoBasicDigestAuthentication() throws Exception {
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
	public void testFormAuthentication() throws Exception {
		final String paramUsername = "username";
		final String valueUsername = "user";
		final String paramPassword = "password";
		final String valuePassword = "pass";
		final String sessionName = "VALID_SESSIONID";

		registerHandler("/form-auth", HttpMode.POST, new SimpleHandler() {
			@Override
			boolean doHandle(Request request, Response response, Callback callback) throws Exception {
				Fields parameters = Request.getParameters(request);
				String username = parameters.getValue(paramUsername);
				String password = parameters.getValue(paramPassword);

				if (!username.equals(valueUsername) || !password.equals(valuePassword)) {
					response.setStatus(401);
					return false;
				}
				HttpCookie cookie = HttpCookie.build(sessionName, "ok").build();
				Response.addCookie(response, cookie);
				okAllIsWell(response, callback);
				return true;
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
					response.setStatus(401);
					return false;
				}
				okAllIsWell(response, callback);
				return true;
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
	public void canDoFormAuthentication() throws Exception {
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
	public void rejectedFormCredentialsFailTheBuild() throws Exception {
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
	public void invalidKeyFormAuthenticationFailsTheBuild() throws Exception {
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
	public void responseContentSupplierHeadersFilling() throws Exception {
		// Prepare test context
		HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
		response.setEntity(new StringEntity("TEST"));
		response.setHeader("Server", "Jenkins");
		response.setHeader("Set-Cookie", "JSESSIONID=123456789");
		response.addHeader("Set-Cookie", "JSESSIONID=abcdefghijk");
		// Run test
		ResponseContentSupplier respSupplier = new ResponseContentSupplier(ResponseHandle.STRING, response);
		// Check expectations
		Assert.assertEquals(2, respSupplier.getHeaders().size());
		Assert.assertTrue(respSupplier.getHeaders().containsKey("Server"));
		Assert.assertTrue(respSupplier.getHeaders().containsKey("Set-Cookie"));
		Assert.assertEquals(1, respSupplier.getHeaders().get("Server").size());
		Assert.assertEquals(2, respSupplier.getHeaders().get("Set-Cookie").size());
		Assert.assertEquals("Jenkins", respSupplier.getHeaders().get("Server").get(0));
		int valuesFoundCounter = 0;
		for (String s : respSupplier.getHeaders().get("Set-Cookie")) {
			if ("JSESSIONID=123456789".equals(s)) {
				valuesFoundCounter++;
			} else if ("JSESSIONID=abcdefghijk".equals(s)) {
				valuesFoundCounter++;
			}
		}
		Assert.assertEquals(2, valuesFoundCounter);
		respSupplier.close();
	}

	@Test
	public void responseContentSupplierHeadersCaseInsensitivity() throws Exception {
		// Prepare test context
		HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
		response.setEntity(new StringEntity("TEST"));
		response.setHeader("Server", "Jenkins");
		// Run test
		ResponseContentSupplier respSupplier = new ResponseContentSupplier(ResponseHandle.STRING, response);
		// Check expectations
		Assert.assertEquals(1, respSupplier.getHeaders().size());
		Assert.assertTrue(respSupplier.getHeaders().containsKey("Server"));
		Assert.assertTrue(respSupplier.getHeaders().containsKey("SERVER"));
		Assert.assertTrue(respSupplier.getHeaders().containsKey("server"));
		respSupplier.close();
	}

	@Test
	public void testFileUpload() throws Exception {
		// Prepare the server
		final File testFolder = folder.newFolder();
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
	public void testUnwrappedPutFileUpload() throws Exception {
		// Prepare the server
		final File testFolder = folder.newFolder();
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
	public void nonExistentProxyAuthFailsTheBuild() throws Exception {
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

}
