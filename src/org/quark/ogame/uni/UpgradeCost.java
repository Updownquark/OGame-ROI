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
			upgradeTime.plus(other.upgradeTime));
	}

	public UpgradeCost times(int mult) {
		return new UpgradeCost(metal * mult, crystal * mult, deuterium * mult, energy, upgradeTime.multipliedBy(mult));
	}

	public long getMetalValue(TradeRatios tradeRate) {
		long value = metal;
		value += Math.round(crystal * tradeRate.getMetal() / tradeRate.getCrystal());
		value += Math.round(deuterium * tradeRate.getMetal() / tradeRate.getDeuterium());
		return value;
	}
}
