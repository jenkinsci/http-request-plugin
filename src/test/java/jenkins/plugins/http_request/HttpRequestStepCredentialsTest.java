package jenkins.plugins.http_request;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Fingerprint;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Result;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;

import hudson.model.Slave;
import jenkins.model.Jenkins;

import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * The HttpRequestStepCredentialsTest suite prepares pipeline scripts to
 * retrieve some previously saved credentials, on the controller,
 * on a node provided by it, and on a worker agent in separate JVM.
 * This picks known-working test cases and their setup from other
 * test classes which address those credential types in more detail.<br/>
 *
 * Initially tied to JENKINS-70101 research, and tests on remote agent
 * would require
 * <a href="https://github.com/jenkinsci/credentials-plugin/pull/391"> PR credentials-plugin#391</a>
 * to be merged first, so credentials-plugin processes {@code snapshot()}
 * properly and readable keystore data gets to remote agent.<br/>
 *
 * So part of this is a mixed test suite of two plugins, making sure that
 * the chosen versions do cooperate correctly for our ultimate needs.<br/>
 *
 * @author Mark Waite
 * @author Jim Klimov
 */
@WithJenkins
class HttpRequestStepCredentialsTest extends HttpRequestTestBase {
    /** For developers: set to `true` so that pipeline console logs show
     * up in {@link System#out} (and/or {@link System#err}) of the plugin
     * test run executed by:
     * <pre>
     *   mvn test -Dtest="HttpRequestStepCredentialsTest"
     * </pre>
     */
    private final boolean verbosePipelines = false;

    // Data for build agent setup
    /** Build agent label expected by test cases for remote logic execution
     * and data transfer [JENKINS-70101] */
    private final static String agentLabelString = "cred-test-worker";
    // Can this be reused for many test cases?
    private Slave agent = null;
    /** Tri-state Unknown/started/not usable [JENKINS-70101] */
    private Boolean agentUsable = null;

    // From CertificateCredentialImplTest in credentials-plugin
    /** Temporary location for keystore files.
     * @see #p12simple
     * @see #p12trusted
     */
    @TempDir
    private File tmp;

    /** A temporary (randomly-named) file with a PKCS#12 key/cert store which
     *  contains a private key + openvpn certs, as alias named "1"
     *  (according to keytool).
     */
    private File p12simple;

    /** A temporary (randomly-named) file with a PKCS#12 key/cert store which
     *  contains a private key + openvpn certs as alias named "1",
     *  and another alias named "ca" with trustedKeyEntry for CA
     *  (according to keytool).
     */
    private File p12trusted;

    /** Reference to the system credentials provider, prepared by
     *  {@link #enableSystemCredentialsProvider} method
     *  before each test case.
     */
    private CredentialsStore store = null;

    /** True if we can use remote agent tests, and the credentials plugin version
     *  here is expected to transfer secret data across the Channel correctly
     *  (assuming issue JENKINS-70101 is fixed in that plugin).
     */
    static private Boolean credentialsPluginDoesSnapshotsRight = null;
    static {
        try {
            Class.forName(
                    "com.cloudbees.plugins.credentials.impl.CertificateCredentialsSnapshotTaker"
                    // , false, HttpRequestStepCredentialsTest.class.getClassLoader()
                    );
            credentialsPluginDoesSnapshotsRight = true;
        } catch (ClassNotFoundException ignored) {
            credentialsPluginDoesSnapshotsRight = false;
        } catch (ExceptionInInitializerError ignored) {
            // Per https://www.baeldung.com/java-check-class-exists the Class.forName()
            // calls a static initializer which may fail (at least for the default
            // single-argument version of the method), but still -- if we get that far,
            // the class exists so we are probably running the version of plugin with
            // https://github.com/jenkinsci/credentials-plugin/pull/391
            credentialsPluginDoesSnapshotsRight = true;
        }
    }
    /** Honour the check via {@link #credentialsPluginDoesSnapshotsRight} [false],
     *  or try (and possibly fail) with any implementation/version of the
     *  credentials plugin [true]?
     */
    private Boolean credentialsPluginTestRemoteAlways = false;

