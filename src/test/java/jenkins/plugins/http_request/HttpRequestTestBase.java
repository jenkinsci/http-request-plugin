package jenkins.plugins.http_request;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.entity.ContentType;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.DefaultHandler;
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
import com.google.common.io.CharStreams;

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
		Map<HttpMode, Handler> handlerByMethod = SERVER.handlersByMethodByTarget.get(target);
		if (handlerByMethod == null) {
			SERVER.handlersByMethodByTarget.put(target, handlerByMethod = new HashMap<>());
		}
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
		credentials.put(Domain.global(), new ArrayList<Credentials>());
	}

	@After
	public void cleanHandlers() {
		if (SERVER != null) {
			SERVER.handlersByMethodByTarget.clear();
		}
	}

	public static abstract class SimpleHandler extends DefaultHandler {
		@Override
		public final void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
			doHandle(target, baseRequest, request, response);
			baseRequest.setHandled(true);
		}

		String requestBody(HttpServletRequest request) throws IOException {
			try (BufferedReader reader = request.getReader()) {
				return CharStreams.toString(reader);
			}
		}

		void okAllIsWell(HttpServletResponse response) throws IOException {
			okText(response, ALL_IS_WELL);
		}

		void okText(HttpServletResponse response, String body) throws IOException {
			body(response, HttpServletResponse.SC_OK, ContentType.TEXT_PLAIN, body);
		}

		void body(HttpServletResponse response, int status, ContentType contentType, String body) throws IOException {
			response.setContentType(contentType != null ? contentType.toString() : "");
			response.setStatus(status);
			response.getWriter().append(body);
		}

		abstract void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;
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
				public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
					Map<HttpMode, Handler> handlerByMethod = handlersByMethodByTarget.get(target);
					if (handlerByMethod != null) {
						Handler handler = handlerByMethod.get(HttpMode.valueOf(request.getMethod()));
						if (handler != null) {
							handler.handle(target, baseRequest, request, response);
							return;
						}
					}

					super.handle(target, baseRequest, request, response);
				}
			});
			server.setHandler(context);

			server.start();
			port = connector.getLocalPort();
			baseURL = "http://localhost:" + port;
		}
	}
}
