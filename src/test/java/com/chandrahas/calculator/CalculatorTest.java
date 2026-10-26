package com.chandrahas.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class CalculatorTest {

	private final Calculator calculator = new Calculator();

	private static BigDecimal n(String value) {
		return new BigDecimal(value);
	}

	// compareTo so that 10 and 10.0 count as equal
	private static void assertNumber(String expected, BigDecimal actual) {
		assertEquals(0, n(expected).compareTo(actual), "expected " + expected + " but got " + actual);
	}

	@Test
	void addsTwoNumbers() {
		assertNumber("5", calculator.add(n("2"), n("3")));
	}

	@Test
	void addsNegativeNumbers() {
		assertNumber("-7", calculator.add(n("-3"), n("-4")));
	}

	@Test
	void addDoesNotLosePrecision() {
		assertNumber("0.3", calculator.add(n("0.1"), n("0.2")));
	}

	@Test
	void subtractsTwoNumbers() {
		assertNumber("-1", calculator.subtract(n("2"), n("3")));
	}

	@Test
	void subtractsDecimals() {
		assertNumber("1.25", calculator.subtract(n("3.5"), n("2.25")));
	}

	@Test
	void multipliesTwoNumbers() {
		assertNumber("12", calculator.multiply(n("3"), n("4")));
	}

	@Test
	void multiplyByZeroGivesZero() {
		assertNumber("0", calculator.multiply(n("123.45"), n("0")));
	}

	@Test
	void multipliesNegativeByPositive() {
		assertNumber("-10", calculator.multiply(n("-2.5"), n("4")));
	}

	@Test
	void dividesTwoNumbers() {
		assertNumber("2.5", calculator.divide(n("10"), n("4")));
	}

	@Test
	void dividesEvenly() {
		assertNumber("2", calculator.divide(n("6"), n("3")));
	}

	@Test
	void divisionThatNeverEndsIsRounded() {
		assertNumber("0.3333333333333333", calculator.divide(n("1"), n("3")));
	}

	@Test
	void zeroDividedByNumberIsZero() {
		assertNumber("0", calculator.divide(n("0"), n("5")));
	}

	@Test
	void divideByZeroThrows() {
		DivisionByZeroException ex = assertThrows(DivisionByZeroException.class,
				() -> calculator.divide(n("5"), n("0")));
		assertEquals("Division by zero is not allowed", ex.getMessage());
	}

	@Test
	void divideByDecimalZeroThrows() {
		assertThrows(DivisionByZeroException.class, () -> calculator.divide(n("5"), n("0.00")));
	}

}