    private static StandardCredentials getInvalidCredential() throws FormException {
        String username = "bad-user";
        String password = "bad-password";
        CredentialsScope scope = CredentialsScope.GLOBAL;
        String id = "username-" + username + "-password-" + password;
        return new UsernamePasswordCredentialsImpl(scope, id, "desc: " + id, username, password);
    }

    private StandardCredentials getCertificateCredentialSimple() throws IOException {
        return getCertificateCredentialSimple("cred_cert_simple", "password");
    }

    private StandardCredentials getCertificateCredentialSimple(String id, String password) throws IOException {
        if (p12simple == null) {
            // Contains a private key + openvpn certs,
            // as alias named "1" (according to keytool)
            p12simple = File.createTempFile("test-keystore-", ".p12", tmp);
            FileUtils.copyURLToFile(HttpRequestStepCredentialsTest.class.getResource("test.p12"), p12simple);
        }

        SecretBytes uploadedKeystore = SecretBytes.fromRawBytes(Files.readAllBytes(p12simple.toPath()));
        CertificateCredentialsImpl.UploadedKeyStoreSource storeSource = new CertificateCredentialsImpl.UploadedKeyStoreSource(null, uploadedKeystore);
        return new CertificateCredentialsImpl(null, id, null, password, storeSource);
    }

    private StandardCredentials getCertificateCredentialTrusted() throws IOException {
        return getCertificateCredentialTrusted("cred_cert_with_ca", "password");
    }

    private StandardCredentials getCertificateCredentialTrusted(String id, String password) throws IOException {
        if (p12trusted == null) {
            // Contains a private key + openvpn certs as alias named "1",
            // and another alias named "ca" with trustedKeyEntry for CA
            p12trusted = File.createTempFile("testTrusted-keystore-", ".p12", tmp);
            FileUtils.copyURLToFile(HttpRequestStepCredentialsTest.class.getResource("testTrusted.p12"), p12trusted);
        }

        SecretBytes uploadedKeystore = SecretBytes.fromRawBytes(Files.readAllBytes(p12trusted.toPath()));
        CertificateCredentialsImpl.UploadedKeyStoreSource storeSource = new CertificateCredentialsImpl.UploadedKeyStoreSource(null, uploadedKeystore);
        return new CertificateCredentialsImpl(null, id, null, password, storeSource);
    }

    /** Get a new CertificateCredentialsImpl() and save it into the {@link #store} */
    private void prepareUploadedKeystore(String id, String password, Boolean useSimple) throws IOException {
        StandardCredentials c = (useSimple
                ? getCertificateCredentialSimple(id, password)
                : getCertificateCredentialTrusted(id, password)
        );
        SystemCredentialsProvider.getInstance().getCredentials().add(c);
        SystemCredentialsProvider.getInstance().save();
    }

    private void prepareUploadedKeystore(String id, String password) throws IOException {
        prepareUploadedKeystore(id, password, true);
    }

    // Partially from certificate-plugin CertificateCredentialImplTest setup()
    private void prepareUploadedKeystore() throws IOException {
        prepareUploadedKeystore("myCert", "password");
    }

    @BeforeEach
    void enableSystemCredentialsProvider() {
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

        // Primarily needed for remote-agent tests per JENKINS-70101?
        j.jenkins.setCrumbIssuer(null);
    }

    /////////////////////////////////////////////////////////////////
    // Test cases
    /////////////////////////////////////////////////////////////////

    /** A credentials tracking test */
    @Test
    void trackCredentials() throws Exception {
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

    /////////////////////////////////////////////////////////////////
    // Helpers for pipeline tests about credentials retrievability
    // by http-request-plugin (on same or remote JVM)
    /////////////////////////////////////////////////////////////////

    private String getLogAsStringPlaintext(WorkflowRun f) throws java.io.IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        f.getLogText().writeLogTo(0, baos);
        return baos.toString();
    }

