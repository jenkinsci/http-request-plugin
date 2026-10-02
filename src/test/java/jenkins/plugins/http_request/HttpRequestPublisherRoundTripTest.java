package jenkins.plugins.http_request;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import hudson.model.FreeStyleProject;

import jenkins.plugins.http_request.util.HttpRequestNameValuePair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link HttpRequestPublisher} survives a configuration round trip, and that the
 * outbound-only HTTP-mode allowlist ({@link HttpMode#SUPPORTED_HTTP_MODES}) is enforced at
 * configuration time via the "HTTP mode" dropdown rather than by rejecting a save or a build.
 * @author James Boylan
 */
@WithJenkins
class HttpRequestPublisherRoundTripTest {
	List<HttpMode> httpModes = Arrays.asList(HttpMode.POST, HttpMode.PUT, HttpMode.DELETE, HttpMode.PATCH);

    @Test
    void configRoundTrip(JenkinsRule j) throws Exception {

		for (HttpMode mode : httpModes) {
			HttpRequestPublisher reloaded = newRequest(j, mode);

			assertEquals("http://domain/", reloaded.getUrl());
			assertEquals(mode, reloaded.getHttpMode());
			assertEquals("100:599", reloaded.getValidResponseCodes());
			assertEquals("some content we want to see", reloaded.getValidResponseContent());
			assertEquals(MimeType.TEXT_HTML, reloaded.getAcceptType());
			assertEquals(MimeType.TEXT_HTML, reloaded.getContentType());
			assertEquals("myfile.txt", reloaded.getOutputFile());
			assertEquals(12, reloaded.getTimeout());
			assertEquals(true, reloaded.getConsoleLogResponseBody());
			assertEquals(1, reloaded.getCustomHeaders().size());
			assertEquals("param1", reloaded.getCustomHeaders().get(0).getName());
			assertEquals("value1", reloaded.getCustomHeaders().get(0).getValue());
		}
	}

	private static HttpRequestPublisher newRequest(JenkinsRule j, HttpMode mode) throws Exception {
		HttpRequestPublisher publisher = new HttpRequestPublisher("http://domain/");
		publisher.setHttpMode(mode);
        publisher.setValidResponseCodes("100:599");
        publisher.setValidResponseContent("some content we want to see");
        publisher.setAcceptType(MimeType.TEXT_HTML);
        publisher.setContentType(MimeType.TEXT_HTML);
        publisher.setOutputFile("myfile.txt");
        publisher.setTimeout(12);
        publisher.setConsoleLogResponseBody(true);
        List<HttpRequestNameValuePair> customHeaders = new ArrayList<>();
        customHeaders.add(new HttpRequestNameValuePair("param1", "value1"));
        publisher.setCustomHeaders(customHeaders);

        FreeStyleProject project = j.createFreeStyleProject();
        project.getPublishersList().add(publisher);
        j.configRoundtrip(project);

        HttpRequestPublisher reloaded = project.getPublishersList().get(HttpRequestPublisher.class);
        assertNotNull(reloaded);
		return reloaded;
    }

    /**
     * The "HTTP mode" dropdown only offers {@link HttpMode#SUPPORTED_HTTP_MODES}
     * ({@link HttpMode#getPublisherFillItems}), so a publisher persisted with a mode outside that
     * set (e.g. GET, the field default) has no matching option to preselect. Saving the config page
     * as-is therefore submits whatever mode the browser defaults an unselected {@code <select>} to -
     * the first offered option - rather than failing; there is no runtime or save-time rejection.
     */
    @Test
    void modeOutsideDropdownIsCoercedToFirstSupportedModeOnSave(JenkinsRule j) throws Exception {
        HttpRequestPublisher publisher = new HttpRequestPublisher("http://domain/");
        publisher.setHttpMode(HttpMode.GET);
        FreeStyleProject project = j.createFreeStyleProject();
        project.getPublishersList().add(publisher);

        j.configRoundtrip(project);

        HttpRequestPublisher reloaded = project.getPublishersList().get(HttpRequestPublisher.class);
        assertNotNull(reloaded);
        assertTrue(HttpMode.SUPPORTED_HTTP_MODES.contains(reloaded.getHttpMode()));
        assertEquals(HttpMode.SUPPORTED_HTTP_MODES.iterator().next(), reloaded.getHttpMode());
    }

    @Test
    void onlyOutboundIsSupported() {
		for (HttpMode mode : httpModes) {
			assertTrue(HttpMode.SUPPORTED_HTTP_MODES.contains(mode));
        }
        assertEquals(4, HttpMode.SUPPORTED_HTTP_MODES.size());
    }
}
