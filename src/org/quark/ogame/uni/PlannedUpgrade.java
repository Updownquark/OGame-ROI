package org.quark.ogame.uni;

public interface PlannedUpgrade {
	AccountUpgradeType getType();
	PlannedUpgrade setType(AccountUpgradeType type);

	long getPlanet();
	PlannedUpgrade setPlanet(long location);

	boolean isMoon();
	PlannedUpgrade setMoon(boolean moon);

	int getQuantity();
	PlannedUpgrade setQuantity(int quantity);
}
