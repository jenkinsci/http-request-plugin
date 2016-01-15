package jenkins.plugins.http_request;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.apache.commons.io.FileUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThat;

import org.junit.Test;
import org.junit.Rule;
import org.junit.BeforeClass;
import org.junit.AfterClass;

import org.jvnet.hudson.test.JenkinsRule;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import com.github.tomakehurst.wiremock.common.FatalStartupException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.Handler;
import java.util.logging.Filter;
import java.util.logging.LogRecord;

import jenkins.model.JenkinsLocationConfiguration;

public class HttpRequestTestWireMock {

    // The class under test
    HttpRequest httpRequest;

    @Rule
    public JenkinsRule j = new JenkinsRule();

    public static WireMockServer wireMockServer;

    public class MyFilter implements Filter {
        public boolean isLoggable(LogRecord record) {
            return false;
        }
    }
//    public static final Logger logger = LoggerFactory.getLogger(HttpRequestTest.class);
    public static String urlForTest;

    @BeforeClass
    public static void setup() throws IOException {
        int portNum;
        for (portNum = 49152; portNum <= 65535; portNum++) {
            wireMockServer = new WireMockServer(wireMockConfig().port(portNum));
            try {
                wireMockServer.start();
            } catch (FatalStartupException exception) {
                continue;
            }
            //logger.debug("Started WireMockServer on port "+portNum);
            break;
        }
        if (portNum > 65535) {
          throw new IOException("Unable to allocation a port number to run the WireMock server.");
        }
        urlForTest = "http://localhost:"+portNum;
    }

    @AfterClass
    public static void cleanup() {
        wireMockServer.stop();
    }

    @Test
    public void testWithMock() throws Exception {

        // WireMock verbosity
        wireMockConfig().notifier(new ConsoleNotifier(true));

        // Prepare a response
        wireMockServer.stubFor(get(urlEqualTo("/path"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "text/plain")
                .withBody("Hello World")));

        System.out.println("Jenkins rule: "+j.getURL().toString());

        // Prepare HttpRequest
        HttpRequest httpRequest = new HttpRequest(urlForTest+"/path");
        httpRequest.setHttpMode(HttpMode.GET);
        httpRequest.setValidResponseContent("Hello World");

        // Run build
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(httpRequest);

        Logger LOGGER = Logger.getLogger(JenkinsLocationConfiguration.class.getName());
        System.out.println(JenkinsLocationConfiguration.class.getName());
        System.out.println("LOGGER: "+LOGGER);
        System.out.println("LOGGER: "+LOGGER.getLevel());
        LOGGER.log(Level.WARNING, "Show this");
        LOGGER.setFilter(new MyFilter());
        LOGGER.log(Level.WARNING, "Hide this");
        Handler[] handlers = LOGGER.getHandlers();
        for(Handler handler : handlers) {
            System.out.println("handler: "+handler);
            //globalLogger.removeHandler(handler);
        }

        // Run the Jenkins job, this issues the request
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        // We expect this Jenkins job to succeed
        j.assertBuildStatusSuccess(build);

        // More verification and debug
        String s = FileUtils.readFileToString(build.getLogFile());
        System.out.println(s);
        Pattern p = Pattern.compile("HttpMode: GET");
        Matcher m = p.matcher(s);
        assertTrue(m.find());
    }
}