    /** Returns a String with prepared part of the pipeline script with imports used by some other snippet generators */
    private String cpsScriptCredentialTestImports() {
        return  "import com.cloudbees.plugins.credentials.CredentialsMatchers;\n" +
                "import com.cloudbees.plugins.credentials.CredentialsProvider;\n" +
                "import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;\n" +
                "import com.cloudbees.plugins.credentials.common.StandardCredentials;\n" +
                "import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;\n" +
                "import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;\n" +
                "import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl;\n" +
                "import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl.KeyStoreSource;\n" +
                "import hudson.security.ACL;\n" +
                "import java.security.KeyStore;\n" +
                "\n";
    }

    /** Returns a String with prepared part of the pipeline script with a request
     *  (to non-existent site) using a credential named by "id" parameter.<br/>
     *
     *  The test class property {@link #verbosePipelines} can be used to toggle writing
     *  a copy of the progress message(s) to {@link System#out} and {@link System#err}
     *  of the build agent JVM.<br/>
     *
     *  Note: we accept any outcome for the HTTP request (for this plugin, unresolved
     *  host is HTTP-404) but it may not crash making use of the credential.<br/>
     *
     * @param id    Credential ID, saved earlier into the store
     * @param runnerTag Reported in pipeline build log
     * @param withReentrability If true, generate a second request with same credential,
     *                          to make sure it is not garbled etc. by first use.
     * @param withLocalCertLookup If true, add lookup and logging of keystore data
     *                           (into the pipeline build console, optionally also system streams).
     *                            Note: test cases {@code withLocalCertLookup} need to
     *                            generate {@link #cpsScriptCredentialTestImports} into
     *                            their pipelines first.
     * @return String with prepared part of pipeline script
     */
    private String cpsScriptCredentialTestHttpRequest(String id, String runnerTag, Boolean withReentrability, Boolean withLocalCertLookup) {
        return  "def authentication='" + id + "';\n"
                + "\n"
                + "def msg\n"
                + (withLocalCertLookup ? (
                        "if (true) { // scoping\n"
                        + "  msg = \"Finding credential with id='${authentication}'...\"\n"
                        + "  echo msg;" + (verbosePipelines ? " System.out.println(msg); System.err.println(msg)" : "" ) + ";\n"
                        + "  StandardCredentials credential = CredentialsMatchers.firstOrNull(\n"
                        + "    CredentialsProvider.lookupCredentials(\n"
                        + "        StandardCredentials.class,\n"
                        + "        Jenkins.instance, null, null),\n"
                        + "    CredentialsMatchers.withId(authentication));\n"
                        + "  msg = \"Getting keystore...\"\n"
                        + "  echo msg;" + (verbosePipelines ? " System.out.println(msg); System.err.println(msg)" : "" ) + ";\n"
                        + "  KeyStore keyStore = credential.getKeyStore();\n"
                        + "  msg = \"Getting keystore source...\"\n"
                        + "  echo msg;" + (verbosePipelines ? " System.out.println(msg); System.err.println(msg)" : "" ) + ";\n"
                        + "  KeyStoreSource kss = ((CertificateCredentialsImpl) credential).getKeyStoreSource();\n"
                        + "  msg = \"Getting keystore source bytes...\"\n"
                        + "  echo msg;" + (verbosePipelines ? " System.out.println(msg); System.err.println(msg)" : "" ) + ";\n"
                        + "  byte[] kssb = kss.getKeyStoreBytes();\n"
                        + "}\n" )
                : "" )
                + "\n"
                + "msg = \"Querying HTTPS with credential on " + (runnerTag != null ? runnerTag : "<unspecified node>") + "...\"\n"
                + "echo msg;" + (verbosePipelines ? " System.out.println(msg); System.err.println(msg)" : "" ) + ";\n"
                + "def response = httpRequest(url: 'https://github.xcom/api/v3',\n"
                + "                 httpMode: 'GET',\n"
                + "                 authentication: authentication,\n"
                + "                 consoleLogResponseBody: true,\n"
                + "                 contentType : 'APPLICATION_FORM',\n"
                + "                 validResponseCodes: '100:599',\n"
                + "                 quiet: false)\n"
                + "println('" + (withReentrability ? "First " : "") + "HTTP Request Plugin Status: '+ response.getStatus())\n"
                + "println('" + (withReentrability ? "First " : "") + "First HTTP Request Plugin Response: '+ response.getContent())\n"
                + "\n"
                + (withReentrability ? (
                        "msg = \"Querying HTTPS with credential again (reentrability)...\"\n"
                        + "echo msg;" + (verbosePipelines ? " System.out.println(msg); System.err.println(msg)" : "" ) + ";\n"
                        + "response = httpRequest(url: 'https://github.xcom/api/v3',\n"
                        + "                 httpMode: 'GET',\n"
                        + "                 authentication: authentication,\n"
                        + "                 consoleLogResponseBody: true,\n"
                        + "                 contentType : 'APPLICATION_FORM',\n"
                        + "                 validResponseCodes: '100:599',\n"
                        + "                 quiet: false)\n"
                        + "println('Second HTTP Request Plugin Status: '+ response.getStatus())\n"
                        + "println('Second HTTP Request Plugin Response: '+ response.getContent())\n"
                        + "\n" )
                : "" );
    }

