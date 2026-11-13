package jenkins.plugins.http_request;

/**
 * Shares the {@link HttpRequestTestBase} echo server fixture (and its {@code SERVER} instance that
 * {@link Registers} registers handlers against) so publisher tests can drive the same harness as the
 * build-step and pipeline-step tests.
 *
 * @author Martin d'Anjou
 */
class HttpRequestPublisherTestBase extends HttpRequestTestBase {
}
