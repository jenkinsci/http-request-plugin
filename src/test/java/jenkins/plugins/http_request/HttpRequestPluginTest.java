package jenkins.plugins.http_request;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;

@WithJenkins
class HttpRequestPluginTest {

	@RegisterExtension
	private static final WireMockExtension WIRE_MOCK_EXTENSION = WireMockExtension.newInstance().build();

	private JenkinsRule r;

	@BeforeEach
	void beforeEach(JenkinsRule rule) {
		r = rule;
	}

	@Test
	@Issue("JENKINS-76353")
	void test() throws Exception {
		String payload = "Some Random String";

		WIRE_MOCK_EXTENSION.stubFor(get(urlMatching("/JENKINS-76353"))
				.willReturn(aResponse()
						.withStatus(200)
						.withStatusMessage("OK")
						.withHeader("date", "Fri, 23 Jan 2026 15:30:38 GMT")
						.withHeader("vary", "Accept-Encoding")
						.withHeader("content-disposition", "attachment; filename=\"file.txt\"; size=" + payload.length())
						.withHeader("etag", "\"a21bcba577f439d703fbe1561f77213bedf7a019616fae65d9a76d2b3687773b\"")
						.withHeader("content-encoding", "none")
						.withHeader("content-type", "application/octet-stream")
						.withHeader("cache-control", "no-store, no-cache")
						.withHeader("pragma", "no-cache")
						.withHeader("content-length", String.valueOf(payload.length()))
						.withHeader("strict-transport-security", "max-age=31536000;includeSubDomains")
						.withBody(payload)
				));

		WorkflowJob project = r.createProject(WorkflowJob.class);
		project.setDefinition(new CpsFlowDefinition(
				"""
						httpRequest(
						    url: "%s/JENKINS-76353"
						)
						""".formatted(WIRE_MOCK_EXTENSION.baseUrl()),
				true));
		r.assertBuildStatusSuccess(project.scheduleBuild2(0));

		WIRE_MOCK_EXTENSION.verify(getRequestedFor(urlMatching("/JENKINS-76353")));
	}
}
