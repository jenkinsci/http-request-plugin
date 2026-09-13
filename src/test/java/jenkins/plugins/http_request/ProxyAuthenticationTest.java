package jenkins.plugins.http_request;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import hudson.model.Result;

/**
 * Verifies that requests sent through an authenticating proxy carry the proxy
 * credential in the {@code Proxy-Authorization} header (issue #314).
 *
 * <p>Uses a minimal socket-level proxy rather than a real one: it rejects
 * requests whose {@code Proxy-Authorization} does not match the expected
 * credential with {@code 407 Proxy Authentication Required}. To keep a
 * wrong-credential request from looping forever it downgrades to {@code 403}
 * after a few challenged attempts.</p>
 */
@WithJenkins
class ProxyAuthenticationTest extends HttpRequestTestBase {

    private static final String PROXY_USER = "proxyUser1";
    private static final String PROXY_PASSWORD = "proxyPassword1";
    private static final String TARGET_USER = "targetUser1";
    private static final String TARGET_PASSWORD = "targetPassword1";

    @Test
    @Timeout(180)
    void proxyAuthenticationWithoutTargetAuthSendsProxyCredentials() throws Exception {
        try (AuthenticatingProxy proxy = new AuthenticatingProxy()) {
            registerBasicCredential("proxy-creds", PROXY_USER, PROXY_PASSWORD);

            WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proxyOnly");
            proj.setDefinition(new CpsFlowDefinition(
                    "def response = httpRequest url:'" + baseURL() + "/StatusOk',\n"
                            + "    httpProxy: 'http://127.0.0.1:" + proxy.port() + "',\n"
                            + "    proxyAuthentication: 'proxy-creds'\n"
                            + "println('Status: '+response.getStatus())\n",
                    true));

            WorkflowRun run = proj.scheduleBuild2(0).get();

            j.assertBuildStatusSuccess(run);
            j.assertLogContains("Status: 200", run);
            assertThat(proxy.lastProxyAuthorization(), is(proxy.expectedAuthorization()));
        }
    }

    @Test
    @Timeout(180)
    void proxyAuthenticationWithBasicTargetAuthSendsProxyCredentials() throws Exception {
        try (AuthenticatingProxy proxy = new AuthenticatingProxy()) {
            registerBasicCredential("proxy-creds", PROXY_USER, PROXY_PASSWORD);
            registerBasicCredential("target-creds", TARGET_USER, TARGET_PASSWORD);

            WorkflowJob proj = j.jenkins.createProject(WorkflowJob.class, "proxyAndTarget");
            proj.setDefinition(new CpsFlowDefinition(
                    "def response = httpRequest url:'" + baseURL() + "/StatusOk',\n"
                            + "    httpProxy: 'http://127.0.0.1:" + proxy.port() + "',\n"
                            + "    proxyAuthentication: 'proxy-creds',\n"
                            + "    authentication: 'target-creds'\n"
                            + "println('Status: '+response.getStatus())\n",
                    true));

            WorkflowRun run = proj.scheduleBuild2(0).get();

            j.assertBuildStatus(Result.SUCCESS, run);
            j.assertLogContains("Status: 200", run);
            // The proxy must receive the proxy credential, not the target one
            assertThat(proxy.lastProxyAuthorization(), is(proxy.expectedAuthorization()));
        }
    }

    /**
     * Minimal authenticating HTTP proxy. Serves requests on a loopback socket,
     * answering {@code 200} when {@code Proxy-Authorization} matches the
     * expected Basic credential, {@code 407} otherwise.
     */
    private static final class AuthenticatingProxy implements Closeable {
        private final ServerSocket serverSocket;
        private final String expectedAuthorization;
        private final AtomicReference<String> lastProxyAuthorization = new AtomicReference<>();
        private final AtomicInteger servedRequests = new AtomicInteger();
        private volatile boolean closed;

        AuthenticatingProxy() throws IOException {
            expectedAuthorization = "Basic " + Base64.getEncoder().encodeToString(
                    (PROXY_USER + ":" + PROXY_PASSWORD).getBytes(StandardCharsets.UTF_8));
            serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            Thread acceptor = new Thread(this::serve, "authenticating-proxy");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        String expectedAuthorization() {
            return expectedAuthorization;
        }

        String lastProxyAuthorization() {
            return lastProxyAuthorization.get();
        }

        private void serve() {
            while (!closed) {
                try (Socket socket = serverSocket.accept()) {
                    socket.setSoTimeout(10_000);
                    handleRequest(socket);
                } catch (IOException e) {
                    if (closed) {
                        return;
                    }
                    // transient accept/IO failure: keep serving
                }
            }
        }

        private void handleRequest(Socket socket) throws IOException {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            String requestLine = reader.readLine();
            if (requestLine == null) {
                return;
            }
            String proxyAuthorization = null;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0 && "Proxy-Authorization".equalsIgnoreCase(line.substring(0, colon).trim())) {
                    proxyAuthorization = line.substring(colon + 1).trim();
                }
            }
            lastProxyAuthorization.set(proxyAuthorization);
            servedRequests.incrementAndGet();

            boolean authorized = expectedAuthorization.equals(proxyAuthorization);
            String statusLine;
            StringBuilder headers = new StringBuilder();
            String body;
            if (authorized) {
                statusLine = "HTTP/1.1 200 OK";
                body = "proxied";
            } else if (servedRequests.get() <= 3) {
                statusLine = "HTTP/1.1 407 Proxy Authentication Required";
                headers.append("Proxy-Authenticate: Basic realm=\"fake-proxy\"\r\n");
                body = "proxy authentication required";
            } else {
                // stop challenging a client that keeps offering bad credentials
                statusLine = "HTTP/1.1 403 Forbidden";
                body = "not authorized";
            }

            String response = statusLine + "\r\n"
                    + headers
                    + "Content-Type: text/plain\r\n"
                    + "Content-Length: " + body.length() + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n"
                    + body;
            OutputStream out = socket.getOutputStream();
            out.write(response.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
        }

        @Override
        public void close() throws IOException {
            closed = true;
            serverSocket.close();
        }
    }
}
