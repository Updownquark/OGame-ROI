package org.quark.ogame.roi;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;

import org.quark.ogame.roi.OGameState2.Upgrade;
import org.quark.ogame.uni.AccountClass;
import org.quark.ogame.uni.OGameRuleSet;
import org.quark.ogame.uni.ResourceType;
import org.quark.ogame.uni.UpgradeCost;
import org.quark.ogame.uni.OGameEconomyRuleSet.Production;
import org.quark.ogame.uni.OGameEconomyRuleSet.ProductionSource;
import org.quark.ogame.uni.versions.OGameRuleSet710;

public class ROIComputation implements Spliterator<OGameImprovement> {
	private final OGameRuleSet theRules;
	private final OGameState2 theState;
	private final boolean isUsingCrawlers;
	private final double theFusionContribution;
	private long[] theCurrentProduction;
	private double theDailyStorageRequirement;
	private final boolean isWithAggressiveHelpers;
	private int theImprovementCounter; // Just debugging

	ROIComputation(int ecoSpeed, int researchSpeed, int planetTemp, boolean miningClass, boolean useCrawlers, double fusionContribution,
		double dailyStorage, boolean aggressiveHelpers, //
		double metalTradeRate, double crystalTradeRate, double deutTradeRate) {
		theRules = new OGameRuleSet710();
		theDailyStorageRequirement = dailyStorage;
		isWithAggressiveHelpers = aggressiveHelpers;
		isUsingCrawlers = useCrawlers;
		theFusionContribution = fusionContribution;
		theState = new OGameState2(theRules);
		theState.getAccount().setGameClass(miningClass ? AccountClass.Collector : AccountClass.Unselected)//
			.getUniverse().setCollectorEnergyBonus(10).setCollectorProductionBonus(25).setCrawlerCap(8)//
			.setEconomySpeed(ecoSpeed).setResearchSpeed(researchSpeed)//
			.getTradeRatios().setMetal(metalTradeRate).setCrystal(crystalTradeRate).setDeuterium(deutTradeRate);
		theState.getPlanet().setMinimumTemperature(planetTemp - 20).setMaximumTemperature(planetTemp + 20);
		theCurrentProduction = theState.getProduction();
	}

	public OGameState2 getState() {
		return theState;
	}

	public long[] getCurrentProduction() {
		return theCurrentProduction;
	}

