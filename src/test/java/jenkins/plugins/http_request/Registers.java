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
import java.util.concurrent.ExecutionException;

import javax.servlet.ServletException;

import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MultiPart;
import org.eclipse.jetty.http.MultiPartConfig;
import org.eclipse.jetty.http.MultiPartFormData;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import jenkins.plugins.http_request.HttpRequestTestBase.SimpleHandler;

/**
 * @author Janario Oliveira
 */
public class Registers {

	static void registerRequestChecker(final HttpMode method) {
		registerHandler("/do" + method.name(), method, new SimpleHandler() {
			@Override
			boolean doHandle(Request request, Response response, Callback callback) {
				assertEquals(method.name(), request.getMethod());

				String query = request.getHttpURI().getQuery();
				assertNull(query);
				return okAllIsWell(response, callback);
			}
		});
	}

	static void registerContentTypeRequestChecker(final MimeType mimeType, final HttpMode httpMode, final String responseMessage) {
		registerHandler("/incoming_" + mimeType.toString(), httpMode, new SimpleHandler() {
			@Override
			boolean doHandle(Request request, Response response, Callback callback) throws IOException {
				assertEquals(httpMode.name(), request.getMethod());

				Enumeration<String> headers = request.getHeaders().getValues(HttpHeaders.CONTENT_TYPE);
				if (mimeType == MimeType.NOT_SET) {
					assertFalse(headers.hasMoreElements());
				} else {
					assertTrue(headers.hasMoreElements());
					String value = headers.nextElement();
					assertFalse(headers.hasMoreElements());

					assertEquals(mimeType.getContentType().toString(), value);
				}

				String query = request.getHttpURI().getQuery();
				assertNull(query);
				String body = responseMessage != null ? responseMessage : requestBody(request);
				return body(response, HttpStatus.OK_200, mimeType.getContentType(), body, callback);
			}
		});
	}

	static void registerAcceptedTypeRequestChecker(final MimeType mimeType) {
		registerHandler("/accept_" + mimeType.toString(), HttpMode.GET, new SimpleHandler() {
			@Override
			boolean doHandle(Request request, Response response, Callback callback) {
				assertEquals("GET", request.getMethod());

				Enumeration<String> headers = request.getHeaders().getValues(HttpHeaders.ACCEPT);


				if (mimeType == MimeType.NOT_SET) {
					assertFalse(headers.hasMoreElements());
				} else {
					assertTrue(headers.hasMoreElements());
					String value = headers.nextElement();
					assertFalse(headers.hasMoreElements());

					assertEquals(mimeType.getValue(), value);
				}
				String query = request.getHttpURI().getQuery();
				assertNull(query);
				return okAllIsWell(response, callback);
			}
		});
	}

	static void registerTimeout() {
		// Timeout, do not respond!
		registerHandler("/timeout", HttpMode.GET, new SimpleHandler() {
			@Override
			boolean doHandle(Request request, Response response, Callback callback) {
				try {
					Thread.sleep(10000);
				} catch (InterruptedException ex) {
					// do nothing the sleep will be interrupted when the test ends
				}
				return true;
			}
		});
	}

	static void registerReqAction() {
		// Accept the form authentication
		registerHandler("/reqAction", HttpMode.GET, new SimpleHandler() {
			@Override
			boolean doHandle(Request request, Response response, Callback callback) throws ServletException {
				assertEquals("GET", request.getMethod());

				Map<String, String[]> parameters;
				try {
					parameters = Request.getParameters(request).toStringArrayMap();
				} catch (Exception e) {
					throw new ServletException(e);
				}

				assertEquals(2, parameters.size());
				assertTrue(parameters.containsKey("param1"));
				String[] value = parameters.get("param1");
				assertEquals(1, value.length);
				assertEquals("value1", value[0]);

				assertTrue(parameters.containsKey("param2"));
				value = parameters.get("param2");
				assertEquals(1, value.length);
				assertEquals("value2", value[0]);
				return okAllIsWell(response, callback);
			}
		});
	}

	static void registerFormAuth() {
		// Check the form authentication
		registerHandler("/formAuth", HttpMode.GET, new SimpleHandler() {
			@Override
			boolean doHandle(Request request, Response response, Callback callback) {
				return okAllIsWell(response, callback);
			}
		});
	}

	static void registerFormAuthBad() {
		// Check the form authentication header
		registerHandler("/formAuthBad", HttpMode.GET, new SimpleHandler() {
			@Override
			boolean doHandle(Request request, Response response, Callback callback) {
				return body(response, HttpStatus.BAD_REQUEST_400, ContentType.TEXT_PLAIN, "Not allowed", callback);
			}
		});
	}

