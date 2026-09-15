package jenkins.plugins.http_request;

import static jenkins.plugins.http_request.Registers.registerCheckRequestBody;
import static jenkins.plugins.http_request.Registers.registerCustomHeaders;
import static jenkins.plugins.http_request.Registers.registerRequestChecker;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.util.ListBoxModel;

import jenkins.plugins.http_request.util.HttpRequestNameValuePair;

/**
 * Exercises {@link HttpRequestPublisher} as a standalone post-build action, mirroring the
 * build-step coverage in {@link HttpRequestTest} but wiring the request through
 * {@link FreeStyleProject#getPublishersList()}.
 *
 * <p>{@link HttpRequestPublisher#perform} does not reject any {@link HttpMode} at run time. The
 * outbound-only allowlist ({@link HttpMode#SUPPORTED_HTTP_MODES}) is enforced purely at
 * configuration time, by restricting the "HTTP mode" dropdown
 * ({@link HttpRequestPublisher.DescriptorImpl#doFillHttpModeItems}) to those modes. A publisher
 * configured with any other mode - including the GET default - still performs the request.
 *
 * @author James Boylan
 */
@WithJenkins
class HttpRequestFreestylePublisherTest extends HttpRequestPublisherTestBase {

    /**
     * Adds {@code publisher} as a post-build action and runs the build to completion.
     */
    private FreeStyleBuild runPublisher(HttpRequestPublisher publisher) throws Exception {
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.getPublishersList().add(publisher);
        return project.scheduleBuild2(0).get();
    }

    // ---------------------------------------------------------------------------------------------
    // Outbound HTTP modes offered by the dropdown: POST, PUT, DELETE, PATCH
    // ---------------------------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.INCLUDE, names = {"POST", "PUT", "DELETE", "PATCH"})
    void supportedModePerformsRequest(HttpMode mode) throws Exception {
        // Prepare the server
        registerRequestChecker(mode);

        // Prepare HttpRequestPublisher
        HttpRequestPublisher publisher = new HttpRequestPublisher(baseURL() + "/do" + mode.name());
        publisher.setHttpMode(mode);
        publisher.setConsoleLogResponseBody(true);

        // Run build (as a post-build action)
        FreeStyleBuild build = runPublisher(publisher);

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogContains(ALL_IS_WELL, build);
        this.j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", build);
    }

    /**
     * The default {@link HttpMode} (GET) is not offered by the dropdown, but {@code perform} has no
     * runtime allowlist check: a publisher left at defaults still performs the request.
     */
    @Test
    void defaultModePerformsGetRequest() throws Exception {
        // Prepare the server
        registerRequestChecker(HttpMode.GET);

        // Prepare HttpRequestPublisher (httpMode left at its GET default)
        HttpRequestPublisher publisher = new HttpRequestPublisher(baseURL() + "/doGET");
        publisher.setConsoleLogResponseBody(true);

        // Run build (as a post-build action)
        FreeStyleBuild build = runPublisher(publisher);

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogContains(ALL_IS_WELL, build);
        this.j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", build);
    }

    // ---------------------------------------------------------------------------------------------
    // Full request handling from the publisher context
    // ---------------------------------------------------------------------------------------------

    @Test
    void passesRequestBodyOnPost() throws Exception {
        // Prepare the server
        registerCheckRequestBody();

        // Prepare HttpRequestPublisher
        HttpRequestPublisher publisher = new HttpRequestPublisher(baseURL() + "/checkRequestBody");
        publisher.setHttpMode(HttpMode.POST);
        publisher.setRequestBody("TestRequestBody");
        publisher.setConsoleLogResponseBody(true);

        // Run build (as a post-build action)
        FreeStyleBuild build = runPublisher(publisher);

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogContains(ALL_IS_WELL, build);
        this.j.assertLogContains("Success: Status code 200 is in the accepted range: 100:399", build);
    }

    @Test
    void sendsCustomHeaders() throws Exception {
        // Prepare the server (the /customHeaders checker only answers GET)
        registerCustomHeaders();

        List<HttpRequestNameValuePair> customHeaders = new ArrayList<>();
        customHeaders.add(new HttpRequestNameValuePair("customHeader", "value1"));
        customHeaders.add(new HttpRequestNameValuePair("customHeader", "value2"));

        HttpRequestPublisher publisher = new HttpRequestPublisher(baseURL() + "/customHeaders");
        publisher.setConsoleLogResponseBody(true);
        publisher.setCustomHeaders(customHeaders);

        // Run build (as a post-build action)
        FreeStyleBuild build = runPublisher(publisher);

        // Check expectations
        this.j.assertBuildStatusSuccess(build);
        this.j.assertLogContains(ALL_IS_WELL, build);
    }

    @Test
    void badResponseContentFailsTheBuild() throws Exception {
        // Prepare the server
        registerRequestChecker(HttpMode.POST);

        // Prepare HttpRequestPublisher
        HttpRequestPublisher publisher = new HttpRequestPublisher(baseURL() + "/doPOST");
        publisher.setHttpMode(HttpMode.POST);
        publisher.setConsoleLogResponseBody(true);
        publisher.setValidResponseContent("bad content");

        // Run build (as a post-build action)
        FreeStyleBuild build = runPublisher(publisher);

        // Check expectations
        this.j.assertBuildStatus(Result.FAILURE, build);
        this.j.assertLogContains("Fail: Response doesn't contain expected content 'bad content'", build);
    }

    // ---------------------------------------------------------------------------------------------
    // Configuration-time allowlist: the dropdown only offers the outbound HTTP modes
    // ---------------------------------------------------------------------------------------------

    @Test
    void httpModeDropdownOnlyOffersOutboundModes() {
        HttpRequestPublisher.DescriptorImpl descriptor =
                this.j.jenkins.getDescriptorByType(HttpRequestPublisher.DescriptorImpl.class);

        Set<String> offered = new HashSet<>();
        for (ListBoxModel.Option option : descriptor.doFillHttpModeItems()) {
            offered.add(option.value);
        }

        Set<String> expected = HttpMode.SUPPORTED_HTTP_MODES.stream()
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertEquals(expected, offered);
    }
}
