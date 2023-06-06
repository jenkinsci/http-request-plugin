package jenkins.plugins.http_request;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Fingerprint;
import hudson.model.Result;
import java.util.Collections;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import org.htmlunit.html.HtmlPage;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Mark Waite
 */
public class HttpRequestStepCredentialsTest extends HttpRequestTestBase {

    private StandardCredentials getInvalidCredential() {
        String username = "bad-user";
        String password = "bad-password";
        CredentialsScope scope = CredentialsScope.GLOBAL;
        String id = "username-" + username + "-password-" + password;
        return new UsernamePasswordCredentialsImpl(scope, id, "desc: " + id, username, password);
    }

    private CredentialsStore store = null;

    @Before
    public void enableSystemCredentialsProvider() throws Exception {
        SystemCredentialsProvider.getInstance()
                .setDomainCredentialsMap(
                        Collections.singletonMap(Domain.global(), Collections.emptyList()));
        for (CredentialsStore s : CredentialsProvider.lookupStores(Jenkins.get())) {
            if (s.getProvider() instanceof SystemCredentialsProvider.ProviderImpl) {
                store = s;
                break;
            }
        }
        assertThat("The system credentials provider is enabled", store, notNullValue());
    }

    @Test
    public void trackCredentials() throws Exception {
        StandardCredentials credential = getInvalidCredential();
        store.addCredentials(Domain.global(), credential);

        Fingerprint fingerprint = CredentialsProvider.getFingerprintOf(credential);
        assertThat("Fingerprint should not be set before job definition", fingerprint, nullValue());

        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo("credentials/store/system/domain/_/credentials/" + credential.getId());
        assertThat("Have usage tracking reported", page.getElementById("usage"), notNullValue());
        assertThat(
                "No fingerprint created until first use on missing page",
                page.getElementById("usage-missing"),
                notNullValue());
        assertThat(
                "No fingerprint created until first use on present page",
                page.getElementById("usage-present"),
                nullValue());

        // Configure the build to use the credential
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        proj.setDefinition(
                new CpsFlowDefinition(
                        "def response = httpRequest(url: 'https://api.github.com/users/jenkinsci',\n"
                                + "                 authentication: '" + credential.getId() + "')\n"
                                + "println('Status: '+response.getStatus())\n"
                                + "println('Response: '+response.getContent())\n",
                        true));

        fingerprint = CredentialsProvider.getFingerprintOf(credential);
        assertThat("Fingerprint should not be set before first build", fingerprint, nullValue());

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();

        // Check expectations
        j.assertBuildStatus(Result.SUCCESS, run);
        j.assertLogContains("https://api.github.com/users/jenkinsci/followers", run);

        // Check the credential use was correctly tracked
        fingerprint = CredentialsProvider.getFingerprintOf(credential);
        assertThat("Fingerprint should be set after first build", fingerprint, notNullValue());
        assertThat(fingerprint.getJobs(), hasItem(is(proj.getFullName())));
        Fingerprint.RangeSet rangeSet = fingerprint.getRangeSet(proj);
        assertThat(rangeSet, notNullValue());
        assertThat(rangeSet.includes(proj.getLastBuild().getNumber()), is(true));

        page = wc.goTo("credentials/store/system/domain/_/credentials/" + credential.getId());
        assertThat(page.getElementById("usage-missing"), nullValue());
        assertThat(page.getElementById("usage-present"), notNullValue());
        assertThat(page.getAnchorByText(proj.getFullDisplayName()), notNullValue());
    }
}