    /** Wrapper for {@link #cpsScriptCredentialTestHttpRequest(String, String, Boolean, Boolean)}
     *  to MAYBE trace {@code withLocalCertLookup=verbosePipelines} by default */
    private String cpsScriptCredentialTestHttpRequest(String id, String runnerTag, Boolean withReentrability) {
        return cpsScriptCredentialTestHttpRequest(id, runnerTag, withReentrability, verbosePipelines);
    }

    /** Wrapper for {@link #cpsScriptCredentialTestHttpRequest(String, String, Boolean, Boolean)}
     *  to MAYBE trace {@code withLocalCertLookup=verbosePipelines}
     *   and enable {@code withReentrability=true} by default */
    private String cpsScriptCredentialTestHttpRequest(String id, String runnerTag) {
        return cpsScriptCredentialTestHttpRequest(id, runnerTag, true, verbosePipelines);
    }

    /** Wrapper for {@link #cpsScriptCredentialTestHttpRequest(String, String, Boolean, Boolean)}
     *  to use a certificate credential {@code id="myCert"} and trace {@code withLocalCertLookup=true} */
    private String cpsScriptCertCredentialTestHttpRequest(String runnerTag) {
        return cpsScriptCredentialTestHttpRequest("myCert", runnerTag, false, true);
    }

    /////////////////////////////////////////////////////////////////
    // Certificate credentials retrievability by http-request-plugin
    // in a local JVM (should work with all versions of credentials plugin)
    /////////////////////////////////////////////////////////////////

    // A set of tests with certificate credentials in different contexts
    // NOTE: Test cases on remote agent require the PR
    //  https://github.com/jenkinsci/credentials-plugin/pull/391
    //  to be merged first, so credentials-plugin processes snapshot()
    //  and readable keystore data gets to remote agent.
    // Note that the tests below focus on ability of the plugin to load and
    // process the key store specified by the credential, rather than that
    // it is usable further. It would be a separate effort to mock up a web
    // server protected by HTTPS and using certificates for login (possibly
    // user and server backed by two different CA's), and query that.

