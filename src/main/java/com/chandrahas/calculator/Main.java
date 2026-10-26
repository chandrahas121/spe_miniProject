package com.chandrahas.calculator;

import java.io.IOException;

public class Main {

	public static void main(String[] args) throws IOException {
		int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8081"));

		CalculatorServer server = new CalculatorServer(port, new Calculator());
		// docker stop sends SIGTERM, this lets the server shut down cleanly
		Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
		server.start();

		System.out.println("Calculator API listening on port " + port);
	}

}
