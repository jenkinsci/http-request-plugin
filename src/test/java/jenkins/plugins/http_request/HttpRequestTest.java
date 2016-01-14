package jenkins.plugins.http_request;

import hudson.model.Cause.UserIdCause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.StringParameterValue;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.jvnet.hudson.test.JenkinsRule;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.localserver.LocalServerTestBase;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

public class HttpRequestTest extends LocalServerTestBase {

    // Note: The JenkinsRule is required, otherwise the call to getDescriptor causes a null pointer exception
    @Rule
    public JenkinsRule j = new JenkinsRule();

    public void setupRequestChecker(final String path, final String method) {
        this.serverBootstrap.registerHandler(path, new HttpRequestHandler() {
            @Override
            public void handle(
                    final org.apache.http.HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                List<NameValuePair> parameters;
                assertEquals(request.getRequestLine().getMethod(), method);
                String uriStr = request.getRequestLine().getUri();
                String query;
                try {
                    query = new URI(uriStr).getQuery();
                } catch (URISyntaxException ex) {
                    throw new IOException("A URISyntaxException occured: "+ex.getCause().getMessage());
                }
                assertNull(query);
                response.setEntity(new StringEntity("All is well", ContentType.TEXT_PLAIN));
            }
        });

    }

    @Before
    public void setUp() throws Exception {
        super.setUp();

        setupRequestChecker("/",         "GET");
        setupRequestChecker("/doGET",    "GET");
        setupRequestChecker("/doPOST",   "POST");
        setupRequestChecker("/doDELETE", "DELETE");
        setupRequestChecker("/doPUT",    "PUT");
        setupRequestChecker("/doHEAD",   "HEAD");

        // Check that exactly one build parameter is passed
        this.serverBootstrap.registerHandler("/checkBuildParameters", new HttpRequestHandler() {
            @Override
            public void handle(
                    final org.apache.http.HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                List<NameValuePair> parameters;
                assertEquals(request.getRequestLine().getMethod(), "GET");
                try {
                    parameters = URLEncodedUtils.parse(new URI(request.getRequestLine().getUri()).getQuery(), StandardCharsets.UTF_8);
                } catch (URISyntaxException ex) {
                    throw new IOException("A URISyntaxException occured: "+ex.getCause().getMessage());
                }
                assertEquals(1,parameters.size());
                assertEquals("foo",parameters.get(0).getName());
                assertEquals("value",parameters.get(0).getValue());
                response.setEntity(new StringEntity("All is well", ContentType.TEXT_PLAIN));
            }
        });

    }

    @After
    public void shutDown() throws Exception {
        Executor.closeIdleConnections();
        super.shutDown();
    }

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
        HttpRequest httpRequest = new HttpRequest(baseURL);
        httpRequest.setHttpMode(HttpMode.GET);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(build);
        String s = FileUtils.readFileToString(build.getLogFile());
        Pattern p = Pattern.compile("All is well");
        Matcher m = p.matcher(s);
        assertTrue(m.find());
    }

    @Test
    public void detectActualContent() throws Exception {
        // Setup the expected pattern
        String findMe = "All is well";
        String findMePattern = Pattern.quote(findMe);

        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL);
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
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL);
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
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL);
        httpRequest.setHttpMode(HttpMode.GET);

        // Expect a mime type that matches the response
        httpRequest.setAcceptType(MimeType.TEXT_PLAIN);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(build);
        String s = FileUtils.readFileToString(build.getLogFile());
        Pattern p = Pattern.compile("All is well");
        Matcher m = p.matcher(s);
        assertTrue(m.find());
    }

    @Test
    public void responseDoesNotMatchAcceptedMimeType() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL);
        httpRequest.setHttpMode(HttpMode.GET);

        // Expect a mime type that does not matche the response
        httpRequest.setAcceptType(MimeType.TEXT_HTML);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(build);
        String s = FileUtils.readFileToString(build.getLogFile());
        Pattern p = Pattern.compile("All is well");
        Matcher m = p.matcher(s);
        assertTrue(m.find());
    }

    @Test
    public void passBuildParametersWithBuildParameters() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/checkBuildParameters");
        httpRequest.setHttpMode(HttpMode.GET);

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
        String s = FileUtils.readFileToString(build.getLogFile());
        Pattern p = Pattern.compile("All is well");
        Matcher m = p.matcher(s);
        assertTrue(m.find());
    }

    @Test
    public void passBuildParametersWithNoBuildParameters() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL);
        httpRequest.setHttpMode(HttpMode.GET);

        // Activate passBuildParameters
        httpRequest.setPassBuildParameters(true);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(build);
        String s = FileUtils.readFileToString(build.getLogFile());
        Pattern p = Pattern.compile("All is well");
        Matcher m = p.matcher(s);
        assertTrue(m.find());
    }

    @Test
    public void doNotPassBuildParametersWithBuildParameters() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL);
        httpRequest.setHttpMode(HttpMode.GET);

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
        String s = FileUtils.readFileToString(build.getLogFile());
        Pattern p = Pattern.compile("All is well");
        Matcher m = p.matcher(s);
        assertTrue(m.find());
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

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(build);

        if (mode == HttpMode.HEAD) return;

        String s = FileUtils.readFileToString(build.getLogFile());
        Pattern p = Pattern.compile("All is well");
        Matcher m = p.matcher(s);
        assertTrue(m.find());
    }

    // TODO: for all these future tests, it would be ideal to have a mock server that receives and checks the actual http packets
    //    valid response codes, invalid response codes
    //    send different content types, check that they are effectively produced in the outgoing packets
    //    output file
    //    timeout
    //    set consoleLogResponseBody to false and check that nothing goes to console
    //    authentication (basic, form)
}
