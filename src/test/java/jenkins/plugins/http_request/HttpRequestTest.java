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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jenkins.plugins.http_request.auth.BasicDigestAuthentication;
import jenkins.plugins.http_request.auth.FormAuthentication;
import jenkins.plugins.http_request.util.NameValuePair;
import jenkins.plugins.http_request.util.RequestAction;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.jvnet.hudson.test.JenkinsRule;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
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

    public void setupRequestChecker(final HttpMode httpMode) {
        this.serverBootstrap.registerHandler("/do"+httpMode.toString(), new HttpRequestHandler() {
            @Override
            public void handle(
                final org.apache.http.HttpRequest request,
                final HttpResponse response,
                final HttpContext context
            ) throws HttpException, IOException {
                List<org.apache.http.NameValuePair> parameters;
                assertEquals(httpMode.toString(), request.getRequestLine().getMethod());
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

    public void setupContentTypeRequestChecker(final MimeType mimeType) {
        this.serverBootstrap.registerHandler("/incoming_"+mimeType.toString(), new HttpRequestHandler() {
            @Override
            public void handle(
                final org.apache.http.HttpRequest request,
                final HttpResponse response,
                final HttpContext context
            ) throws HttpException, IOException {
                assertEquals("GET", request.getRequestLine().getMethod());
                Header[] headers = request.getHeaders("Content-type");
                if (mimeType == MimeType.NOT_SET) {
                    assertEquals(0, headers.length);
                } else {
                    assertEquals(1, headers.length);
                    assertEquals(mimeType.getValue(), headers[0].getValue());
                }
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

    public void setupAcceptedTypeRequestChecker(final MimeType mimeType) {
        this.serverBootstrap.registerHandler("/accept_"+mimeType.toString(), new HttpRequestHandler() {
            @Override
            public void handle(
                final org.apache.http.HttpRequest request,
                final HttpResponse response,
                final HttpContext context
            ) throws HttpException, IOException {
                assertEquals("GET", request.getRequestLine().getMethod());
                Header[] headers = request.getHeaders("Accept");
                if (mimeType == MimeType.NOT_SET) {
                    assertEquals(0, headers.length);
                } else {
                    assertEquals(1, headers.length);
                    assertEquals(mimeType.getValue(), headers[0].getValue());
                }
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

        for (HttpMode httpMode : HttpMode.values()) {
            setupRequestChecker(httpMode);
        }

        for (MimeType mimeType : MimeType.values()) {
            setupContentTypeRequestChecker(mimeType);
        }

        for (MimeType mimeType : MimeType.values()) {
            setupAcceptedTypeRequestChecker(mimeType);
        }

        // Check that exactly one build parameter is passed
        this.serverBootstrap.registerHandler("/checkBuildParameters", new HttpRequestHandler() {
            @Override
            public void handle(
                final org.apache.http.HttpRequest request,
                final HttpResponse response,
                final HttpContext context
            ) throws HttpException, IOException {
                assertEquals("GET", request.getRequestLine().getMethod());
                List<org.apache.http.NameValuePair> parameters;
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

        // Return an invalid status code
        this.serverBootstrap.registerHandler("/invalidStatusCode", new HttpRequestHandler() {
            @Override
            public void handle(
                final org.apache.http.HttpRequest request,
                final HttpResponse response,
                final HttpContext context
            ) throws HttpException, IOException {
                assertEquals("GET", request.getRequestLine().getMethod());
                String uriStr = request.getRequestLine().getUri();
                String query;
                try {
                    query = new URI(uriStr).getQuery();
                } catch (URISyntaxException ex) {
                    throw new IOException("A URISyntaxException occured: "+ex.getCause().getMessage());
                }
                assertNull(query);
                response.setEntity(new StringEntity("Throwing status 400 for test", ContentType.TEXT_PLAIN));
                response.setStatusCode(400);
            }
        });

        // Timeout, do not respond!
        this.serverBootstrap.registerHandler("/timeout", new HttpRequestHandler() {
            @Override
            public void handle(
                final org.apache.http.HttpRequest request,
                final HttpResponse response,
                final HttpContext context
            ) throws HttpException, IOException {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ex) {
                    // do nothing the sleep will be interrupted when the test ends
                }
            }
        });

        // Check the basic authentication header
        this.serverBootstrap.registerHandler("/basicAuth", new HttpRequestHandler() {
            @Override
            public void handle(
                final org.apache.http.HttpRequest request,
                final HttpResponse response,
                final HttpContext context
            ) throws HttpException, IOException {
                Header[] headers = request.getAllHeaders();
                headers = request.getHeaders("Authorization");
                assertEquals(1, headers.length);
                Header auth = headers[0];
                Base64 base64 = new Base64();
                byte[] bytes = base64.decodeBase64(auth.getValue().substring(6));
                String usernamePasswordPair = new String(bytes);
                String[] usernamePassword = usernamePasswordPair.split(":");
                assertEquals("username1", usernamePassword[0]);
                assertEquals("password1", usernamePassword[1]);
                response.setEntity(new StringEntity("All is well", ContentType.TEXT_PLAIN));
            }
        });

        // Accept the form authentication
        this.serverBootstrap.registerHandler("/reqAction", new HttpRequestHandler() {
            @Override
            public void handle(
                final org.apache.http.HttpRequest request,
                final HttpResponse response,
                final HttpContext context
            ) throws HttpException, IOException {
                assertEquals("GET", request.getRequestLine().getMethod());
                List<org.apache.http.NameValuePair> parameters;
                try {
                    parameters = URLEncodedUtils.parse(new URI(request.getRequestLine().getUri()).getQuery(), StandardCharsets.UTF_8);
                } catch (URISyntaxException ex) {
                    throw new IOException("A URISyntaxException occured: "+ex.getCause().getMessage());
                }
                assertEquals(2,parameters.size());
                assertEquals("param1",parameters.get(0).getName());
                assertEquals("value1",parameters.get(0).getValue());
                assertEquals("param2",parameters.get(1).getName());
                assertEquals("value2",parameters.get(1).getValue());
                response.setEntity(new StringEntity("All is well", ContentType.TEXT_PLAIN));
            }
        });

        // Check the form authentication
        this.serverBootstrap.registerHandler("/formAuth", new HttpRequestHandler() {
            @Override
            public void handle(
                final org.apache.http.HttpRequest request,
                final HttpResponse response,
                final HttpContext context
            ) throws HttpException, IOException {
                response.setEntity(new StringEntity("All is well", ContentType.TEXT_PLAIN));
            }
        });

        // Check the form authentication header
        this.serverBootstrap.registerHandler("/formAuthBad", new HttpRequestHandler() {
            @Override
            public void handle(
                final org.apache.http.HttpRequest request,
                final HttpResponse response,
                final HttpContext context
            ) throws HttpException, IOException {
                response.setEntity(new StringEntity("Not allowed", ContentType.TEXT_PLAIN));
                response.setStatusCode(400);
            }
        });

        // Check the basic authentication header
        this.serverBootstrap.registerHandler("/customHeaders", new HttpRequestHandler() {
            @Override
            public void handle(
                final org.apache.http.HttpRequest request,
                final HttpResponse response,
                final HttpContext context
            ) throws HttpException, IOException {
                Header[] headers = request.getAllHeaders();
                headers = request.getHeaders("customHeader");
                assertEquals(2, headers.length);
                assertEquals("value1", headers[0].getValue());
                assertEquals("value2", headers[1].getValue());
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
        HttpRequest httpRequest = new HttpRequest(baseURL+"/doGET");
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
    public void canDetectActualContent() throws Exception {
        // Setup the expected pattern
        String findMe = "All is well";
        String findMePattern = Pattern.quote(findMe);

        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/doGET");
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
    public void badContentFailsTheBuild() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/doGET");
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
        HttpRequest httpRequest = new HttpRequest(baseURL+"/doGET");
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
    public void responseDoesNotMatchAcceptedMimeTypeDoesNotFailTheBuild() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/doGET");
        httpRequest.setHttpMode(HttpMode.GET);

        // Expect a mime type that does not match the response
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
    public void passBuildParametersWhenAskedAndParamtersArePresent() throws Exception {
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
    public void silentlyIgnoreNonExistentBuildParameters() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/doGET");
        httpRequest.setHttpMode(HttpMode.GET);

        // Activate passBuildParameters without parameters present
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
        HttpRequest httpRequest = new HttpRequest(baseURL+"/doGET");
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

    @Test
    public void invalidResponseCodeFailsTheBuild() throws Exception {
        // Prepare the server
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(baseURL+"/invalidStatusCode");
        httpRequest.setHttpMode(HttpMode.GET);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.FAILURE, build);
        String s = FileUtils.readFileToString(build.getLogFile());
        Pattern p = Pattern.compile("Throwing status 400 for test");
        Matcher m = p.matcher(s);
        assertTrue(m.find());
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

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(build);
        String s = FileUtils.readFileToString(build.getLogFile());
        Pattern p = Pattern.compile("Throwing status 400 for test");
        Matcher m = p.matcher(s);
        assertTrue(m.find());
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
        httpRequest.setContentType(mimeType);

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
        httpRequest.setAcceptType(mimeType);

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
    public void canPutResponseInOutputFile() throws Exception {
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

        // By default, the response is printed to the console even if an outputFile is used
        String s = FileUtils.readFileToString(build.getLogFile());
        Pattern p = Pattern.compile("All is well");
        Matcher m = p.matcher(s);
        assertTrue(m.find());

        // The response is in the output file
        String outputFile = build.getWorkspace().child("file.txt").readToString();
        p = Pattern.compile("All is well");
        m = p.matcher(outputFile);
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
        httpRequest.setConsoleLogResponseBody(false);
        httpRequest.setHttpMode(HttpMode.GET);

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatusSuccess(build);

        // Check that the console does NOT have the response body
        String s = FileUtils.readFileToString(build.getLogFile());
        Pattern p = Pattern.compile("All is well");
        Matcher m = p.matcher(s);
        assertFalse(m.find());

        // The response is in the output file
        String outputFile = build.getWorkspace().child("file.txt").readToString();
        p = Pattern.compile("All is well");
        m = p.matcher(outputFile);
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
        httpRequest.getDescriptor().setBasicDigestAuthentications(bda);
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
        httpRequest.getDescriptor().setFormAuthentications(formAuthList);
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

        // Prepare HttpRequest (this request won't be sent)
        HttpRequest httpRequest = new HttpRequest(baseURL+"/formAuthBad");
        httpRequest.setHttpMode(HttpMode.GET);
        httpRequest.getDescriptor().setFormAuthentications(formAuthList);
        httpRequest.setAuthentication("keyname");

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.FAILURE, build);

        String s = FileUtils.readFileToString(build.getLogFile());
        assertThat(s, org.hamcrest.CoreMatchers.containsString("Error doing authentication"));
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
        httpRequest.getDescriptor().setFormAuthentications(formAuthList);

        // Select a non-existent form authentication, this will error the build before any request is made
        httpRequest.setAuthentication("non-existent");

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.FAILURE, build);

        String s = FileUtils.readFileToString(build.getLogFile());
        assertThat(s, org.hamcrest.CoreMatchers.containsString("Authentication 'non-existent' doesn't exist anymore"));
    }
}