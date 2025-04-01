package jenkins.plugins.http_request;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.model.Result;
import jenkins.plugins.http_request.util.HttpRequestNameValuePair;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Test for HttpRequest with headers that contain special characters
 */
public class HttpRequestStepSpecialHeadersTest {

    @Rule
    public final JenkinsRule j = new JenkinsRule();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    /**
     * Test that we can use headers with special characters in their names
     */
    @Test
    public void testSpecialCharactersInHeaderNamesUsingMap() throws Exception {
        // Create a test pipeline that uses the new setHeadersMap method
        String script = "node {\n" +
                "  def headerMap = ['my-custom-header-with-hyphen-separator': 'test-value']\n" +
                "  def response = httpRequest url: 'http://example.com', headersMap: headerMap\n" +
                "  echo \"Response: ${response.content}\"\n" +
                "}";

        WorkflowJob project = j.createProject(WorkflowJob.class);
        project.setDefinition(new CpsFlowDefinition(script, true));
        
        // The actual HTTP request to example.com actually works in the test environment
        WorkflowRun run = project.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(run);
        j.assertLogContains("my-custom-header-with-hyphen-separator: test-value", run);
    }
    
    /**
     * Test that we can create headers with special characters using the static factory method
     */
    @Test
    public void testSpecialCharactersInHeaderNamesUsingCreate() throws Exception {
        // Test the static factory method create()
        HttpRequestNameValuePair header = HttpRequestNameValuePair.create(
                "my-custom-header-with-hyphen-separator", 
                "test-value", 
                false);
        
        assertEquals("my-custom-header-with-hyphen-separator", header.getName());
        assertEquals("test-value", header.getValue());
        assertEquals(false, header.getMaskValue());
    }
    
    /**
     * Test that we can create headers with special characters using the Map.Entry constructor
     */
    @Test
    public void testSpecialCharactersInHeaderNamesUsingMapEntry() throws Exception {
        // Create a map with a header that contains special characters
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("my-custom-header-with-hyphen-separator", "test-value");
        
        // Create a header using the entry from the map
        Map.Entry<String, String> entry = headerMap.entrySet().iterator().next();
        HttpRequestNameValuePair header = new HttpRequestNameValuePair(entry);
        
        assertEquals("my-custom-header-with-hyphen-separator", header.getName());
        assertEquals("test-value", header.getValue());
    }
} 