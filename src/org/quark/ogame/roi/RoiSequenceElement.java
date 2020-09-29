package org.quark.ogame.roi;

import org.quark.ogame.uni.AccountUpgradeType;

public class RoiSequenceElement {
	public final AccountUpgradeType upgrade;
	public final int planetIndex;
	public final long roi;
	private int theTargetLevel;
	private long theTime;

	public RoiSequenceElement(AccountUpgradeType upgrade, int planetIndex, long roi) {
		this.upgrade = upgrade;
		this.planetIndex = planetIndex;
		this.roi = roi;
	}

	public int getTargetLevel() {
		return theTargetLevel;
	}

	RoiSequenceElement setTargetLevel(int targetLevel) {
		theTargetLevel = targetLevel;
		return this;
	}

	public long getTime() {
		return theTime;
	}

	void setTime(long time) {
		theTime = time;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder(upgrade.name());
		if (planetIndex >= 0) {
			str.append('@').append(planetIndex);
		}
		if (theTargetLevel > 0) {
			str.append(':').append(theTargetLevel);
		}
		return str.toString();
	}
}