    /** Check that "simple" Certificate credentials are usable with pipeline script
     *  running without a {@code node{}} block.
     */
    @Test
    @Issue({"JENKINS-70000", "JENKINS-70101"})
    void testCertSimpleHttpRequestOnController() throws Exception {
        StandardCredentials credential = getCertificateCredentialSimple();
        store.addCredentials(Domain.global(), credential);

        // Configure the build to use the credential
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        String script =
                cpsScriptCredentialTestImports() +
                cpsScriptCredentialTestHttpRequest("cred_cert_simple", "CONTROLLER BUILT-IN", true, true);
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

    /** Check that "simple" Certificate credentials are usable with pipeline script
     *  running on a {@code node{}} (provided by the controller JVM).
     */
    @Test
    @Issue({"JENKINS-70000", "JENKINS-70101"})
    void testCertSimpleHttpRequestOnNodeLocal() throws Exception {
        StandardCredentials credential = getCertificateCredentialSimple();
        store.addCredentials(Domain.global(), credential);

        // Configure the build to use the credential
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        String script =
                cpsScriptCredentialTestImports() +
                "node {\n" +
                cpsScriptCredentialTestHttpRequest("cred_cert_simple", "CONTROLLER NODE", true, true) +
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

    /** Check that "trusted" Certificate credentials are usable with pipeline script
     *  running without a {@code node{}} block.
     */
    @Test
    @Issue({"JENKINS-70000", "JENKINS-70101"})
    void testCertTrustedHttpRequestOnController() throws Exception {
        StandardCredentials credential = getCertificateCredentialTrusted();
        store.addCredentials(Domain.global(), credential);

        // Configure the build to use the credential
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        String script =
                cpsScriptCredentialTestImports() +
                cpsScriptCredentialTestHttpRequest("cred_cert_with_ca", "CONTROLLER BUILT-IN", true, true);
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

    /** Check that "trusted" Certificate credentials are usable with pipeline script
     *  running on a {@code node{}} (provided by the controller JVM).
     */
    @Test
    @Issue({"JENKINS-70000", "JENKINS-70101"})
    void testCertTrustedHttpRequestOnNodeLocal() throws Exception {
        StandardCredentials credential = getCertificateCredentialTrusted();
        store.addCredentials(Domain.global(), credential);

        // Configure the build to use the credential
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        String script =
                cpsScriptCredentialTestImports() +
                "node {\n" +
                cpsScriptCredentialTestHttpRequest("cred_cert_with_ca", "CONTROLLER NODE", true, true) +
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

    /////////////////////////////////////////////////////////////////
    // Helpers for pipeline tests with remote agents
    /////////////////////////////////////////////////////////////////

    private Boolean isAvailableAgent() {
        // Can be used to skip optional tests if we know we could not set up an agent
        if (agent == null)
            return false;
        return agentUsable;
    }

    private Boolean setupAgent() throws OutOfMemoryError, Exception {
        if (isAvailableAgent())
            return true;

        // See how credentialsPluginTestRemoteAlways is determined above
        // and revise if the ultimately merged fix that started as
        // https://github.com/jenkinsci/credentials-plugin/pull/391
        // gets changed before the merge or later on...
        String msg_70101 = "This test needs a version of credentials-plugin with a fix for JENKINS-70101, and that does not seem to be deployed here";
        if (!credentialsPluginTestRemoteAlways)
            assumeTrue(credentialsPluginDoesSnapshotsRight, msg_70101);

        // else: credentialsPluginTestRemoteAlways, even if we fail
        if (!credentialsPluginDoesSnapshotsRight) {
            System.err.println("WARNING: " + msg_70101 + "; this test run was configured to try remote agents anyway");
            // return false;
        }

        // Note we anticipate this might fail e.g. due to system resources;
        // it should not block the whole test suite from running
        // (we would just dynamically skip certain test cases)
        try {
            // Define a "Permanent Agent"
            Label agentLabel = Label.get(agentLabelString);
            agent = j.createOnlineSlave(agentLabel);
            agent.setNodeDescription("Worker in another JVM, remoting used");
            agent.setNumExecutors(1);
            agent.setMode(Node.Mode.EXCLUSIVE);
            ///agent.setRetentionStrategy(new RetentionStrategy.Always());

/*
            // Add node envvars
            List<Entry> env = new ArrayList<Entry>();
            env.add(new Entry("key1","value1"));
            env.add(new Entry("key2","value2"));
            EnvironmentVariablesNodeProperty envPro = new EnvironmentVariablesNodeProperty(env);
            agent.getNodeProperties().add(envPro);
*/

            String agentLog = null;
            agentUsable = false;
            for (long i = 0; i < 5; i++) {
                Thread.sleep(1000);
                agentLog = agent.getComputer().getLog();
                if (i == 2 && (agentLog == null || agentLog.isEmpty())) {
                    // Give it a little time to autostart, then kick it up if needed:
                    agent.getComputer().connect(true); // "always" should have started it; avoid duplicate runs
                }
                if (agentLog != null && agentLog.contains("Agent successfully connected and online")) {
                    agentUsable = true;
                    break;
                }
            }
            System.out.println("Spawned build agent " +
                    "usability: " + agentUsable.toString() +
                    "; connection log:" + (agentLog == null ? " <null>" : "\n" + agentLog));
        } catch (Descriptor.FormException | NullPointerException e) {
            agentUsable = false;
        }

        return agentUsable;
    }

    /////////////////////////////////////////////////////////////////
    // Certificate credentials retrievability by http-request-plugin
    // in a set of local+remote JVMs (should work with versions of
    // credentials plugin where issue JENKINS-70101 is fixed)
    /////////////////////////////////////////////////////////////////

    /** Simplified version of simple/trusted tests with "myCert" credential id,
     *  transplanted from https://github.com/jenkinsci/credentials-plugin/pull/391 :
     *  Check that Certificate credentials are usable with pipeline script
     *  running without a {@code node{}} block.
     */
    @Test
    @Issue("JENKINS-70101")
    void testCertHttpRequestOnController() throws Exception {
        prepareUploadedKeystore();

        // Configure the build to use the credential
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        String script =
                cpsScriptCredentialTestImports() +
                cpsScriptCertCredentialTestHttpRequest("CONTROLLER BUILT-IN");
        proj.setDefinition(new CpsFlowDefinition(script, false));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();
        if (verbosePipelines) System.out.println(getLogAsStringPlaintext(run));

        // Check expectations
        j.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        j.assertLogContains("HTTP Request Plugin Response: ", run);
        j.assertLogContains("Using authentication: myCert", run);
    }

    /** Simplified version of simple/trusted tests with "myCert" credential id,
     *  transplanted from https://github.com/jenkinsci/credentials-plugin/pull/391 :
     *  Check that Certificate credentials are usable with pipeline script
     *  running on a {@code node{}} (provided by the controller)
     */
    @Test
    @Issue("JENKINS-70101")
    void testCertHttpRequestOnNodeLocal() throws Exception {
        prepareUploadedKeystore();

        // Configure the build to use the credential
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        String script =
                cpsScriptCredentialTestImports() +
                "node {\n" +
                cpsScriptCertCredentialTestHttpRequest("CONTROLLER NODE") +
                "}\n";
        proj.setDefinition(new CpsFlowDefinition(script, false));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();
        if (verbosePipelines) System.out.println(getLogAsStringPlaintext(run));

        // Check expectations
        j.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        j.assertLogContains("HTTP Request Plugin Response: ", run);
        j.assertLogContains("Using authentication: myCert", run);
    }

    /**
     * Check that Certificate credentials are usable with pipeline script
     * running on a remote {@code node{}} with separate JVM (e.g.
     * check that remoting and credential snapshot work properly).
     */
    @Test
    @Issue("JENKINS-70101")
    void testCertHttpRequestOnNodeRemote() throws Exception {
        assumeTrue(this.setupAgent() == true, "This test needs a separate build agent");

        prepareUploadedKeystore();

        // Configure the build to use the credential
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        String script =
                cpsScriptCredentialTestImports() +
                "node(\"" + agentLabelString + "\") {\n" +
                cpsScriptCertCredentialTestHttpRequest("REMOTE NODE") +
                "}\n";
        proj.setDefinition(new CpsFlowDefinition(script, false));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();
        if (verbosePipelines) System.out.println(getLogAsStringPlaintext(run));

        // Check expectations
        j.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        j.assertLogContains("HTTP Request Plugin Response: ", run);
    }

    /////////////////////////////////////////////////////////////////
    // User/pass credentials tests
    /////////////////////////////////////////////////////////////////

    // Partially from UsernamePasswordCredentialsImplTest setup()
    private void prepareUsernamePassword() throws IOException, FormException {
        UsernamePasswordCredentialsImpl credentials =
                new UsernamePasswordCredentialsImpl(null,
                        "abc123", "Bob’s laptop",
                        "bob", "s3cr3t");
        SystemCredentialsProvider.getInstance().getCredentials().add(credentials);
        SystemCredentialsProvider.getInstance().save();
    }

    private String cpsScriptUsernamePasswordCredentialTestHttpRequest(String runnerTag) {
        return cpsScriptCredentialTestHttpRequest("abc123", runnerTag, false);
    }

    /** Check that Username credentials are usable with pipeline script
     * running without a {@code node{}} block.
     */
    @Test
    @Issue("JENKINS-70101")
    void testUsernamePasswordHttpRequestOnController() throws Exception {
        prepareUsernamePassword();

        // Configure the build to use the credential
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        String script =
                cpsScriptUsernamePasswordCredentialTestHttpRequest("CONTROLLER BUILT-IN");
        proj.setDefinition(new CpsFlowDefinition(script, false));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();
        if (verbosePipelines) System.out.println(getLogAsStringPlaintext(run));

        // Check expectations
        j.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        j.assertLogContains("HTTP Request Plugin Response: ", run);
    }

    /** Check that Username credentials are usable with pipeline script
     *  running on a {@code node{}} (provided by the controller JVM).
     */
    @Test
    @Issue("JENKINS-70101")
    void testUsernamePasswordHttpRequestOnNodeLocal() throws Exception {
        prepareUsernamePassword();

        // Configure the build to use the credential
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        String script =
                "node {\n" +
                        cpsScriptUsernamePasswordCredentialTestHttpRequest("CONTROLLER NODE") +
                "}\n";
        proj.setDefinition(new CpsFlowDefinition(script, false));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();
        if (verbosePipelines) System.out.println(getLogAsStringPlaintext(run));

        // Check expectations
        j.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        j.assertLogContains("HTTP Request Plugin Response: ", run);
    }

    /**
     * Check that Username credentials are usable with pipeline script
     * running on a remote {@code node{}} with separate JVM (e.g.
     * check that remoting and credential snapshot work properly).
     */
    @Test
    @Issue("JENKINS-70101")
    void testUsernamePasswordHttpRequestOnNodeRemote() throws Exception {
        // Check that credentials are usable with pipeline script
        // running on a remote node{} with separate JVM (check
        // that remoting/snapshot work properly)
        assumeTrue(this.setupAgent() == true, "This test needs a separate build agent");

        prepareUsernamePassword();

        // Configure the build to use the credential
        WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proj");
        String script =
                "node(\"" + agentLabelString + "\") {\n" +
                        cpsScriptUsernamePasswordCredentialTestHttpRequest("REMOTE NODE") +
                "}\n";
        proj.setDefinition(new CpsFlowDefinition(script, false));

        // Execute the build
        WorkflowRun run = proj.scheduleBuild2(0).get();
        if (verbosePipelines) System.out.println(getLogAsStringPlaintext(run));

        // Check expectations
        j.assertBuildStatus(Result.SUCCESS, run);
        // Got to the end?
        j.assertLogContains("HTTP Request Plugin Response: ", run);
    }
}
