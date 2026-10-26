package com.chandrahas.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

// starts the real server on a random free port and calls it over HTTP
class CalculatorServerTest {

	private static CalculatorServer server;
	private static HttpClient client;
	private static String baseUrl;

	@BeforeAll
	static void startServer() throws IOException {
		server = new CalculatorServer(0, new Calculator());
		server.start();
		client = HttpClient.newHttpClient();
		baseUrl = "http://localhost:" + server.getPort();
	}

	@AfterAll
	static void stopServer() {
		server.stop();
	}

	private static HttpResponse<String> get(String pathAndQuery) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + pathAndQuery)).GET().build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void addReturnsJsonResult() throws Exception {
		HttpResponse<String> response = get("/api/v1/add?a=2&b=3");

		assertEquals(200, response.statusCode());
		assertEquals("application/json", response.headers().firstValue("Content-Type").orElse(""));
		assertEquals("{\"operation\":\"add\",\"a\":2,\"b\":3,\"result\":5}", response.body());
	}

	@Test
	void subtractReturnsJsonResult() throws Exception {
		HttpResponse<String> response = get("/api/v1/subtract?a=2&b=3");

		assertEquals(200, response.statusCode());
		assertEquals("{\"operation\":\"subtract\",\"a\":2,\"b\":3,\"result\":-1}", response.body());
	}

	@Test
	void multiplyReturnsJsonResult() throws Exception {
		HttpResponse<String> response = get("/api/v1/multiply?a=2.5&b=4");

		assertEquals(200, response.statusCode());
		assertEquals("{\"operation\":\"multiply\",\"a\":2.5,\"b\":4,\"result\":10.0}", response.body());
	}

	@Test
	void divideReturnsJsonResult() throws Exception {
		HttpResponse<String> response = get("/api/v1/divide?a=10&b=4");

		assertEquals(200, response.statusCode());
		assertEquals("{\"operation\":\"divide\",\"a\":10,\"b\":4,\"result\":2.5}", response.body());
	}

	@Test
	void decimalsDoNotLosePrecision() throws Exception {
		assertEquals("{\"operation\":\"add\",\"a\":0.1,\"b\":0.2,\"result\":0.3}",
				get("/api/v1/add?a=0.1&b=0.2").body());
	}

	@Test
	void divideByZeroReturns400() throws Exception {
		HttpResponse<String> response = get("/api/v1/divide?a=5&b=0");

		assertEquals(400, response.statusCode());
		assertEquals("{\"status\":400,\"error\":\"Bad Request\",\"message\":\"Division by zero is not allowed\"}",
				response.body());
	}

	@Test
	void missingParameterReturns400() throws Exception {
		HttpResponse<String> response = get("/api/v1/add?a=5");

		assertEquals(400, response.statusCode());
		assertTrue(response.body().contains("Missing required parameter: b"));
	}

	@Test
	void emptyParameterCountsAsMissing() throws Exception {
		HttpResponse<String> response = get("/api/v1/add?a=&b=1");

		assertEquals(400, response.statusCode());
		assertTrue(response.body().contains("Missing required parameter: a"));
	}

	@Test
	void nonNumericParameterReturns400() throws Exception {
		HttpResponse<String> response = get("/api/v1/multiply?a=abc&b=2");

		assertEquals(400, response.statusCode());
		assertTrue(response.body().contains("Parameter 'a' must be a valid number"));
	}

	@Test
	void nanIsNotAccepted() throws Exception {
		assertEquals(400, get("/api/v1/add?a=NaN&b=1").statusCode());
	}

	@Test
	void hugeExponentIsRejected() throws Exception {
		assertEquals(400, get("/api/v1/add?a=1e999999999&b=1").statusCode());
	}

	@Test
	void healthEndpointIsUp() throws Exception {
		HttpResponse<String> response = get("/actuator/health");

		assertEquals(200, response.statusCode());
		assertEquals("{\"status\":\"UP\"}", response.body());
	}

	@Test
	void unknownPathReturns404() throws Exception {
		assertEquals(404, get("/api/v1/power?a=2&b=3").statusCode());
		assertEquals(404, get("/nothing").statusCode());
	}

	@Test
	void postIsNotAllowed() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/add?a=1&b=2"))
				.POST(HttpRequest.BodyPublishers.noBody()).build();
		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		assertEquals(405, response.statusCode());
		assertEquals("GET", response.headers().firstValue("Allow").orElse(""));
	}

}
