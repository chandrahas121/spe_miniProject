package com.chandrahas.calculator;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BinaryOperator;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class CalculatorServer {

	private static final String API_PREFIX = "/api/v1/";
	private static final String HEALTH_PATH = "/actuator/health";

	// rejects inputs like 1e999999999 that would blow up when printed as a plain number
	private static final int MAX_SCALE = 1000;

	private final HttpServer server;
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
	private final Map<String, BinaryOperator<BigDecimal>> operations;

	public CalculatorServer(int port, Calculator calculator) throws IOException {
		this.operations = Map.of(
				"add", calculator::add,
				"subtract", calculator::subtract,
				"multiply", calculator::multiply,
				"divide", calculator::divide);
		this.server = HttpServer.create(new InetSocketAddress(port), 0);
		this.server.createContext("/", this::handle);
		this.server.setExecutor(executor);
	}

	public void start() {
		server.start();
	}

	public void stop() {
		server.stop(0);
		executor.shutdown();
	}

	// useful when the server was started on port 0 (random free port)
	public int getPort() {
		return server.getAddress().getPort();
	}

	private void handle(HttpExchange exchange) throws IOException {
		try (exchange) {
			try {
				route(exchange);
			} catch (BadRequestException | DivisionByZeroException e) {
				sendError(exchange, 400, "Bad Request", e.getMessage());
			} catch (RuntimeException e) {
				e.printStackTrace();
				sendError(exchange, 500, "Internal Server Error", "Something went wrong");
			}
		}
	}

	private void route(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		String name = path.startsWith(API_PREFIX) ? path.substring(API_PREFIX.length()) : "";
		BinaryOperator<BigDecimal> operation = operations.get(name);
		boolean isHealth = path.equals(HEALTH_PATH);
		if (operation == null && !isHealth) {
			sendError(exchange, 404, "Not Found", "No such endpoint");
			return;
		}
		if (!exchange.getRequestMethod().equals("GET")) {
			exchange.getResponseHeaders().set("Allow", "GET");
			sendError(exchange, 405, "Method Not Allowed", "Only GET is supported");
			return;
		}

		if (isHealth) {
			send(exchange, 200, "{\"status\":\"UP\"}");
			return;
		}

		Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
		BigDecimal a = number(params, "a");
		BigDecimal b = number(params, "b");
		BigDecimal result = operation.apply(a, b);
		send(exchange, 200, "{\"operation\":\"%s\",\"a\":%s,\"b\":%s,\"result\":%s}".formatted(
				name, a.toPlainString(), b.toPlainString(), result.toPlainString()));
	}

	private static Map<String, String> parseQuery(String rawQuery) {
		Map<String, String> params = new HashMap<>();
		if (rawQuery == null || rawQuery.isEmpty()) {
			return params;
		}
		for (String pair : rawQuery.split("&")) {
			int eq = pair.indexOf('=');
			String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
			String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
			params.putIfAbsent(key, value);
		}
		return params;
	}

	private static BigDecimal number(Map<String, String> params, String name) {
		String value = params.get(name);
		if (value == null || value.isBlank()) {
			throw new BadRequestException("Missing required parameter: " + name);
		}
		BigDecimal number;
		try {
			number = new BigDecimal(value);
		} catch (NumberFormatException e) {
			throw invalidNumber(name);
		}
		if (Math.abs(number.scale()) > MAX_SCALE) {
			throw invalidNumber(name);
		}
		return number;
	}

	private static BadRequestException invalidNumber(String name) {
		return new BadRequestException("Parameter '" + name + "' must be a valid number");
	}

	private static void sendError(HttpExchange exchange, int status, String reason, String message) throws IOException {
		send(exchange, status, "{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"}".formatted(status, reason, message));
	}

	private static void send(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
		System.out.printf("%s %s -> %d%n", exchange.getRequestMethod(), exchange.getRequestURI(), status);
	}

}
