package jenkins.plugins.http_request;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.MultiException;
import org.eclipse.jetty.util.MultiPartInputStreamParser;

import com.google.common.collect.Iterables;

import jenkins.plugins.http_request.HttpRequestTestBase.SimpleHandler;

/**
 * @author Janario Oliveira
 */
public class Registers {

	static void registerRequestChecker(final HttpMode method) {
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

	static void registerContentTypeRequestChecker(final MimeType mimeType, final HttpMode httpMode, final String responseMessage) {
		registerHandler("/incoming_" + mimeType.toString(), httpMode, new SimpleHandler() {
			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
				assertEquals(httpMode.name(), request.getMethod());

				Enumeration<String> headers = request.getHeaders(HttpHeaders.CONTENT_TYPE);
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

	static void registerAcceptedTypeRequestChecker(final MimeType mimeType) {
		registerHandler("/accept_" + mimeType.toString(), HttpMode.GET, new SimpleHandler() {
			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
				assertEquals("GET", request.getMethod());

				Enumeration<String> headers = request.getHeaders(HttpHeaders.ACCEPT);

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

	static void registerTimeout() {
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

	static void registerReqAction() {
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

	static void registerFormAuth() {
		// Check the form authentication
		registerHandler("/formAuth", HttpMode.GET, new SimpleHandler() {
			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
				okAllIsWell(response);
			}
		});
	}

	static void registerFormAuthBad() {
		// Check the form authentication header
		registerHandler("/formAuthBad", HttpMode.GET, new SimpleHandler() {
			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
				body(response, HttpServletResponse.SC_BAD_REQUEST, ContentType.TEXT_PLAIN, "Not allowed");
			}
		});
	}

	static void registerBasicAuth() {
		// Check the basic authentication header
		registerHandler("/basicAuth", HttpMode.GET, new SimpleHandler() {
			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
				Enumeration<String> headers = request.getHeaders(HttpHeaders.AUTHORIZATION);

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

	static void registerCheckRequestBodyWithTag() {
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

	static void registerCustomHeaders() {
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

	static void registerInvalidStatusCode() {
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

	static void registerCustomHeadersResolved() {
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

	static void registerCheckBuildParameters() {
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

	static void registerCheckRequestBody() {
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

	static void registerFileUpload(final File testFolder, final File uploadFile, final String responseText) {
		registerHandler("/uploadFile", HttpMode.POST, new SimpleHandler() {

			private static final String MULTIPART_FORMDATA_TYPE = "multipart/form-data";

			private void enableMultipartSupport(HttpServletRequest request, MultipartConfigElement multipartConfig) {
				request.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, multipartConfig);
			}

			private boolean isMultipartRequest(ServletRequest request) {
				return request.getContentType() != null && request.getContentType().startsWith(MULTIPART_FORMDATA_TYPE);
			}

			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
				assertEquals("POST", request.getMethod());
				assertTrue(isMultipartRequest(request));

				MultipartConfigElement multipartConfig = new MultipartConfigElement(testFolder.getAbsolutePath());
				enableMultipartSupport(request, multipartConfig);

				try {
					Part part = request.getPart("file-name");
					assertNotNull(part);
					assertEquals(uploadFile.length(), part.getSize());
					assertEquals(uploadFile.getName(), part.getSubmittedFileName());
					assertEquals(MimeType.APPLICATION_ZIP.getValue(), part.getContentType());

					body(response, HttpServletResponse.SC_CREATED, ContentType.TEXT_PLAIN, responseText);
				} finally {
					MultiPartInputStreamParser multipartInputStream = (MultiPartInputStreamParser) request
							.getAttribute(Request.__MULTIPART_INPUT_STREAM);
					if (multipartInputStream != null) {
						try {
							multipartInputStream.deleteParts();
						} catch (MultiException e) {
							// ignore
						}
					}
				}
			}
		});
	}

	private static void registerHandler(String target, HttpMode method, SimpleHandler handler) {
		HttpRequestTestBase.registerHandler(target, method, handler);
	}
}