	static void registerBasicAuth() {
		// Check the basic authentication header
		registerHandler("/basicAuth", HttpMode.GET, new SimpleHandler() {
			@Override
			boolean doHandle(Request request, Response response, Callback callback) {
				Enumeration<String> headers = request.getHeaders().getValues(HttpHeaders.AUTHORIZATION);

				String value = headers.nextElement();
				assertFalse(headers.hasMoreElements());

				byte[] bytes = Base64.getDecoder().decode(value.substring(6));
				String usernamePasswordPair = new String(bytes);
				String[] usernamePassword = usernamePasswordPair.split(":");
				assertEquals("username1", usernamePassword[0]);
				assertEquals("password1", usernamePassword[1]);

				return okAllIsWell(response, callback);
			}
		});
	}

	static void registerCheckRequestBodyWithTag() {
		// Check that request body is present and that the containing parameter ${Tag} has been resolved to "trunk"
		registerHandler("/checkRequestBodyWithTag", HttpMode.POST, new SimpleHandler() {
			@Override
			boolean doHandle(Request request, Response response, Callback callback) throws IOException {
				assertEquals("POST", request.getMethod());
				String requestBody = requestBody(request);

				assertEquals("cleanupDir=D:/continuousIntegration/deployments/Daimler/trunk/standalone", requestBody);
				return okAllIsWell(response, callback);
			}
		});
	}

	static void registerCustomHeaders() {
		// Check the custom headers
		registerHandler("/customHeaders", HttpMode.GET, new SimpleHandler() {
			@Override
			boolean doHandle(Request request, Response response, Callback callback) {
				Enumeration<String> headers = request.getHeaders().getValues("customHeader");

				String value1 = headers.nextElement();
				String value2 = headers.nextElement();

				assertFalse(headers.hasMoreElements());
				assertEquals("value1", value1);
				assertEquals("value2", value2);

				return okAllIsWell(response, callback);
			}
		});
	}

	static void registerInvalidStatusCode() {
		// Return an invalid status code
		registerHandler("/invalidStatusCode", HttpMode.GET, new SimpleHandler() {
			@Override
			boolean doHandle(Request request, Response response, Callback callback) {
				assertEquals("GET", request.getMethod());
				String query = request.getHttpURI().getQuery();
				assertNull(query);

				return body(response, HttpStatus.BAD_REQUEST_400, ContentType.TEXT_PLAIN, "Throwing status 400 for test", callback);
			}
		});
	}

	static void registerCustomHeadersResolved() {
		// Check if the parameters in custom headers have been resolved
		registerHandler("/customHeadersResolved", HttpMode.POST, new SimpleHandler() {
			@Override
			boolean doHandle(Request request, Response response, Callback callback) {
				Enumeration<String> headers = request.getHeaders().getValues("resolveCustomParam");

				String value = headers.nextElement();
				assertFalse(headers.hasMoreElements());
				assertEquals("trunk", value);

				headers = request.getHeaders().getValues("resolveEnvParam");
				value = headers.nextElement();
				assertFalse(headers.hasMoreElements());
				assertEquals("C:/path/to/my/workspace", value);

				return okAllIsWell(response, callback);
			}
		});
	}

	static void registerCheckBuildParameters() {
		// Check that exactly one build parameter is passed
		registerHandler("/checkBuildParameters", HttpMode.GET, new SimpleHandler() {
			@Override
			boolean doHandle(Request request, Response response, Callback callback) throws ServletException {
				assertEquals("GET", request.getMethod());

				Map<String, String[]> parameters;
				try {
					parameters = Request.getParameters(request).toStringArrayMap();
				} catch (Exception e) {
					throw new ServletException(e);
				}

				assertEquals(1, parameters.size());
				assertTrue(parameters.containsKey("foo"));
				String[] value = parameters.get("foo");
				assertEquals(1, value.length);
				assertEquals("value", value[0]);

				return okAllIsWell(response, callback);
			}
		});
	}

	static void registerCheckRequestBody() {
		// Check that request body is present and equals to TestRequestBody
		registerHandler("/checkRequestBody", HttpMode.POST, new SimpleHandler() {
			@Override
			boolean doHandle(Request request, Response response, Callback callback) throws IOException {
				assertEquals("POST", request.getMethod());
				String requestBody = requestBody(request);
				assertEquals("TestRequestBody", requestBody);
				return okAllIsWell(response, callback);
			}
		});
	}

