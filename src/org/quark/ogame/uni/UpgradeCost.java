package org.quark.ogame.uni;

import java.time.Duration;

public class UpgradeCost {
	public final long metal;
	public final long crystal;
	public final long deuterium;
	public final int energy;
	public final Duration upgradeTime;

	public UpgradeCost(long metal, long crystal, long deuterium, int energy, Duration upgradeTime) {
		this.metal = metal;
		this.crystal = crystal;
		this.deuterium = deuterium;
		this.energy = energy;
		this.upgradeTime = upgradeTime;
	}

	public UpgradeCost plus(UpgradeCost other) {
		return new UpgradeCost(metal + other.metal, crystal + other.crystal, deuterium + other.deuterium, Math.max(energy, other.energy),
			add(upgradeTime, other.upgradeTime));
	}

	public UpgradeCost times(int mult) {
		return new UpgradeCost(metal * mult, crystal * mult, deuterium * mult, energy, upgradeTime.multipliedBy(mult));
	}

	public long getTotal() {
		return metal + crystal + deuterium;
	}

	public long getMetalValue(TradeRatios tradeRate) {
		long value = metal;
		value += Math.round(crystal * tradeRate.getMetal() / tradeRate.getCrystal());
		value += Math.round(deuterium * tradeRate.getMetal() / tradeRate.getDeuterium());
		return value;
	}

	private Duration add(Duration d1, Duration d2) {
		if (d1 == null || d2 == null) {
			return null;
		}
		try {
			return d1.plus(d2);
		} catch (ArithmeticException e) {
			return null;
		}
	}
}