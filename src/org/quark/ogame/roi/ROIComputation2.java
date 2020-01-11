package org.quark.ogame.roi;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

import org.quark.ogame.uni.AccountUpgrade;
import org.quark.ogame.uni.UpgradeCost;

public class ROIComputation2 implements Spliterator<AccountUpgrade> {
	private final UpgradableAccount theState;
	private long[] theCurrentProduction;
	private final boolean isWithAggressiveHelpers;

	ROIComputation2(UpgradableAccount account, boolean aggressiveHelpers) {
		theState = account;
		isWithAggressiveHelpers = aggressiveHelpers;
		theCurrentProduction = theState.getProduction();
	}

	public long[] getCurrentProduction() {
		return theCurrentProduction;
	}

	@Override
	public boolean tryAdvance(Consumer<? super AccountUpgrade> action) {
		List<OGameImprovementType> bestType = new ArrayList<>();
		Duration bestROI = null;
		AccountUpgrade bestUpgrade = null;
		long[] postUpgradeProduction = null;
		for (UpgradableAccount.Upgrade upgrade : theState.getAvailableProductionUpgrades()) {
			UpgradeCost cost = upgrade.getUpgrade().getCost();
			long[] production = theState.getProduction();
			Duration roi = calculateROI(cost, theCurrentProduction, production);
			if (bestROI == null || roi.compareTo(bestROI) < 0) {
				bestUpgrade = upgrade.getUpgrade();
				bestROI = roi;
				postUpgradeProduction = production;
			}
			upgrade.revert();
		}
		// See if any helpers' upgrade time improvements make enough difference
		Set<OGameImprovementType> helpers = new LinkedHashSet<>();
		for (OGameImprovementType type : bestType) {
			helpers.addAll(type.helpers);
		}
		for (boolean helped = true; helped;) {
			helped = false;
			double addedProductionRate = calcValue(//
				postUpgradeProduction[0] - theCurrentProduction[0], //
				postUpgradeProduction[1] - theCurrentProduction[1], //
				postUpgradeProduction[2] - theCurrentProduction[2]);
			for (UpgradableAccount.Upgrade helper : theState.getBuildHelpers(bestUpgrade.getType())) {
				double helperCost = calcValueCost(helper.getUpgrade().getCost());
				UpgradableAccount.Upgrade postHelpUpgrade = theState.upgrade(bestUpgrade.getType(), bestUpgrade.getToLevel());
				Duration postUpgradeTime = helper.getUpgrade().getCost().getUpgradeTime()//
					.plus(postHelpUpgrade.getUpgrade().getCost().getUpgradeTime());
				postHelpUpgrade.revert();
				if (postUpgradeTime.compareTo(bestUpgrade.getCost().getUpgradeTime()) >= 0) {
					helper.revert();
					continue;
				}
				Duration upgradeTimeDiff = bestUpgrade.getCost().getUpgradeTime().minus(postUpgradeTime);
				double addedProduction = addedProductionRate * (upgradeTimeDiff.getSeconds() / 3600.0);
				if (isWithAggressiveHelpers) {
					// addedProduction is now the amount of extra value that would be generated
					// as a result of finishing the upgrade sooner because of the helper.
					// But the helper actually helps more than that.
					// It will generate increased production for future upgrades as well.
					// The question we need answered is whether the helper will generate value enough to cover its cost
					// faster than upgrading something else.
					// We'll apply a multiplier to approximate how much value the helper will create over the current ROI time
					double roiMult = calcValue(postUpgradeProduction[0], postUpgradeProduction[1], postUpgradeProduction[2])
						/ calcValueCost(bestUpgrade.getCost())//
						* (bestROI.getSeconds() / 3600.0);
					addedProduction *= roiMult;
				}

				if (addedProduction >= helperCost) {
					helped = true;
				} else {
					helper.revert();
				}
			}
		}

		theState.upgrade(bestUpgrade.getType(), bestUpgrade.getToLevel());
		action.accept(bestUpgrade);
		theState.postUpgradeCheck(postUpgradeProduction, bestROI);
		theCurrentProduction = theState.getProduction();
		return true;
	}

	@Override
	public Spliterator<AccountUpgrade> trySplit() {
		return null;
	}

	@Override
	public long estimateSize() {
		return Long.MAX_VALUE;
	}

	@Override
	public int characteristics() {
		return 0;
	}

	private double calcValueCost(UpgradeCost upgradeCost) {
		return calcValue(upgradeCost.getMetal(), upgradeCost.getCrystal(), upgradeCost.getDeuterium());
	}

	private double calcValue(long metal, long crystal, long deuterium) {
		return metal / theState.getTradeRates().getMetal()//
			+ crystal / theState.getTradeRates().getCrystal()//
			+ deuterium / theState.getTradeRates().getDeuterium();
	}

	private Duration calculateROI(UpgradeCost upgradeCost, long[] previousProduction, long[] postProduction) {
		double valueCost = calcValueCost(upgradeCost);
		double productionValueDiff = (postProduction[0] - previousProduction[0])
			/ theState.getTradeRates().getMetal()//
			+ (postProduction[1] - previousProduction[1]) / theState.getTradeRates().getCrystal()//
			+ (postProduction[2] - previousProduction[2]) / theState.getTradeRates().getDeuterium();
		double hours = valueCost / productionValueDiff;
		if (hours < 0) {
			return Duration.ofDays(365000000);
		}
		return Duration.ofSeconds((long) (hours * 60 * 60));
	}
}