	static void registerFileUpload(final File uploadFile, final String responseText) {
		registerHandler("/uploadFile", HttpMode.POST, new SimpleHandler() {

			private static final String MULTIPART_FORMDATA_TYPE = "multipart/form-data";

			private boolean isMultipartRequest(Request request) {
				return request.getHeaders().get(HttpHeader.CONTENT_TYPE) != null && request.getHeaders().get(HttpHeader.CONTENT_TYPE).startsWith(MULTIPART_FORMDATA_TYPE);
			}

			@Override
			boolean doHandle(Request request, Response response, Callback callback) throws ServletException {
				assertEquals("POST", request.getMethod());
				assertTrue(isMultipartRequest(request));

				MultiPartFormData.Parts parts;
				try {
					String contentType = request.getHeaders().get(HttpHeader.CONTENT_TYPE);
					parts = MultiPartFormData.from(request, request, contentType, new MultiPartConfig.Builder().build()).get();
				} catch (InterruptedException | ExecutionException e) {
					throw new ServletException(e);
				}

				MultiPart.Part part = parts.getFirst("file-name");
				assertNotNull(part);
				assertEquals(uploadFile.length(), part.getLength());
				assertEquals(uploadFile.getName(), part.getFileName());
				assertEquals(MimeType.APPLICATION_ZIP.getValue(), part.getHeaders().get(HttpHeader.CONTENT_TYPE));

				return body(response, HttpStatus.CREATED_201, ContentType.TEXT_PLAIN, responseText, callback);
			}
		});
	}

	static void registerFormData(String content, final File file1,
			File file2, final String responseText) {
		registerHandler("/formData", HttpMode.POST, new SimpleHandler() {

			private static final String MULTIPART_FORMDATA_TYPE = "multipart/form-data";

			private boolean isMultipartRequest(Request request) {
				return request.getHeaders().get(HttpHeader.CONTENT_TYPE) != null
						&& request.getHeaders().get(HttpHeader.CONTENT_TYPE).startsWith(MULTIPART_FORMDATA_TYPE);
			}

			@Override
			boolean doHandle(Request request, Response response, Callback callback) throws ServletException {
				assertEquals("POST", request.getMethod());
				assertTrue(isMultipartRequest(request));

				MultiPartFormData.Parts parts;
				try {
					String contentType = request.getHeaders().get(HttpHeader.CONTENT_TYPE);
					parts = MultiPartFormData.from(request, request, contentType, new MultiPartConfig.Builder().build()).get();
				} catch (InterruptedException | ExecutionException e) {
					throw new ServletException(e);
				}

				MultiPart.Part file1Part = parts.getFirst("file1");
				assertNotNull(file1Part);
				assertEquals(file1.length(), file1Part.getLength());
				assertEquals(file1.getName(), file1Part.getFileName());
				assertEquals(MimeType.TEXT_PLAIN.getValue(), file1Part.getHeaders().get(HttpHeader.CONTENT_TYPE));

				MultiPart.Part file2Part = parts.getFirst("file2");
				assertNotNull(file2Part);
				assertEquals(file2.length(), file2Part.getLength());
				assertEquals(file2.getName(), file2Part.getFileName());
				assertEquals(MimeType.APPLICATION_ZIP.getValue(), file2Part.getHeaders().get(HttpHeader.CONTENT_TYPE));

				MultiPart.Part modelPart = parts.getFirst("model");
				assertNotNull(modelPart);
				assertEquals(content, modelPart.getContentAsString(StandardCharsets.UTF_8));
				assertEquals(MimeType.APPLICATION_JSON.getValue(), modelPart.getHeaders().get(HttpHeader.CONTENT_TYPE));

				// So far so good
				return body(response, HttpStatus.CREATED_201, ContentType.TEXT_PLAIN,
						responseText, callback);
			}
		});
	}

	static void registerUnwrappedPutFileUpload(final File uploadFile, final String responseText) {
		registerHandler("/uploadFile/" + uploadFile.getName(), HttpMode.PUT, new SimpleHandler() {

			private static final String MULTIPART_FORMDATA_TYPE = "multipart/form-data";

			private boolean isMultipartRequest(Request request) {
				return request.getHeaders().get(HttpHeader.CONTENT_TYPE) != null && request.getHeaders().get(HttpHeader.CONTENT_TYPE).startsWith(MULTIPART_FORMDATA_TYPE);
			}

			@Override
			boolean doHandle(Request request, Response response, Callback callback) {
				assertEquals("PUT", request.getMethod());
				assertFalse(isMultipartRequest(request));
				assertEquals(uploadFile.length(), request.getLength());
				assertEquals(MimeType.APPLICATION_ZIP.getValue(), request.getHeaders().get(HttpHeader.CONTENT_TYPE));
				return body(response, HttpStatus.CREATED_201, ContentType.TEXT_PLAIN, responseText, callback);
			}
		});
	}

	private static void registerHandler(String target, HttpMode method, SimpleHandler handler) {
		HttpRequestTestBase.registerHandler(target, method, handler);
	}
}
