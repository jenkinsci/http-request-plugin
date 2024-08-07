package jenkins.plugins.http_request;

import static jenkins.plugins.http_request.Registers.registerAcceptedTypeRequestChecker;
import static jenkins.plugins.http_request.Registers.registerBasicAuth;
import static jenkins.plugins.http_request.Registers.registerContentTypeRequestChecker;
import static jenkins.plugins.http_request.Registers.registerCustomHeaders;
import static jenkins.plugins.http_request.Registers.registerFileUpload;
import static jenkins.plugins.http_request.Registers.registerFormAuth;
import static jenkins.plugins.http_request.Registers.registerFormAuthBad;
import static jenkins.plugins.http_request.Registers.registerFormData;
import static jenkins.plugins.http_request.Registers.registerInvalidStatusCode;
import static jenkins.plugins.http_request.Registers.registerReqAction;
import static jenkins.plugins.http_request.Registers.registerRequestChecker;
import static jenkins.plugins.http_request.Registers.registerTimeout;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.FilenameUtils;
import org.apache.http.entity.ContentType;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import hudson.model.Result;

import jenkins.plugins.http_request.auth.FormAuthentication;
import jenkins.plugins.http_request.util.HttpRequestNameValuePair;
import jenkins.plugins.http_request.util.RequestAction;

/**
 * @author Martin d'Anjou
 */
public class HttpRequestStepTest extends HttpRequestTestBase {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void simpleGetTest() throws Exception {
        // Prepare the server
        registerRequestChecker(HttpMode.GET);

