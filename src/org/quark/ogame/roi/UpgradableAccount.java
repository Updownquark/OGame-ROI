package org.quark.ogame.roi;

import java.time.Duration;

import org.quark.ogame.uni.AccountUpgrade;
import org.quark.ogame.uni.AccountUpgradeType;
import org.quark.ogame.uni.TradeRatios;

public interface UpgradableAccount {
	interface Upgrade {
		AccountUpgrade getUpgrade();

		void revert();
	}

	Iterable<Upgrade> getAvailableProductionUpgrades();

	Iterable<Upgrade> getBuildHelpers(AccountUpgradeType type);

	Upgrade upgrade(AccountUpgradeType type, int level);

	void postUpgradeCheck(long[] production, Duration upgradeROI);

	TradeRatios getTradeRates();

	long[] getProduction();
}
