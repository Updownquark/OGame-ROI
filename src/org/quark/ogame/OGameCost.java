package org.quark.ogame;

import java.util.Arrays;

public class OGameCost {
	public static final OGameCost ZERO = new OGameCost(new long[][] { //
			new long[] { 0, 0, 0 }, //
			new long[] { 0, 0, 0 } });

	private final long[][] theCosts;

	public OGameCost(long[] buildingCost, long[] researchCost) {
		this(new long[][] { buildingCost == null ? new long[3] : buildingCost, researchCost == null ? new long[3] : researchCost });
	}

	private OGameCost(long[][] costs) {
		theCosts = costs;
	}

	public OGameCost justBuildings() {
		return new OGameCost(theCosts[0], null);
	}

	public OGameCost justResearch() {
		return new OGameCost(null, theCosts[1]);
	}

	public long getBuildingCost(int resourceType) {
		return theCosts[0][resourceType];
	}

	public long getResearchCost(int resourceType) {
		return theCosts[1][resourceType];
	}

	public long getTotalCost(int resourceType) {
		return theCosts[0][resourceType] + theCosts[1][resourceType];
	}

	public OGameCost plus(OGameCost other) {
		return new OGameCost(add(theCosts, other.theCosts));
	}

	public OGameCost multiply(int num) {
		if (num == 0) {
			return ZERO;
		} else if (num == 1) {
			return this;
		} else {
			return new OGameCost(multiply(theCosts, num));
		}
	}

	public OGameCost divide(int num) {
		if (num == 1) {
			return this;
		} else {
			return new OGameCost(divide(theCosts, num));
		}
	}

	public double getValue(double metalTradeRate, double crystalTradeRate, double deutTradeRate) {
		return getTotalCost(0) * metalTradeRate + getTotalCost(1) * crystalTradeRate + getTotalCost(2) * deutTradeRate;
	}

	private static long[][] copy(long[][] a) {
		long[][] copy = new long[a.length][];
		for (int i = 0; i < a.length; i++) {
			copy[i] = a[i].clone();
		}
		return copy;
	}

	private static long[][] add(long[][] a1, long[][] a2) {
		long[][] newA = copy(a1);
		for (int i = 0; i < newA.length; i++) {
			for (int j = 0; j < newA[i].length; j++) {
				newA[i][j] += a2[i][j];
			}
		}
		return newA;
	}

	private static long[][] multiply(long[][] a, int mult) {
		long[][] newA = copy(a);
		for (int i = 0; i < newA.length; i++) {
			for (int j = 0; j < newA[i].length; j++) {
				newA[i][j] *= mult;
			}
		}
		return newA;
	}

	private static long[][] divide(long[][] a, int div) {
		long[][] newA = copy(a);
		for (int i = 0; i < newA.length; i++) {
			for (int j = 0; j < newA[i].length; j++) {
				newA[i][j] /= div;
			}
		}
		return newA;
	}

	@Override
	public String toString() {
		return Arrays.deepToString(theCosts);
	}
}