	@Override
	public boolean tryAdvance(Consumer<? super OGameImprovement> action) {
		List<OGameImprovementType> bestType = new ArrayList<>();
		Duration bestROI = null;
		UpgradeCost bestCost = null;
		long[] postUpgradeProduction = null;
		List<OGameState2.Upgrade> tempUpgrades = new ArrayList<>();
		for (OGameImprovementType type : OGameImprovementType.values()) {
			if (type.energyType || type.isHelper || type.isStorage() != null) {
				continue; // No upgrade benefit by itself
			} else if (type == OGameImprovementType.Crawler && !isUsingCrawlers) {
				continue;
			}
			OGameState2.Upgrade upgrade=theState.upgrade(type);
			tempUpgrades.add(upgrade);
			UpgradeCost cost = upgrade.getCost();
			long[] production = theState.getProduction();
			Duration roi = calculateROI(cost, theCurrentProduction, production);
			if (bestROI == null || roi.compareTo(bestROI) < 0) {
				bestType.clear();
				bestType.add(type);
				bestCost = cost;
				bestROI = roi;
				postUpgradeProduction = production;
			}
			// Undo in reverse order to restore state
			for (int i = tempUpgrades.size() - 1; i >= 0; i--) {
				tempUpgrades.get(i).undo();
			}
			tempUpgrades.clear();
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
			for (OGameImprovementType helper : helpers) {
				OGameState2.Upgrade helperUpgrade = theState.upgrade(helper);
				double helperCost = calcValueCost(helperUpgrade.getCost());
				Duration postUpgradeTime = Duration.ZERO;
				for (OGameImprovementType type : bestType) {
					OGameState2.Upgrade postHelpUpgrade = theState.upgrade(type);
					postUpgradeTime = postUpgradeTime.plus(postHelpUpgrade.getCost().getUpgradeTime());
					tempUpgrades.add(postHelpUpgrade);
				}
				// Undo in reverse order to restore state
				for (int i = tempUpgrades.size() - 1; i >= 0; i--) {
					tempUpgrades.get(i).undo();
				}
				tempUpgrades.clear();

				if (bestCost.getUpgradeTime() == null || postUpgradeTime == null) {
					bestCost.getUpgradeTime();
				}
				Duration upgradeTimeDiff = bestCost.getUpgradeTime().minus(postUpgradeTime);
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
						/ calcValueCost(bestCost)//
						* (bestROI.getSeconds() / 3600.0);
					addedProduction *= roiMult;
				}

				if (addedProduction >= helperCost) {
					helped = true;
					int newLevel = helperUpgrade.effect();
					OGameImprovement improvement = new OGameImprovement(theState, helper, newLevel, null);
					action.accept(improvement);
					System.out.println(helper + " " + newLevel + ": " + theState.getAccountValue());
				} else {
					helperUpgrade.undo();
				}
			}
		}
		boolean first = true;
		for (OGameImprovementType type : bestType) {
			int level = theState.upgrade(type).effect();
			if (first) {
				action.accept(new OGameImprovement(theState, type, level, bestROI));
				System.out.println(type + " " + level + ": " + theState.getAccountValue());
			} else {
				action.accept(new OGameImprovement(theState, type, level, null));
				System.out.println(type + " " + level);
			}
		}
		theCurrentProduction = postUpgradeProduction;
		theImprovementCounter++;
		if (theState.getPlanetCount() >= OGameROI.PLANETS_BEFORE_FUSION && theFusionContribution > 0.0) {
			Production energy = theState.getEnergy();
			double fusionEnergy = energy.byType.get(ProductionSource.Fusion);
			int newFusion = 0;
			int newEnergy = 0;
			int otherEnergy = 0;
			for (ProductionSource src : ProductionSource.values()) {
				switch (src) {
				case Solar:
				case Satellite:
					otherEnergy += energy.byType.get(src);
					break;
				default:
					break;
				}
			}
			boolean fusionInit = theState.getPlanet().getFusionReactor() == 0;
			while (fusionEnergy / (fusionEnergy + otherEnergy) < theFusionContribution) {
				// More fusion power
				OGameState2.Upgrade energyUpgrade = theState.upgrade(OGameImprovementType.Fusion);
				UpgradeCost fusionCost = energyUpgrade.getCost();
				double fusionBuildingCost = calcValueCost(fusionCost);
				double fusionDeutCost = (postUpgradeProduction[2] - theState.getProduction()[2])
					/ theState.getAccount().getUniverse().getTradeRatios().getDeuterium()
					* bestROI.getSeconds() / 3600.0;
				double fusionTotalCost = fusionBuildingCost + fusionDeutCost;
				int newFusionEnergy1 = theState.getEnergy().byType.get(ProductionSource.Fusion);
				double fusionEfficiency = (newFusionEnergy1 - fusionEnergy) * theState.getPlanetCount() / fusionTotalCost;
				energyUpgrade.undo();
				energyUpgrade = theState.upgrade(OGameImprovementType.Energy);
				UpgradeCost energyCost = energyUpgrade.getCost();
				double energyTotalCost = calcValueCost(energyCost);
				int newFusionEnergy2 = theState.getEnergy().byType.get(ProductionSource.Fusion);
				double energyEfficiency = (newFusionEnergy2 - fusionEnergy) * theState.getPlanetCount() / energyTotalCost;
				energyUpgrade.undo();
				int newFusionEnergy3;
				double fusionUtilEfficiency;
				if (theState.getPlanet().getFusionReactorUtilization() < 100) {
					theState.getPlanet().setFusionReactorUtilization(theState.getPlanet().getFusionReactorUtilization() + 10);
					fusionDeutCost = (postUpgradeProduction[2] - theState.getProduction()[2])
						/ theState.getAccount().getUniverse().getTradeRatios().getDeuterium() * bestROI.getSeconds() / 3600.0;
					newFusionEnergy3 = theState.getEnergy().byType.get(ProductionSource.Fusion);
					fusionUtilEfficiency = (newFusionEnergy3 - fusionEnergy) * theState.getPlanetCount() / fusionDeutCost;
					theState.getPlanet().setFusionReactorUtilization(theState.getPlanet().getFusionReactorUtilization() - 10);
				} else {
					newFusionEnergy3 = 0;
					fusionUtilEfficiency = 0;
				}
				if (fusionEfficiency >= energyEfficiency && fusionEfficiency > fusionUtilEfficiency) {
					energyUpgrade = theState.upgrade(OGameImprovementType.Fusion);
					newFusion = energyUpgrade.effect();
					if (!fusionInit) {
						action.accept(new OGameImprovement(theState, OGameImprovementType.Fusion, newFusion, null));
						System.out.println(OGameImprovementType.Fusion + " " + newFusion);
					}
					fusionEnergy = newFusionEnergy1;
				} else if (energyEfficiency > fusionUtilEfficiency) {
					energyUpgrade = theState.upgrade(OGameImprovementType.Energy);
					newEnergy = energyUpgrade.effect();
					if (!fusionInit) {
						action.accept(new OGameImprovement(theState, OGameImprovementType.Energy, newEnergy, null));
						System.out.println(OGameImprovementType.Energy + " " + newEnergy);
					}
					fusionEnergy = newFusionEnergy2;
				} else {
					theState.getPlanet().setFusionReactorUtilization(theState.getPlanet().getFusionReactorUtilization() + 10);
					fusionEnergy = newFusionEnergy3;
				}
				energy = theState.getEnergy();
				if (theState.getPlanet().getSolarSatellites() > 0 && energy.totalProduction > energy.totalConsumption) {
					int satEnergy = theRules.economy().getSatelliteEnergy(theState.getAccount(), theState.getPlanet());
					int dropped = (energy.totalProduction - energy.totalConsumption) / satEnergy;
					theState.getPlanet().setSolarSatellites(theState.getPlanet().getSolarSatellites() - dropped);
				}
				otherEnergy = 0;
				for (ProductionSource src : ProductionSource.values()) {
					switch (src) {
					case Solar:
					case Satellite:
						otherEnergy += energy.byType.get(src);
						break;
					default:
						break;
					}
				}
				postUpgradeProduction = theState.getProduction();
			}
			if (fusionInit) {
				action.accept(new OGameImprovement(theState, OGameImprovementType.Fusion, newFusion, null));
				System.out.println(OGameImprovementType.Fusion + " " + newFusion);
				action.accept(new OGameImprovement(theState, OGameImprovementType.Energy, newEnergy, null));
				System.out.println(OGameImprovementType.Energy + " " + newEnergy);
			}
		}
		// See if we need to upgrade storage
		if (theDailyStorageRequirement > 0) {
			ResourceType resType = bestType.get(0).isMine();
			if (resType != null) {
				double storageAmount = theRules.economy().getStorage(theState.getPlanet(), resType);
				// No way a single mine upgrade would cause the need for 2 storage levels, but this is for completeness
				while (storageAmount < theDailyStorageRequirement / theState.getPlanetCount() * 24
					* theCurrentProduction[resType.ordinal()]) {
					OGameImprovementType upgradeType = OGameImprovementType.getStorageImprovement(resType);
					int level = theState.upgrade(upgradeType).effect();
					action.accept(new OGameImprovement(theState, upgradeType, level, null));
					System.out.println(upgradeType + " " + level);
					storageAmount = theRules.economy().getStorage(theState.getPlanet(), resType);
				}
			}
		}
		return true;
	}

	@Override
	public Spliterator<OGameImprovement> trySplit() {
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
		return metal / theState.getAccount().getUniverse().getTradeRatios().getMetal()//
			+ crystal / theState.getAccount().getUniverse().getTradeRatios().getCrystal()//
			+ deuterium / theState.getAccount().getUniverse().getTradeRatios().getDeuterium();
	}

	private Duration calculateROI(UpgradeCost upgradeCost, long[] previousProduction, long[] postProduction) {
		double valueCost = calcValueCost(upgradeCost);
		double productionValueDiff = (postProduction[0] - previousProduction[0])
			/ theState.getAccount().getUniverse().getTradeRatios().getMetal()//
			+ (postProduction[1] - previousProduction[1]) / theState.getAccount().getUniverse().getTradeRatios().getCrystal()//
			+ (postProduction[2] - previousProduction[2]) / theState.getAccount().getUniverse().getTradeRatios().getDeuterium();
		double hours = valueCost / productionValueDiff;
		if (hours < 0) {
			return Duration.ofDays(365000000);
		}
		return Duration.ofSeconds((long) (hours * 60 * 60));
	}
}