        // Configure the build
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        proj.setDefinition(new CpsFlowDefinition(
            "def response = httpRequest '"+baseURL()+"/doGET'\n" +
            "println('Status: '+response.status)\n" +
            "println('Response: '+response.content)\n",
            true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(run);
        j.assertLogContains("Status: 200",run);
        j.assertLogContains("Response: "+ ALL_IS_WELL,run);
        j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", run);
    }

    @Test
    public void quietTest() throws Exception {
        // Prepare the server
        registerRequestChecker(HttpMode.GET);

        // Configure the build
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        proj.setDefinition(new CpsFlowDefinition(
                "def response = httpRequest(url: '"+baseURL()+"/doGET', quiet: true)\n" +
                        "println('Status: '+response.status)\n" +
                        "println('Response: '+response.content)\n",
                true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(run);
        j.assertLogContains("Status: 200",run);
        j.assertLogContains("Response: "+ ALL_IS_WELL,run);
        j.assertLogNotContains("HttpMethod:", run);
        j.assertLogNotContains("URL:", run);
        j.assertLogNotContains("Sending request to url:", run);
        j.assertLogNotContains("Response Code:", run);
    }

    @Test
    public void canDetectActualContent() throws Exception {
        // Setup the expected pattern
        String findMe = ALL_IS_WELL;

        // Prepare the server
        registerRequestChecker(HttpMode.GET);

        // Configure the build
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        proj.setDefinition(new CpsFlowDefinition(
            "def response = httpRequest url:'"+baseURL()+"/doGET',\n" +
            "    consoleLogResponseBody: true\n",
            true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(run);
        j.assertLogContains(findMe,run);
        j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", run);
    }

    @Test
    public void badContentFailsTheBuild() throws Exception {
        // Prepare the server
        registerRequestChecker(HttpMode.GET);

        // Configure the build
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        proj.setDefinition(new CpsFlowDefinition(
            "def response = httpRequest url:'"+baseURL()+"/doGET',\n" +
            "    consoleLogResponseBody: true,\n" +
            "    validResponseContent: 'bad content'\n" +
            "println('Status: '+response.getStatus())\n" +
            "println('Response: '+response.getContent())\n",
            true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.FAILURE, run);
        j.assertLogContains("Fail: Response doesn't contain expected content 'bad content'", run);
        j.assertLogContains(" while calling " + baseURL(), run);
        j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", run);
    }

    @Test
    public void responseMatchAcceptedMimeType() throws Exception {
        // Prepare the server
        registerRequestChecker(HttpMode.GET);

        // Configure the build
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        proj.setDefinition(new CpsFlowDefinition(
            "def response = httpRequest url:'"+baseURL()+"/doGET',\n" +
            "    consoleLogResponseBody: true,\n" +
            "    acceptType: 'TEXT_PLAIN'\n" +
            "println('Status: '+response.getStatus())\n" +
            "println('Response: '+response.getContent())\n",
            true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(run);
        j.assertLogContains(ALL_IS_WELL,run);
        j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", run);
    }

    @Test
    public void responseDoesNotMatchAcceptedMimeTypeDoesNotFailTheBuild() throws Exception {
        // Prepare the server
        registerRequestChecker(HttpMode.GET);

        // Configure the build
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        proj.setDefinition(new CpsFlowDefinition(
            "def response = httpRequest url:'"+baseURL()+"/doGET',\n" +
            "    consoleLogResponseBody: true,\n" +
            "    acceptType: 'TEXT_HTML'\n" +
            "println('Status: '+response.getStatus())\n" +
            "println('Response: '+response.getContent())\n",
            true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(run);
        j.assertLogContains(ALL_IS_WELL,run);
        j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", run);
    }

    @Test
    public void doAllRequestTypes() throws Exception {
        for (HttpMode mode: HttpMode.values()) {
            // Prepare the server
            registerRequestChecker(mode);
            doRequest(mode);

            cleanHandlers();
        }
    }

    public void doRequest(final HttpMode mode) throws Exception {
        // Configure the build
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj"+mode.toString());
        proj.setDefinition(new CpsFlowDefinition(
            "def response = httpRequest url:'"+baseURL()+"/do"+ mode +"',\n" +
            "    consoleLogResponseBody: true,\n" +
            "    httpMode: '"+ mode +"'\n" +
            "println('Status: '+response.getStatus())\n" +
            "println('Response: '+response.getContent())\n",
            true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(run);

        if (mode == HttpMode.HEAD) return;

        j.assertLogContains(ALL_IS_WELL,run);
        j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", run);
    }

    @Test
    public void invalidResponseCodeFailsTheBuild() throws Exception {
        // Prepare the server
        registerInvalidStatusCode();

        // Configure the build
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        proj.setDefinition(new CpsFlowDefinition(
            "def response = httpRequest url:'"+baseURL()+"/invalidStatusCode',\n" +
            "    consoleLogResponseBody: true\n" +
            "println('Status: '+response.getStatus())\n" +
            "println('Response: '+response.getContent())\n",
            true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.FAILURE, run);
        j.assertLogContains("Throwing status 400 for test",run);
        j.assertLogContains("Fail: Status code 400 is not in the accepted range: 100:399", run);
        j.assertLogContains(" while calling " + baseURL(), run);
    }

    @Test
    public void invalidResponseCodeIsAccepted() throws Exception {
        // Prepare the server
        registerInvalidStatusCode();

        // Configure the build
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        proj.setDefinition(new CpsFlowDefinition(
            "def response = httpRequest url:'"+baseURL()+"/invalidStatusCode',\n" +
            "    consoleLogResponseBody: true,\n" +
            "    validResponseCodes: '100:599'\n" +
            "println('Status: '+response.getStatus())\n" +
            "println('Response: '+response.getContent())\n",
            true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(run);
        j.assertLogContains("Throwing status 400 for test",run);
        j.assertLogContains("Success: Status code 400 is in the accepted range: 100:599", run);
        j.assertLogNotContains(" while calling " + baseURL(), run);
    }

    @Test
    public void reverseRangeFailsTheBuild() throws Exception {
        // Prepare the server
        registerInvalidStatusCode();

        // Configure the build
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        proj.setDefinition(new CpsFlowDefinition(
            "def response = httpRequest url:'"+baseURL()+"/invalidStatusCode',\n" +
            "    consoleLogResponseBody: true,\n" +
            "    validResponseCodes: '599:100'\n" +
            "println('Status: '+response.getStatus())\n" +
            "println('Response: '+response.getContent())\n",
            true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.FAILURE, run);
        j.assertLogContains("Interval 599:100 should be FROM less than TO", run);
    }

    @Test
    public void notANumberRangeValueFailsTheBuild() throws Exception {
        // Prepare the server
        registerInvalidStatusCode();

        // Configure the build
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        proj.setDefinition(new CpsFlowDefinition(
            "def response = httpRequest url:'"+baseURL()+"/invalidStatusCode',\n" +
            "    consoleLogResponseBody: true,\n" +
            "    validResponseCodes: 'text'\n" +
            "println('Status: '+response.getStatus())\n" +
            "println('Response: '+response.getContent())\n",
            true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.FAILURE, run);
        j.assertLogContains("Invalid number text", run);
    }

    @Test
    public void rangeWithTextFailsTheBuild() throws Exception {
        // Prepare the server
        registerInvalidStatusCode();

        // Configure the build
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        proj.setDefinition(new CpsFlowDefinition(
            "def response = httpRequest url:'"+baseURL()+"/invalidStatusCode',\n" +
            "    consoleLogResponseBody: true,\n" +
            "    validResponseCodes: '1:text'\n" +
            "println('Status: '+response.getStatus())\n" +
            "println('Response: '+response.getContent())\n",
            true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.FAILURE, run);
        j.assertLogContains("Invalid number text", run);
    }

    @Test
    public void invalidRangeFailsTheBuild() throws Exception {
        // Prepare the server
        registerInvalidStatusCode();

        // Configure the build
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        proj.setDefinition(new CpsFlowDefinition(
            "def response = httpRequest url:'"+baseURL()+"/invalidStatusCode',\n" +
            "    consoleLogResponseBody: true,\n" +
            "    validResponseCodes: '1:2:3'\n" +
            "println('Status: '+response.getStatus())\n" +
            "println('Response: '+response.getContent())\n",
            true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.FAILURE, run);
        j.assertLogContains("Code 1:2:3 should be an interval from:to or a single value", run);
    }

    @Test
    public void sendAllContentTypes() throws Exception {
        for (MimeType mimeType : MimeType.values()) {
            // Prepare the server
            registerContentTypeRequestChecker(mimeType, HttpMode.GET, ALL_IS_WELL);

            sendContentType(mimeType);
            cleanHandlers();
        }
    }

    public void sendContentType(final MimeType mimeType) throws Exception {
        // Configure the build
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj"+mimeType.toString());
        proj.setDefinition(new CpsFlowDefinition(
            "def response = httpRequest url:'"+baseURL()+"/incoming_"+ mimeType +"',\n" +
            "    consoleLogResponseBody: true,\n" +
            "    contentType: '"+ mimeType +"'\n" +
            "println('Status: '+response.getStatus())\n" +
            "println('Response: '+response.getContent())\n",
            true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(run);
        j.assertLogContains(ALL_IS_WELL,run);
        j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", run);
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
        // Configure the build
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj"+mimeType.toString());
        proj.setDefinition(new CpsFlowDefinition(
            "def response = httpRequest url:'"+baseURL()+"/accept_"+ mimeType +"',\n" +
            "    consoleLogResponseBody: true,\n" +
            "    acceptType: '"+ mimeType +"'\n" +
            "println('Status: '+response.getStatus())\n" +
            "println('Response: '+response.getContent())\n",
            true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(run);
        j.assertLogContains(ALL_IS_WELL,run);
        j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", run);
    }

    @Test
    public void timeoutFailsTheBuild() throws Exception {
        // Prepare the server
        registerTimeout();

        // Configure the build
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        proj.setDefinition(new CpsFlowDefinition(
            "def response = httpRequest url:'"+baseURL()+"/timeout',\n" +
            "    timeout: 2\n" +
            "println('Status: '+response.getStatus())\n" +
            "println('Response: '+response.getContent())\n",
            true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.FAILURE, run);
        j.assertLogContains("Fail: Status code 408 is not in the accepted range: 100:399", run);
        j.assertLogContains(" while calling " + baseURL(), run);
    }

    @Test
    public void canDoCustomHeaders() throws Exception {
        // Prepare the server
        registerCustomHeaders();

        // Configure the build
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        proj.setDefinition(new CpsFlowDefinition(
            "def response = httpRequest url:'"+baseURL()+"/customHeaders',\n" +
            "    customHeaders: [[name: 'customHeader', value: 'value1'],[name: 'customHeader', value: 'value2']]\n" +
            "println('Status: '+response.getStatus())\n" +
            "println('Response: '+response.getContent())\n",
            true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(run);
        j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", run);
    }

    @Test
    public void nonExistentBasicAuthFailsTheBuild() throws Exception {
        // Prepare the server
        registerBasicAuth();

        // Configure the build
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        proj.setDefinition(new CpsFlowDefinition(
            "def response = httpRequest url:'"+baseURL()+"/basicAuth',\n" +
            "    authentication: 'invalid'\n" +
            "println('Status: '+response.getStatus())\n" +
            "println('Response: '+response.getContent())\n",
            true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.FAILURE, run);
        j.assertLogContains("Authentication 'invalid' doesn't exist anymore", run);
    }

    @Test
    public void canDoBasicDigestAuthentication() throws Exception {
        // Prepare the server
        registerBasicAuth();

        // Prepare the authentication
        registerBasicCredential("keyname1", "username1", "password1");
        registerBasicCredential("keyname2", "username2", "password2");

        // Prepare HttpRequest
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        proj.setDefinition(new CpsFlowDefinition(
            "def response = httpRequest url:'"+baseURL()+"/basicAuth',\n" +
            "    authentication: 'keyname1'\n" +
            "println('Status: '+response.getStatus())\n" +
            "println('Response: '+response.getContent())\n",
            true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(run);
        j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", run);
    }

    @Test
    public void canDoFormAuthentication() throws Exception {
        // Prepare the server
        registerReqAction();
        registerFormAuth();

        // Prepare the authentication
        List<HttpRequestNameValuePair> params = new ArrayList<>();
        params.add(new HttpRequestNameValuePair("param1","value1"));
        params.add(new HttpRequestNameValuePair("param2","value2"));

        RequestAction action = new RequestAction(new URL(baseURL()+"/reqAction"),HttpMode.GET,null,params);
        List<RequestAction> actions = new ArrayList<>();
        actions.add(action);

        FormAuthentication formAuth = new FormAuthentication("keyname",actions);
        List<FormAuthentication> formAuthList = new ArrayList<>();
        formAuthList.add(formAuth);

        // Store the configuration
        HttpRequestGlobalConfig.get().setFormAuthentications(formAuthList);

        // Prepare HttpRequest
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        proj.setDefinition(new CpsFlowDefinition(
            "def response = httpRequest url:'"+baseURL()+"/formAuth',\n" +
            "    authentication: 'keyname'\n",
            true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(run);
        j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", run);
    }

    @Test
    public void rejectedFormCredentialsFailTheBuild() throws Exception {
        // Prepare the server
        registerFormAuthBad();

        // Prepare the authentication
        List<HttpRequestNameValuePair> params = new ArrayList<>();
        params.add(new HttpRequestNameValuePair("param1","value1"));
        params.add(new HttpRequestNameValuePair("param2","value2"));

        RequestAction action = new RequestAction(new URL(baseURL()+"/formAuthBad"),HttpMode.GET,null,params);
        List<RequestAction> actions = new ArrayList<>();
        actions.add(action);

        FormAuthentication formAuth = new FormAuthentication("keyname",actions);
        List<FormAuthentication> formAuthList = new ArrayList<>();
        formAuthList.add(formAuth);

        // Store the configuration
        HttpRequestGlobalConfig.get().setFormAuthentications(formAuthList);

        // Prepare HttpRequest
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        proj.setDefinition(new CpsFlowDefinition(
            "def response = httpRequest url:'"+baseURL()+"/formAuthBad',\n" +
            "    authentication: 'keyname'\n",
            true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.FAILURE, run);
        j.assertLogContains("Error doing authentication",run);
    }

    @Test
    public void invalidKeyFormAuthenticationFailsTheBuild() throws Exception {
        // Prepare the authentication
        List<HttpRequestNameValuePair> params = new ArrayList<>();
        params.add(new HttpRequestNameValuePair("param1","value1"));
        params.add(new HttpRequestNameValuePair("param2","value2"));

        // The request action won't be sent but we need to prepare it
        RequestAction action = new RequestAction(new URL(baseURL()+"/non-existent"),HttpMode.GET,null,params);
        List<RequestAction> actions = new ArrayList<>();
        actions.add(action);

        FormAuthentication formAuth = new FormAuthentication("keyname",actions);
        List<FormAuthentication> formAuthList = new ArrayList<>();
        formAuthList.add(formAuth);

        // Store the configuration
        HttpRequestGlobalConfig.get().setFormAuthentications(formAuthList);

        // Prepare HttpRequest
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        proj.setDefinition(new CpsFlowDefinition(
            "def response = httpRequest url:'"+baseURL()+"/non-existent',\n" +
            "    authentication: 'non-existent'\n",
            true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.FAILURE, run);
        j.assertLogContains("Authentication 'non-existent' doesn't exist anymore", run);
    }

    @Test
    public void testPostBody() throws Exception {
        //configure server
        registerHandler("/doPostBody", HttpMode.POST, new SimpleHandler() {
            @Override
            boolean doHandle(Request request, Response response, Callback callback) throws IOException, ExecutionException, InterruptedException {
                assertEquals("POST", request.getMethod());

                String body = requestBody(request);
                body(response, HttpStatus.OK_200, ContentType.TEXT_PLAIN, body, callback);
                return true;
            }
        });

        String body = "send-body-workflow";

        // Configure the build
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "postBody");
        proj.setDefinition(new CpsFlowDefinition(
                "def response = httpRequest" +
                        " httpMode: 'POST'," +
                        " requestBody: '" + body + "'," +
                        " url: '" + baseURL() + "/doPostBody'\n" +
                        "println('Response: ' + response.content)\n",
                true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(run);
        j.assertLogContains("Response: " + body, run);
        j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", run);
    }

    @Test
    public void testFileUpload() throws Exception {
        // Prepare the server
        final File testFolder = folder.newFolder();
        File uploadFile = File.createTempFile("upload", ".zip", testFolder);
        String responseText = "File upload successful!";
        registerFileUpload(testFolder, uploadFile, responseText);

        // Prepare HttpRequest
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "uploadFile");
        proj.setDefinition(new CpsFlowDefinition(
                "node {\n"+
                    "def response = httpRequest" +
                        " httpMode: 'POST'," +
                        " validResponseCodes: '201'," +
                        " consoleLogResponseBody: true," +
                        " acceptType: '" + MimeType.TEXT_PLAIN + "'," +
                        " contentType: '" + MimeType.APPLICATION_ZIP + "'," +
                        " uploadFile: '" + uploadFile.getAbsolutePath().replace("\\", "\\\\") + "'," +
                        " multipartName: 'file-name'," +
                        " url: '" + baseURL() + "/uploadFile'\n" +
                "}",
                true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(run);
        j.assertLogContains(responseText, run);
        j.assertLogContains("Success: Status code 201 is in the accepted range: 201", run);
    }

	@Test
	public void testFormData() throws Exception {
		final File testFolder = folder.newFolder();
		File projectRoot = Paths.get("").toAbsolutePath().toFile();
		String responseText = "File upload successful!";
		String json = "{\"foo\": \"bar\"}";
		File file1 = new File(projectRoot, "src/test/resources/testdata/readme.txt");
		File file2 = new File(projectRoot, "src/test/resources/testdata/small.zip");
		registerFormData(testFolder, json, file1, file2, responseText);

		// Let's upload these files and a JSON
		String script = "node {\n"
				+ "def response = httpRequest httpMode: 'POST', validResponseCodes: '201', "
				+ "consoleLogResponseBody: true, acceptType: '" + MimeType.TEXT_PLAIN.toString()
				+ "', url: '" + baseURL() + "/formData', "
				+ "formData: [[contentType: 'application/json', body: '" + json
				+ "', name: 'model'], [contentType: 'text/plain', name: 'file1', fileName: 'readme.txt', uploadFile: '"
				+ FilenameUtils.separatorsToUnix(file1.getPath()) // Developing on windows is a	joyride
				+ "'], [contentType: 'application/zip', name: 'file2', fileName: 'small.zip', uploadFile: '"
				+ FilenameUtils.separatorsToUnix(file2.getPath()) + "']]\n}";

		// Prepare HttpRequest
		WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "formData");
		proj.setDefinition(new CpsFlowDefinition(script, true));

		// Execute the build
		WorkflowRun run = proj.scheduleBuild2(0).get();

		// Check expectations
		j.assertBuildStatusSuccess(run);
		j.assertLogContains(responseText, run);
	}

    @Test
    public void nonExistentProxyAuthFailsTheBuild() throws Exception {
        // Prepare the server
        registerBasicAuth();

        // Configure the build
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        proj.setDefinition(new CpsFlowDefinition(
                "def response = httpRequest url:'"+baseURL()+"/proxyAuth',\n" +
                        "    httpProxy: 'http://proxy.example.com:8080',\n" +
                        "    proxyAuthentication: 'invalid'\n" +
                        "println('Status: '+response.getStatus())\n" +
                        "println('Response: '+response.getContent())\n",
                true));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.FAILURE, run);
        j.assertLogContains("Proxy authentication 'invalid' doesn't exist anymore or is not a username/password credential type", run);
    }
}
