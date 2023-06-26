package jenkins.plugins.http_request;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Fingerprint;
import hudson.model.Result;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import jenkins.model.Jenkins;

import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import org.htmlunit.html.HtmlPage;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Mark Waite
 */
public class HttpRequestStepCredentialsTest extends HttpRequestTestBase {
    // For developers: set to `true` so that pipeline console logs show
    // up in System.out (and/or System.err) of the plugin test run by
    //   mvn test -Dtest="HttpRequestStepCredentialsTest"
    private boolean verbosePipelines = false;
    String getLogAsStringPlaintext(WorkflowRun f) throws java.io.IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        f.getLogText().writeLogTo(0, baos);
        return baos.toString();
    }

    // From CertificateCredentialImplTest
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    private File p12simple;
    private File p12trusted;

    private StandardCredentials getInvalidCredential() {
        String username = "bad-user";
        String password = "bad-password";
        CredentialsScope scope = CredentialsScope.GLOBAL;
        String id = "username-" + username + "-password-" + password;
        return new UsernamePasswordCredentialsImpl(scope, id, "desc: " + id, username, password);
    }

    private StandardCredentials getCertificateCredentialSimple() throws IOException {
        if (p12simple == null) {
            // Contains a private key + openvpn certs,
            // as alias named "1" (according to keytool)
            p12simple = tmp.newFile("test.p12");
            FileUtils.copyURLToFile(HttpRequestStepCredentialsTest.class.getResource("test.p12"), p12simple);
        }

        SecretBytes uploadedKeystore = SecretBytes.fromBytes(Files.readAllBytes(p12simple.toPath()));
        CertificateCredentialsImpl.UploadedKeyStoreSource storeSource = new CertificateCredentialsImpl.UploadedKeyStoreSource(uploadedKeystore);
        return new CertificateCredentialsImpl(null, "cred_cert_simple", null, "password", storeSource);
    }

    private StandardCredentials getCertificateCredentialTrusted() throws IOException {
        if (p12trusted == null) {
            // Contains a private key + openvpn certs as alias named "1",
            // and another alias named "ca" with trustedKeyEntry for CA
            p12trusted = tmp.newFile("testTrusted.p12");
            FileUtils.copyURLToFile(HttpRequestStepCredentialsTest.class.getResource("testTrusted.p12"), p12trusted);
        }

        SecretBytes uploadedKeystore = SecretBytes.fromBytes(Files.readAllBytes(p12trusted.toPath()));
        CertificateCredentialsImpl.UploadedKeyStoreSource storeSource = new CertificateCredentialsImpl.UploadedKeyStoreSource(uploadedKeystore);
        return new CertificateCredentialsImpl(null, "cred_cert_with_ca", null, "password", storeSource);
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

    // A set of tests with certificate credentials in different contexts
    // TODO: Test on remote agent as in https://github.com/jenkinsci/credentials-plugin/pull/391
    //  but this requires that PR to be merged first, so credentials-plugin
    //  processes snapshot() and readable keystore data gets to remote agent.
    // Note that the tests below focus on ability of the plugin to load and
    // process the key store specified by the credential, rather than that
    // it is usable further. It would be a separate effort to mock up a web
    // server protected by HTTPS and using certificates for login (possibly
    // user and server backed by two different CA's), and query that.
    String cpsScriptCredentialTestHttpRequest(String id, String runnerTag) {
        // Note: we accept any outcome (for the plugin, unresolved host is HTTP-404)
        // but it may not crash making use of the credential
        // Note: cases withLocalCertLookup also need cpsScriptCredentialTestImports()
        return  "def authentication='" + id + "';\n"
                + "\n"
                + "echo \"Querying HTTPS with credential...\"\n"
                + "def response = httpRequest(url: 'https://github.xcom/api/v3',\n"
                + "                 httpMode: 'GET',\n"
                + "                 authentication: authentication,\n"
                + "                 consoleLogResponseBody: true,\n"
                + "                 contentType : 'APPLICATION_FORM',\n"
                + "                 validResponseCodes: '100:599',\n"
                + "                 quiet: false)\n"
                + "println('First HTTP Request Plugin Status: '+ response.getStatus())\n"
                + "println('First HTTP Request Plugin Response: '+ response.getContent())\n"
                + "\n"
                + "echo \"Querying HTTPS with credential again (reentrability)...\"\n"
                + "response = httpRequest(url: 'https://github.xcom/api/v3',\n"
                + "                 httpMode: 'GET',\n"
                + "                 authentication: authentication,\n"
                + "                 consoleLogResponseBody: true,\n"
                + "                 contentType : 'APPLICATION_FORM',\n"
                + "                 validResponseCodes: '100:599',\n"
                + "                 quiet: false)\n"
                + "println('Second HTTP Request Plugin Status: '+ response.getStatus())\n"
                + "println('Second HTTP Request Plugin Response: '+ response.getContent())\n"
                + "\n";
    }

    @Test
    @Issue({"JENKINS-70000", "JENKINS-70101"})
    public void testCertSimpleHttpRequestOnController() throws Exception {
        // Check that credentials are usable with pipeline script
        // running without a node{}
        StandardCredentials credential = getCertificateCredentialSimple();
        store.addCredentials(Domain.global(), credential);

        // Configure the build to use the credential
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        String script =
                cpsScriptCredentialTestHttpRequest("cred_cert_simple", "CONTROLLER BUILT-IN");
        proj.setDefinition(new CpsFlowDefinition(script, false));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();
        if (verbosePipelines) System.out.println(getLogAsStringPlaintext(run));

        // Check expectations
        j.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        j.assertLogContains("HTTP Request Plugin Response: ", run);
        j.assertLogContains("Using authentication: cred_cert_simple", run);
        // Currently we always try adding the material
        // and report if not failed trying (might have
        // had 0 entries to add though):
        //j.assertLogNotContains("Added Trust Material from provided KeyStore", run);
        j.assertLogContains("Added Key Material from provided KeyStore", run);
        j.assertLogContains("Treating UnknownHostException", run);
    }

    @Test
    @Issue({"JENKINS-70000", "JENKINS-70101"})
    public void testCertSimpleHttpRequestOnNodeLocal() throws Exception {
        // Check that credentials are usable with pipeline script
        // running on a node{} (provided by the controller)
        StandardCredentials credential = getCertificateCredentialSimple();
        store.addCredentials(Domain.global(), credential);

        // Configure the build to use the credential
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        String script =
                "node {\n" +
                cpsScriptCredentialTestHttpRequest("cred_cert_simple", "CONTROLLER NODE") +
                "}\n";
        proj.setDefinition(new CpsFlowDefinition(script, false));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();
        if (verbosePipelines) System.out.println(getLogAsStringPlaintext(run));

        // Check expectations
        j.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        j.assertLogContains("HTTP Request Plugin Response: ", run);
        j.assertLogContains("Using authentication: cred_cert_simple", run);
        // Currently we always try adding the material
        // and report if not failed trying (might have
        // had 0 entries to add though):
        //j.assertLogNotContains("Added Trust Material from provided KeyStore", run);
        j.assertLogContains("Added Key Material from provided KeyStore", run);
        j.assertLogContains("Treating UnknownHostException", run);
    }

    @Test
    @Issue({"JENKINS-70000", "JENKINS-70101"})
    public void testCertTrustedHttpRequestOnController() throws Exception {
        // Check that credentials are usable with pipeline script
        // running without a node{}
        StandardCredentials credential = getCertificateCredentialTrusted();
        store.addCredentials(Domain.global(), credential);

        // Configure the build to use the credential
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        String script =
                cpsScriptCredentialTestHttpRequest("cred_cert_with_ca", "CONTROLLER BUILT-IN");
        proj.setDefinition(new CpsFlowDefinition(script, false));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();
        if (verbosePipelines) System.out.println(getLogAsStringPlaintext(run));

        // Check expectations
        j.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        j.assertLogContains("HTTP Request Plugin Response: ", run);
        j.assertLogContains("Using authentication: cred_cert_with_ca", run);
        j.assertLogContains("Added Trust Material from provided KeyStore", run);
        j.assertLogContains("Added Key Material from provided KeyStore", run);
        j.assertLogContains("Treating UnknownHostException", run);
    }

    @Test
    @Issue({"JENKINS-70000", "JENKINS-70101"})
    public void testCertTrustedHttpRequestOnNodeLocal() throws Exception {
        // Check that credentials are usable with pipeline script
        // running on a node{} (provided by the controller)
        StandardCredentials credential = getCertificateCredentialTrusted();
        store.addCredentials(Domain.global(), credential);

        // Configure the build to use the credential
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        String script =
                "node {\n" +
                cpsScriptCredentialTestHttpRequest("cred_cert_with_ca", "CONTROLLER NODE") +
                "}\n";
        proj.setDefinition(new CpsFlowDefinition(script, false));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();
        if (verbosePipelines) System.out.println(getLogAsStringPlaintext(run));

        // Check expectations
        j.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        j.assertLogContains("HTTP Request Plugin Response: ", run);
        j.assertLogContains("Using authentication: cred_cert_with_ca", run);
        j.assertLogContains("Added Trust Material from provided KeyStore", run);
        j.assertLogContains("Added Key Material from provided KeyStore", run);
        j.assertLogContains("Treating UnknownHostException", run);
    }

}
