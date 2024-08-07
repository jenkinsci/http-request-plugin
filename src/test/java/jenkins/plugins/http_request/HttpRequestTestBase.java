package jenkins.plugins.http_request;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletResponse;

import org.apache.http.entity.ContentType;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.util.Callback;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

/**
 * @author Martin d'Anjou
 */
public class HttpRequestTestBase {

	private static ServerRunning SERVER;
	static final String ALL_IS_WELL = "All is well";
	@Rule
	public JenkinsRule j = new JenkinsRule();
	private Map<Domain, List<Credentials>> credentials;

	final String baseURL() {
		return SERVER.baseURL;
	}

	void registerBasicCredential(String id, String username, String password) {
		credentials.get(Domain.global()).add(
				new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
						id, "", username, password));
		SystemCredentialsProvider.getInstance().setDomainCredentialsMap(credentials);
	}

	static void registerHandler(String target, HttpMode method, SimpleHandler handler) {
		Map<HttpMode, Handler> handlerByMethod = SERVER.handlersByMethodByTarget.computeIfAbsent(target, k -> new HashMap<>());
		handlerByMethod.put(method, handler);
	}

	@BeforeClass
	public static void beforeClass() throws Exception {
		if (SERVER != null) {
			return;
		}
		SERVER = new ServerRunning();
	}

	@AfterClass
	public static void afterClass() throws Exception {
		if (SERVER != null) {
			SERVER.server.stop();
			SERVER = null;
		}
	}

	@Before
	public void init() {
		credentials = new HashMap<>();
		credentials.put(Domain.global(), new ArrayList<>());
	}

	@After
	public void cleanHandlers() {
		if (SERVER != null) {
			SERVER.handlersByMethodByTarget.clear();
		}
	}

	public static abstract class SimpleHandler extends Handler.Abstract {
		@Override
		public final boolean handle(Request request, Response response, Callback callback) throws Exception {
			doHandle(request, response, callback);
			return true;
		}

		String requestBody(Request request) throws IOException, ExecutionException, InterruptedException {
			CompletableFuture<String> completable = Content.Source.asStringAsync(request, StandardCharsets.UTF_8);
			return completable.get();
		}

		void okAllIsWell(Response response, Callback callback) throws IOException {
			okText(response, ALL_IS_WELL, callback);
		}

		void okText(Response response, String body, Callback callback) throws IOException {
			body(response, HttpStatus.OK_200, ContentType.TEXT_PLAIN, body, callback);
		}

		void body(Response response, int status, ContentType contentType, String body, Callback callback) throws IOException {
			response.getHeaders().put(String.valueOf(contentType), "text/plain; charset=UTF-8");
			response.setStatus(status);
			Content.Sink.write(response, true, body, callback);
		}

		abstract boolean doHandle(Request request, Response response, Callback callback) throws Exception;
	}

	private static final class ServerRunning {
		private final Server server;
		private final int port;
		private final String baseURL;
		private final Map<String, Map<HttpMode, Handler>> handlersByMethodByTarget = new HashMap<>();

		private ServerRunning() throws Exception {
			server = new Server();
			ServerConnector connector = new ServerConnector(server);
			server.setConnectors(new Connector[]{connector});

			ContextHandler context = new ContextHandler();
			context.setContextPath("/");
			context.setHandler(new DefaultHandler() {
				@Override
				public boolean handle(Request request, Response response, Callback callback) throws Exception {
					String target = request.getHttpURI().getPath();
					Map<HttpMode, Handler> handlerByMethod = handlersByMethodByTarget.get(target);
					if (handlerByMethod != null) {
						Handler handler = handlerByMethod.get(HttpMode.valueOf(request.getMethod()));
						if (handler != null) {
							handler.handle(request, response, callback);
							return true;
						}
					}

					super.handle(request, response, callback);
					return true;
				}
			});
			server.setHandler(context);

			server.start();
			port = connector.getLocalPort();
			baseURL = "http://localhost:" + port;
		}
	}
}
