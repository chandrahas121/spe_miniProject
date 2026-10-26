package com.chandrahas.calculator;

public class DivisionByZeroException extends ArithmeticException {

	public DivisionByZeroException() {
		super("Division by zero is not allowed");
	}

}
