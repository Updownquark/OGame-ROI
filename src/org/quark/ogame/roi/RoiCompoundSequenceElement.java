package org.quark.ogame.roi;

import java.util.Arrays;

import org.quark.ogame.uni.AccountUpgradeType;

public class RoiCompoundSequenceElement {
	public final AccountUpgradeType upgrade;
	public final int level;
	public final int[] planetIndexes;
	public final long time;

	public RoiCompoundSequenceElement(AccountUpgradeType upgrade, int level, int[] planetIndexes, long time) {
		this.upgrade = upgrade;
		this.level = level;
		this.planetIndexes = planetIndexes;
		this.time = time;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder(upgrade.name()).append(' ').append(level);
		if (planetIndexes != null) {
			str.append('@').append(Arrays.toString(planetIndexes));
		}
		return str.toString();
	}
}
