package org.quark.ogame;

import java.text.DecimalFormat;

public class OGameUtils {

	public static String printResourceAmount(double amount) {
		StringBuilder str = new StringBuilder();
		if (amount < 0) {
			str.append('-');
			amount = -amount;
		}
		if (amount < 1E6) {
			str.append(OGameUtils.WHOLE_FORMAT.format(amount));
		} else if (amount < 1E9) {
			str.append(OGameUtils.THREE_DIGIT_FORMAT.format(amount / 1E6)).append('M');
		} else if (amount < 1E12) {
			str.append(OGameUtils.THREE_DIGIT_FORMAT.format(amount / 1E9)).append('B');
		} else {
			str.append(OGameUtils.THREE_DIGIT_FORMAT.format(amount / 1E12)).append('T');
		}
		return str.toString();
	}

	public static final DecimalFormat WHOLE_FORMAT = new DecimalFormat("#,##0");
	public static final DecimalFormat TWO_DIGIT_FORMAT = new DecimalFormat("#,##0.00");
	public static final DecimalFormat THREE_DIGIT_FORMAT = new DecimalFormat("#,##0.000");

}
