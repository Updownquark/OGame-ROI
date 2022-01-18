package org.quark.ogame.roi;

import org.observe.SettableValue;
import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.AccountUpgradeType;
import org.quark.ogame.uni.OGameEconomyRuleSet;
import org.quark.ogame.uni.RockyBody;
import org.quark.ogame.uni.UpgradeCost;

public class DefenseRatios {
	private final SettableValue<Integer> theRocketLaunchers;
	private final SettableValue<Integer> theLightLasers;
	private final SettableValue<Integer> theHeavyLasers;
	private final SettableValue<Integer> theGaussCannons;
	private final SettableValue<Integer> theIonCannons;
	private final SettableValue<Integer> thePlasmaTurrets;

	public DefenseRatios() {
		theRocketLaunchers = SettableValue.build(int.class).withValue(0).build();
		theLightLasers = SettableValue.build(int.class).withValue(0).build();
		theHeavyLasers = SettableValue.build(int.class).withValue(0).build();
		theGaussCannons = SettableValue.build(int.class).withValue(0).build();
		theIonCannons = SettableValue.build(int.class).withValue(0).build();
		thePlasmaTurrets = SettableValue.build(int.class).withValue(0).build();
	}

	public SettableValue<Integer> getRocketLaunchers() {
		return theRocketLaunchers;
	}

	public SettableValue<Integer> getLightLasers() {
		return theLightLasers;
	}

	public SettableValue<Integer> getHeavyLasers() {
		return theHeavyLasers;
	}

	public SettableValue<Integer> getGaussCannons() {
		return theGaussCannons;
	}

	public SettableValue<Integer> getIonCannons() {
		return theIonCannons;
	}

	public SettableValue<Integer> getPlasmaTurrets() {
		return thePlasmaTurrets;
	}

	public UpgradeCost getTotalCost(Account account, RockyBody body, OGameEconomyRuleSet eco) {
		UpgradeCost cost = UpgradeCost.ZERO;
		int amount;
		amount = theRocketLaunchers.get();
		if (amount > 0) {
			cost = cost.plus(eco.getUpgradeCost(account, body, AccountUpgradeType.RocketLauncher, 0, amount));
		}
		amount = theLightLasers.get();
		if (amount > 0) {
			cost = cost.plus(eco.getUpgradeCost(account, body, AccountUpgradeType.LightLaser, 0, amount));
		}
		amount = theHeavyLasers.get();
		if (amount > 0) {
			cost = cost.plus(eco.getUpgradeCost(account, body, AccountUpgradeType.HeavyLaser, 0, amount));
		}
		amount = theGaussCannons.get();
		if (amount > 0) {
			cost = cost.plus(eco.getUpgradeCost(account, body, AccountUpgradeType.GaussCannon, 0, amount));
		}
		amount = theIonCannons.get();
		if (amount > 0) {
			cost = cost.plus(eco.getUpgradeCost(account, body, AccountUpgradeType.IonCannon, 0, amount));
		}
		amount = thePlasmaTurrets.get();
		if (amount > 0) {
			cost = cost.plus(eco.getUpgradeCost(account, body, AccountUpgradeType.PlasmaTurret, 0, amount));
		}

		return cost;
	}
}
