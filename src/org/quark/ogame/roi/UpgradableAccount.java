package org.quark.ogame.roi;

import org.quark.ogame.uni.AccountUpgrade;
import org.quark.ogame.uni.AccountUpgradeType;

public interface UpgradableAccount {
	interface Upgrade {
		AccountUpgrade getUpgrade();

		void revert();
	}

	Iterable<Upgrade> getAvailableProductionUpgrades();

	Iterable<Upgrade> getBuildHelpers(AccountUpgradeType type);

	Upgrade upgrade(AccountUpgradeType type, int level);

	void postUpgradeCheck(long[] production, Duration upgradeROI);

	long[] getProduction();
}
