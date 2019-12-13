package org.quark.ogame;

import java.time.Duration;
import java.util.Arrays;

public class OGameCost {
	public static final OGameCost ZERO = new OGameCost(new long[][] { //
			new long[] { 0, 0, 0 }, //
					new long[] { 0, 0, 0 } }, //
			new Duration[] { Duration.ZERO, Duration.ZERO });

	private final long[][] theCosts;
	private final Duration[] theUpgradeTime;

	public OGameCost(long[] buildingCost, long[] researchCost, Duration buildingUpgradeTime, Duration researchTime) {
		this(new long[][] { buildingCost == null ? new long[3] : buildingCost, researchCost == null ? new long[3] : researchCost }, //
				new Duration[] { buildingUpgradeTime == null ? Duration.ZERO : buildingUpgradeTime, //
						researchTime == null ? Duration.ZERO : researchTime });
	}

	private OGameCost(long[][] costs, Duration[] upgradeTime) {
		theCosts = costs;
		theUpgradeTime = upgradeTime;
	}

	public OGameCost justBuildings() {
		return new OGameCost(theCosts[0], null, theUpgradeTime[0], null);
	}

	public OGameCost justResearch() {
		return new OGameCost(null, theCosts[1], null, theUpgradeTime[1]);
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

	public Duration getUpgradeTime() {
		return theUpgradeTime[0].plus(theUpgradeTime[1]);
	}

	public OGameCost noTime() {
		return new OGameCost(theCosts, new Duration[] { Duration.ZERO, Duration.ZERO });
	}

	public OGameCost plus(OGameCost other) {
		return new OGameCost(add(theCosts, other.theCosts), add(theUpgradeTime, other.theUpgradeTime));
	}

	public OGameCost multiply(int num) {
		if (num == 0) {
			return ZERO;
		} else if (num == 1) {
			return this;
		} else {
			return new OGameCost(multiply(theCosts, num), theUpgradeTime); // AccountUpgrade times are not affected by multiplication
		}
	}

	public OGameCost divide(int num) {
		if (num == 1) {
			return this;
		} else {
			return new OGameCost(divide(theCosts, num), theUpgradeTime); // AccountUpgrade times are not affected by division
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

	private static Duration[] add(Duration[] d1, Duration[] d2) {
		Duration[] newD = new Duration[d1.length];
		for (int i = 0; i < newD.length; i++) {
			newD[i] = d1[i].plus(d2[i]);
		}
		return newD;
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
