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

public class HttpRequestTestBase extends LocalServerTestBase {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    final String allIsWellMessage = "All is well";

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
                response.setEntity(new StringEntity(allIsWellMessage, ContentType.TEXT_PLAIN));
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
                response.setEntity(new StringEntity(allIsWellMessage, ContentType.TEXT_PLAIN));
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
                response.setEntity(new StringEntity(allIsWellMessage, ContentType.TEXT_PLAIN));
            }
        });
    }

    @Before
    public void setupTest() throws Exception {
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
                response.setEntity(new StringEntity(allIsWellMessage, ContentType.TEXT_PLAIN));
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
                response.setEntity(new StringEntity(allIsWellMessage, ContentType.TEXT_PLAIN));
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
                response.setEntity(new StringEntity(allIsWellMessage, ContentType.TEXT_PLAIN));
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
                response.setEntity(new StringEntity(allIsWellMessage, ContentType.TEXT_PLAIN));
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
                response.setEntity(new StringEntity(allIsWellMessage, ContentType.TEXT_PLAIN));
            }
        });
    }

    @After
    public void afterTest() throws Exception {
        Executor.closeIdleConnections();
        super.shutDown();
    }

}
