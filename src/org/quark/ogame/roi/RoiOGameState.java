package org.quark.ogame.roi;

import org.observe.config.ObservableConfig;
import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.AccountUpgradeType;
import org.quark.ogame.uni.OGameEconomyRuleSet.Production;
import org.quark.ogame.uni.OGameEconomyRuleSet.ProductionSource;
import org.quark.ogame.uni.OGameRuleSet;
import org.quark.ogame.uni.Planet;
import org.quark.ogame.uni.ResourceType;
import org.quark.ogame.uni.UpgradeCost;
import org.quark.ogame.uni.ui.OGameUniGui;

public class RoiOGameState implements UpgradableAccount {
	private final OGameRuleSet theRules;
	private final Account theAccount;
	private final Planet thePlanet;
	private int thePlanetCount;
	private UpgradeCost theAccountValue;

	private double theFusionContribution;
	private double theDaysProductionStorage;

	public RoiOGameState(OGameRuleSet rules) {
		theRules = rules;
		// The easiest way to synthesize the entity is to back it with a config
		ObservableConfig config = ObservableConfig.createRoot("ogame-roi");
		theAccount = OGameUniGui.getAccounts(config, "state/account").create().create().get();
		thePlanet = theAccount.getPlanets().create().create().get(); // Add the initial planet
		thePlanet.setMetalUtilization(100).setCrystalUtilization(100).setDeuteriumUtilization(100)//
			.setSolarPlantUtilization(100).setFusionReactorUtilization(100).setSolarSatelliteUtilization(100).setCrawlerUtilization(100);
		thePlanetCount = 1;
		theAccountValue = UpgradeCost.ZERO;
	}

	public void init(int ecoSpeed, int researchSpeed, int planetTemp, boolean miningClass, double fusionContribution,
		double dailyStorage) {}

	public Account getAccount() {
		return theAccount;
	}

	public Planet getPlanet() {
		return thePlanet;
	}

	public int getPlanetCount() {
		return thePlanetCount;
	}

	public UpgradeCost getAccountValue() {
		return theAccountValue;
	}

	public Production getEnergy() {
		return theRules.economy().getProduction(theAccount, thePlanet, ResourceType.Energy, 1);
	}

	@Override
	public long[] getProduction() {
		Production energy = theRules.economy().getProduction(theAccount, thePlanet, ResourceType.Energy, 1);
		double energyFactor = energy.totalConsumption == 0 ? 1 : Math.min(1.0, energy.totalProduction * 1.0 / energy.totalConsumption);
		Production metal = theRules.economy().getProduction(theAccount, thePlanet, ResourceType.Metal, energyFactor);
		Production crystal = theRules.economy().getProduction(theAccount, thePlanet, ResourceType.Crystal, energyFactor);
		Production deuterium = theRules.economy().getProduction(theAccount, thePlanet, ResourceType.Deuterium, energyFactor);
		return new long[] { metal.totalNet * thePlanetCount, crystal.totalNet * thePlanetCount, deuterium.totalNet * thePlanetCount };
	}

	@Override
	public Iterable<Upgrade> getAvailableProductionUpgrades() {

		// TODO Auto-generated method stub
	}

	@Override
	public Iterable<Upgrade> getBuildHelpers(AccountUpgradeType type) {
		// TODO Auto-generated method stub
	}

	@Override
	public Upgrade upgrade(AccountUpgradeType type, int level) {
		// TODO Auto-generated method stub
	}

	@Override
	public void postUpgradeCheck(long[] production, Duration upgradeROI) {
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
			boolean fusionInit = thePlanet.getFusionReactor() == 0;
			while (fusionEnergy / (fusionEnergy + otherEnergy) < theFusionContribution) {
				// More fusion power
				OGameState2.Upgrade energyUpgrade = upgrade(OGameImprovementType.Fusion);
				UpgradeCost fusionCost = energyUpgrade.getCost();
				double fusionBuildingCost = calcValueCost(fusionCost);
				double fusionDeutCost = (production[2] - getProduction()[2])
					/ theState.getAccount().getUniverse().getTradeRatios().getDeuterium() * bestROI.getSeconds() / 3600.0;
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
					fusionDeutCost = (production[2] - theState.getProduction()[2])
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
	}
}
