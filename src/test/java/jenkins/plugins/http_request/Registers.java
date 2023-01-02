package jenkins.plugins.http_request;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Enumeration;
import java.util.Map;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.eclipse.jetty.server.MultiPartFormInputStream;
import org.eclipse.jetty.server.Request;

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
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
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
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
				assertEquals("GET", request.getMethod());

				Map<String, String[]> parameters = request.getParameterMap();

				assertEquals(2, parameters.size());
				assertTrue(parameters.containsKey("param1"));
				String[] value = parameters.get("param1");
				assertEquals(1, value.length);
				assertEquals("value1", value[0]);

				assertTrue(parameters.containsKey("param2"));
				value = parameters.get("param2");
				assertEquals(1, value.length);
				assertEquals("value2", value[0]);
				okAllIsWell(response);
			}
		});
	}

	static void registerFormAuth() {
		// Check the form authentication
		registerHandler("/formAuth", HttpMode.GET, new SimpleHandler() {
			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
				okAllIsWell(response);
			}
		});
	}

	static void registerFormAuthBad() {
		// Check the form authentication header
		registerHandler("/formAuthBad", HttpMode.GET, new SimpleHandler() {
			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
				body(response, HttpServletResponse.SC_BAD_REQUEST, ContentType.TEXT_PLAIN, "Not allowed");
			}
		});
	}

	static void registerBasicAuth() {
		// Check the basic authentication header
		registerHandler("/basicAuth", HttpMode.GET, new SimpleHandler() {
			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
				Enumeration<String> headers = request.getHeaders(HttpHeaders.AUTHORIZATION);

				String value = headers.nextElement();
				assertFalse(headers.hasMoreElements());

				byte[] bytes = Base64.getDecoder().decode(value.substring(6));
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
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
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
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
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
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
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
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
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
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
				assertEquals("GET", request.getMethod());

				Map<String, String[]> parameters = request.getParameterMap();
				assertEquals(1, parameters.size());
				assertTrue(parameters.containsKey("foo"));
				String[] value = parameters.get("foo");
				assertEquals(1, value.length);
				assertEquals("value", value[0]);

				okAllIsWell(response);
			}
		});
	}

	static void registerCheckRequestBody() {
		// Check that request body is present and equals to TestRequestBody
		registerHandler("/checkRequestBody", HttpMode.POST, new SimpleHandler() {
			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
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
					String MULTIPART = "org.eclipse.jetty.servlet.MultiPartFile.multiPartInputStream";
					MultiPartFormInputStream multipartInputStream = (MultiPartFormInputStream) request.getAttribute(MULTIPART);
					if (multipartInputStream != null) {
						multipartInputStream.deleteParts();
					}
				}
			}
		});
	}

	static void registerFormData(final File testFolder, String content, final File file1,
			File file2, final String responseText) {
		registerHandler("/formData", HttpMode.POST, new SimpleHandler() {

			private static final String MULTIPART_FORMDATA_TYPE = "multipart/form-data";

			private void enableMultipartSupport(HttpServletRequest request,
					MultipartConfigElement multipartConfig) {
				request.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, multipartConfig);
			}

			private boolean isMultipartRequest(ServletRequest request) {
				return request.getContentType() != null
						&& request.getContentType().startsWith(MULTIPART_FORMDATA_TYPE);
			}

			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request,
					HttpServletResponse response) throws IOException, ServletException {
				assertEquals("POST", request.getMethod());
				assertTrue(isMultipartRequest(request));

				MultipartConfigElement multipartConfig =
						new MultipartConfigElement(testFolder.getAbsolutePath());
				enableMultipartSupport(request, multipartConfig);

				try {
					Part file1Part = request.getPart("file1");
					assertNotNull(file1Part);
					assertEquals(file1.length(), file1Part.getSize());
					assertEquals(file1.getName(), file1Part.getSubmittedFileName());
					assertEquals(MimeType.TEXT_PLAIN.getValue(), file1Part.getContentType());

					Part file2Part = request.getPart("file2");
					assertNotNull(file2Part);
					assertEquals(file2.length(), file2Part.getSize());
					assertEquals(file2.getName(), file2Part.getSubmittedFileName());
					assertEquals(MimeType.APPLICATION_ZIP.getValue(), file2Part.getContentType());

					Part modelPart = request.getPart("model");
					assertNotNull(modelPart);
					assertEquals(content,
							IOUtils.toString(modelPart.getInputStream(), StandardCharsets.UTF_8));
					assertEquals(MimeType.APPLICATION_JSON.getValue(), modelPart.getContentType());

					// So far so good
					body(response, HttpServletResponse.SC_CREATED, ContentType.TEXT_PLAIN,
							responseText);
				} finally {
					String MULTIPART =
							"org.eclipse.jetty.servlet.MultiPartFile.multiPartInputStream";
					MultiPartFormInputStream multipartInputStream =
							(MultiPartFormInputStream) request.getAttribute(MULTIPART);
					if (multipartInputStream != null) {
						multipartInputStream.deleteParts();
					}
				}
			}
		});
	}

	static void registerUnwrappedPutFileUpload(final File uploadFile, final String responseText) {
		registerHandler("/uploadFile/" + uploadFile.getName(), HttpMode.PUT, new SimpleHandler() {

			private static final String MULTIPART_FORMDATA_TYPE = "multipart/form-data";

			private boolean isMultipartRequest(ServletRequest request) {
				return request.getContentType() != null && request.getContentType().startsWith(MULTIPART_FORMDATA_TYPE);
			}

			@Override
			void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
				assertEquals("PUT", request.getMethod());
				assertFalse(isMultipartRequest(request));
				assertEquals(uploadFile.length(), request.getContentLength());
				assertEquals(MimeType.APPLICATION_ZIP.getValue(), request.getContentType());
				body(response, HttpServletResponse.SC_CREATED, ContentType.TEXT_PLAIN, responseText);
			}
		});
	}

	private static void registerHandler(String target, HttpMode method, SimpleHandler handler) {
		HttpRequestTestBase.registerHandler(target, method, handler);
	}
}
