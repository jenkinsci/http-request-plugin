package jenkins.plugins.http_request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
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
import org.junit.BeforeClass;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

import com.google.common.collect.Iterables;
import com.google.common.io.CharStreams;

/**
 * @author Martin d'Anjou
 */
public class HttpRequestTestBase {

	private static ServerRunning SERVER;
	static final String ALL_IS_WELL = "All is well";
	@Rule
	public JenkinsRule j = new JenkinsRule();

	final void registerRequestChecker(final HttpMode method) {
		registerHandler("/do" + method.name(), method, new SimpleHandler() {
			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
				assertEquals(method.name(), request.getMethod());

				String query = request.getQueryString();
				assertNull(query);
				okAllIsWell(response);
			}
		});
	}

	final void registerContentTypeRequestChecker(final MimeType mimeType, final HttpMode httpMode, final String responseMessage) {
		registerHandler("/incoming_" + mimeType.toString(), httpMode, new SimpleHandler() {
			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
				assertEquals(httpMode.name(), request.getMethod());

				Enumeration<String> headers = request.getHeaders("Content-type");
				if (mimeType == MimeType.NOT_SET) {
					assertFalse(headers.hasMoreElements());
				} else {
					assertTrue(headers.hasMoreElements());
					String value = headers.nextElement();
					assertFalse(headers.hasMoreElements());

					assertEquals(mimeType.getContentType().toString(), value);
				}

				String query = request.getQueryString();
				assertNull(query);
				String body = responseMessage != null ? responseMessage : requestBody(request);
				body(response, HttpServletResponse.SC_OK, mimeType.getContentType(), body);
			}
		});
	}

	final void registerAcceptedTypeRequestChecker(final MimeType mimeType) {
		registerHandler("/accept_" + mimeType.toString(), HttpMode.GET, new SimpleHandler() {
			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
				assertEquals("GET", request.getMethod());

				Enumeration<String> headers = request.getHeaders("Accept");

				if (mimeType == MimeType.NOT_SET) {
					assertFalse(headers.hasMoreElements());
				} else {
					assertTrue(headers.hasMoreElements());
					String value = headers.nextElement();
					assertFalse(headers.hasMoreElements());

					assertEquals(mimeType.getValue(), value);
				}
				String query = request.getQueryString();
				assertNull(query);
				okAllIsWell(response);
			}
		});
	}

	final void registerTimeout() {
		// Timeout, do not respond!
		registerHandler("/timeout", HttpMode.GET, new SimpleHandler() {
			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
				try {
					Thread.sleep(10000);
				} catch (InterruptedException ex) {
					// do nothing the sleep will be interrupted when the test ends
				}
			}
		});
	}

	final void registerReqAction() {
		// Accept the form authentication
		registerHandler("/reqAction", HttpMode.GET, new SimpleHandler() {
			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
				assertEquals("GET", request.getMethod());

				Map<String, String[]> parameters = request.getParameterMap();

				assertEquals(2, parameters.size());
				Entry<String, String[]> e = Iterables.getFirst(parameters.entrySet(), null);
				assertEquals("param1", e.getKey());
				assertEquals(1, e.getValue().length);
				assertEquals("value1", e.getValue()[0]);

				e = Iterables.get(parameters.entrySet(), 1);
				assertEquals("param2", e.getKey());
				assertEquals(1, e.getValue().length);
				assertEquals("value2", e.getValue()[0]);
				okAllIsWell(response);
			}
		});
	}

	final void registerFormAuth() {
		// Check the form authentication
		registerHandler("/formAuth", HttpMode.GET, new SimpleHandler() {
			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
				okAllIsWell(response);
			}
		});
	}

	final void registerFormAuthBad() {
		// Check the form authentication header
		registerHandler("/formAuthBad", HttpMode.GET, new SimpleHandler() {
			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
				body(response, HttpServletResponse.SC_BAD_REQUEST, ContentType.TEXT_PLAIN, "Not allowed");
			}
		});
	}

	final void registerBasicAuth() {
		// Check the basic authentication header
		registerHandler("/basicAuth", HttpMode.GET, new SimpleHandler() {
			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
				Enumeration<String> headers = request.getHeaders("Authorization");

				String value = headers.nextElement();
				assertFalse(headers.hasMoreElements());

				byte[] bytes = Base64.decodeBase64(value.substring(6));
				String usernamePasswordPair = new String(bytes);
				String[] usernamePassword = usernamePasswordPair.split(":");
				assertEquals("username1", usernamePassword[0]);
				assertEquals("password1", usernamePassword[1]);

				okAllIsWell(response);
			}
		});
	}

	final void registerCheckRequestBodyWithTag() {
		// Check that request body is present and that the containing parameter ${Tag} has been resolved to "trunk"
		registerHandler("/checkRequestBodyWithTag", HttpMode.POST, new SimpleHandler() {
			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
				assertEquals("POST", request.getMethod());
				String requestBody = requestBody(request);

				assertEquals("cleanupDir=D:/continuousIntegration/deployments/Daimler/trunk/standalone", requestBody);
				okAllIsWell(response);
			}
		});
	}

	final void registerCustomHeaders() {
		// Check the custom headers
		registerHandler("/customHeaders", HttpMode.GET, new SimpleHandler() {
			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
				Enumeration<String> headers = request.getHeaders("customHeader");

				String value1 = headers.nextElement();
				String value2 = headers.nextElement();

				assertFalse(headers.hasMoreElements());
				assertEquals("value1", value1);
				assertEquals("value2", value2);

				okAllIsWell(response);
			}
		});
	}

	final void registerInvalidStatusCode() {
		// Return an invalid status code
		registerHandler("/invalidStatusCode", HttpMode.GET, new SimpleHandler() {
			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
				assertEquals("GET", request.getMethod());
				String query = request.getQueryString();
				assertNull(query);

				body(response, HttpServletResponse.SC_BAD_REQUEST, ContentType.TEXT_PLAIN, "Throwing status 400 for test");
			}
		});
	}

	final void registerCustomHeadersResolved() {
		// Check if the parameters in custom headers have been resolved
		registerHandler("/customHeadersResolved", HttpMode.POST, new SimpleHandler() {
			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
				Enumeration<String> headers = request.getHeaders("resolveCustomParam");
				String value = headers.nextElement();
				assertFalse(headers.hasMoreElements());
				assertEquals("trunk", value);

				headers = request.getHeaders("resolveEnvParam");
				value = headers.nextElement();
				assertFalse(headers.hasMoreElements());
				assertEquals("C:/path/to/my/workspace", value);

				okAllIsWell(response);
			}
		});
	}

	final void registerCheckBuildParameters() {
		// Check that exactly one build parameter is passed
		registerHandler("/checkBuildParameters", HttpMode.GET, new SimpleHandler() {
			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
				assertEquals("GET", request.getMethod());

				Map<String, String[]> parameters = request.getParameterMap();
				assertEquals(1, parameters.size());
				Entry<String, String[]> parameter = Iterables.getFirst(parameters.entrySet(), null);
				assertEquals("foo", parameter.getKey());
				assertEquals(1, parameter.getValue().length);
				assertEquals("value", parameter.getValue()[0]);

				okAllIsWell(response);
			}
		});
	}

	final void registerCheckRequestBody() {
		// Check that request body is present and equals to TestRequestBody
		registerHandler("/checkRequestBody", HttpMode.POST, new SimpleHandler() {
			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
				assertEquals("POST", request.getMethod());
				String requestBody = requestBody(request);
				assertEquals("TestRequestBody", requestBody);
				okAllIsWell(response);
			}
		});
	}

	final String baseURL() {
		return SERVER.baseURL;
	}

	final void registerHandler(String target, HttpMode method, SimpleHandler handler) {
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
			response.setContentType(contentType.toString());
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
