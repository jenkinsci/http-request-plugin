package jenkins.plugins.http_request;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import jenkins.plugins.http_request.util.HttpRequestQueryParam;

class HttpRequestExecutionTest {

	private static final String ENDPOINT = "https://localhost:8080/endpoint";

	@Test
	void noParam() throws MalformedURLException {
		URL actual = HttpRequestExecution.getUrl(ENDPOINT, List.of());
		Assertions.assertEquals(new URL(ENDPOINT), actual);
	}

	@Test
	void emptyParam() throws MalformedURLException {
		URL actual = HttpRequestExecution.getUrl(ENDPOINT, List.of(
				new HttpRequestQueryParam("none")
		));
		Assertions.assertEquals(new URL(ENDPOINT + "?none"), actual);
	}

	@Test
	void twoParam() throws MalformedURLException {
		URL actual = HttpRequestExecution.getUrl(ENDPOINT, List.of(
				new HttpRequestQueryParam("one", "1"),
				new HttpRequestQueryParam("two", "2")
		));
		Assertions.assertEquals(new URL(ENDPOINT + "?one=1&two=2"), actual);
	}

	@Test
	void encoded() throws MalformedURLException {
		URL actual = HttpRequestExecution.getUrl(ENDPOINT, List.of(
				new HttpRequestQueryParam("special", ", ?&"),
				new HttpRequestQueryParam("two", "inner=v")
		));
		Assertions.assertEquals(new URL(ENDPOINT + "?special=%2C+%3F%26&two=inner%3Dv"), actual);
	}
}