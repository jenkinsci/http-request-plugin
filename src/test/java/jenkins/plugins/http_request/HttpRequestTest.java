package jenkins.plugins.http_request;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.apache.commons.io.FileUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThat;

import org.junit.Test;
import org.junit.Rule;

import org.jvnet.hudson.test.JenkinsRule;

public class HttpRequestTest {

    HttpRequest httpRequest;

    // Note: The JenkinsRule is required, otherwise the call to getDescriptor causes a null pointer exception
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void defaultValues() throws Exception {
        HttpRequest httpRequest = new HttpRequest("http://www.domain/", HttpMode.GET, "",
            MimeType.NOT_SET, MimeType.NOT_SET,
            "", null, false, false, null, 0, "", "");
        assertEquals(httpRequest.getDescriptor().getDefaultHttpMode(),HttpMode.POST);
        assertTrue(httpRequest.getDescriptor().getDefaultLogResponseBody());
        assertTrue(httpRequest.getDescriptor().isDefaultReturnCodeBuildRelevant());
    }

   @Test
   public void defaultSettingsTest() throws Exception {
       FreeStyleProject project = j.createFreeStyleProject();
       project.getBuildersList().add(new HttpRequest(j.getURL().toString()+"api/json", HttpMode.GET, "",
           MimeType.NOT_SET, MimeType.NOT_SET,
           "", null, true, false, null, 0, "", ""));
       FreeStyleBuild build = project.scheduleBuild2(0).get();
       j.assertBuildStatusSuccess(build);
       String s = FileUtils.readFileToString(build.getLogFile());
       Pattern p = Pattern.compile("HttpMode: GET");
       Matcher m = p.matcher(s);
       assertTrue(m.find());
   }

   @Test
   public void detectActualContent() throws Exception {
       String findMe = "\"views\":[{\"name\":\"All\",\"url\":\"http://localhost:";
       String findMePattern = Pattern.quote(findMe);
       FreeStyleProject project = j.createFreeStyleProject();
       project.getBuildersList().add(new HttpRequest(j.getURL().toString()+"api/json", HttpMode.GET, "",
           MimeType.NOT_SET, MimeType.NOT_SET,
           "", null, true, false, null, 0, "", findMe));
       FreeStyleBuild build = project.scheduleBuild2(0).get();
       j.assertBuildStatusSuccess(build);
       String s = FileUtils.readFileToString(build.getLogFile());
       Pattern p = Pattern.compile(findMePattern);
       Matcher m = p.matcher(s);
       assertTrue(m.find());
   }

   @Test
   public void detectBadContent() throws Exception {
       FreeStyleProject project = j.createFreeStyleProject();
       project.getBuildersList().add(new HttpRequest(j.getURL().toString()+"api/json", HttpMode.GET, "",
           MimeType.NOT_SET, MimeType.NOT_SET,
           "", null, true, false, null, 0, "", "bad content"));
       FreeStyleBuild build = project.scheduleBuild2(0).get();
       j.assertBuildStatus(Result.FAILURE, build);
       String s = FileUtils.readFileToString(build.getLogFile());
       assertThat(s, org.hamcrest.CoreMatchers.containsString("Build step 'HTTP Request' marked build as failure"));
       Pattern p = Pattern.compile("Fail: Response with length \\d+ doesn't contain 'bad content'");
       Matcher m = p.matcher(s);
       assertTrue(m.find());
   }

   @Test
   public void expectMimeType() throws Exception {
       FreeStyleProject project = j.createFreeStyleProject();
       project.getBuildersList().add(new HttpRequest(j.getURL().toString()+"api/json", HttpMode.GET, "",
           MimeType.NOT_SET, MimeType.APPLICATION_JSON,
           "", null, true, false, null, 0, "", ""));
       FreeStyleBuild build = project.scheduleBuild2(0).get();
       j.assertBuildStatusSuccess(build);
       String s = FileUtils.readFileToString(build.getLogFile());
       System.out.println(s);
       Pattern p = Pattern.compile("HttpMode: GET");
       Matcher m = p.matcher(s);
       assertTrue(m.find());
   }

   @Test
   public void expectBadMimeType() throws Exception {
       FreeStyleProject project = j.createFreeStyleProject();
       project.getBuildersList().add(new HttpRequest(j.getURL().toString()+"api/json", HttpMode.GET, "",
           MimeType.NOT_SET, MimeType.TEXT_HTML,
           "", null, true, false, null, 0, "", ""));
       FreeStyleBuild build = project.scheduleBuild2(0).get();
       j.assertBuildStatusSuccess(build);
       String s = FileUtils.readFileToString(build.getLogFile());
       System.out.println(s);
       //assertThat(s, org.hamcrest.CoreMatchers.containsString("Build step 'HTTP Request' marked build as failure"));
       Pattern p = Pattern.compile("HttpMode: GET");
       Matcher m = p.matcher(s);
       assertTrue(m.find());
   }
}
