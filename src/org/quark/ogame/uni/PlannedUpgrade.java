package org.quark.ogame.uni;

public interface PlannedUpgrade {
	AccountUpgradeType getType();
	PlannedUpgrade setType(AccountUpgradeType type);

	RockyBody getLocation();
	PlannedUpgrade setLocation(RockyBody location);
}
