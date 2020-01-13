package org.quark.ogame;

import java.text.DecimalFormat;

import org.qommons.io.Format;

public class OGameUtils {
	private static final Format<Double> FORMAT = Format.doubleFormat(5).withExpCondition(4, 0)//
		.withPrefix("K", 3)//
		.withPrefix("M", 6)//
		.withPrefix("B", 9)//
		.withPrefix("T", 12)//
		.withPrefix("Q", 12)//
		.build();

	public static String printResourceAmount(double amount) {
		return FORMAT.format(amount);
	}

	public static final DecimalFormat WHOLE_FORMAT = new DecimalFormat("#,##0");
	public static final DecimalFormat TWO_DIGIT_FORMAT = new DecimalFormat("#,##0.00");
	public static final DecimalFormat THREE_DIGIT_FORMAT = new DecimalFormat("#,##0.000");
}
