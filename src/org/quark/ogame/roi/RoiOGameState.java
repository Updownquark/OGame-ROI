package org.quark.ogame.roi;

import java.time.Duration;

import org.observe.config.ObservableConfig;
import org.quark.ogame.uni.*;
import org.quark.ogame.uni.OGameEconomyRuleSet.Production;
import org.quark.ogame.uni.OGameEconomyRuleSet.ProductionSource;
import org.quark.ogame.uni.ui.OGameUniGui;

public class RoiOGameState implements UpgradableAccount {
	private final OGameRuleSet theRules;
	private final Account theAccount;
	private final Planet thePlanet;
	private int thePlanetCount;
	private UpgradeCost theAccountValue;

	private double theFusionContribution;
	private double theDailyStorageRequirement;

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

	public RoiOGameState init(int ecoSpeed, int researchSpeed, int planetTemp, boolean miningClass, double fusionContribution,
		double dailyStorage, double metalTradeRate, double crystalTradeRate, double deutTradeRate) {
		theFusionContribution = fusionContribution;
		theDailyStorageRequirement = dailyStorage;
		theAccount.setGameClass(miningClass ? AccountClass.Collector : AccountClass.Unselected)//
		.getUniverse().setCollectorEnergyBonus(10).setCollectorProductionBonus(25).setCrawlerCap(8)//
		.setEconomySpeed(ecoSpeed).setResearchSpeed(researchSpeed)//
		.getTradeRatios().setMetal(metalTradeRate).setCrystal(crystalTradeRate).setDeuterium(deutTradeRate);
		thePlanet.setMinimumTemperature(planetTemp - 20).setMaximumTemperature(planetTemp + 20);
		return this;
	}

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
	public TradeRatios getTradeRates() {
		// TODO Auto-generated method stub
		return null;
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
		if (thePlanetCount >= OGameROI.PLANETS_BEFORE_FUSION && theFusionContribution > 0.0) {
			Production energy = getEnergy();
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
				Upgrade energyUpgrade = upgrade(AccountUpgradeType.FusionReactor, thePlanet.getFusionReactor() + 1);
				UpgradeCost fusionCost = energyUpgrade.getUpgrade().getCost();
				double fusionBuildingCost = getTradeRates().calcValueCost(fusionCost);
				double fusionDeutCost = (production[2] - getProduction()[2])
					/ getTradeRates().getDeuterium() * upgradeROI.getSeconds() / 3600.0;
				double fusionTotalCost = fusionBuildingCost + fusionDeutCost;
				int newFusionEnergy1 = getEnergy().byType.get(ProductionSource.Fusion);
				double fusionEfficiency = (newFusionEnergy1 - fusionEnergy) * thePlanetCount / fusionTotalCost;
				energyUpgrade.revert();
				energyUpgrade = upgrade(AccountUpgradeType.Energy, theAccount.getResearch().getEnergy());
				UpgradeCost energyCost = energyUpgrade.getUpgrade().getCost();
				double energyTotalCost = calcValueCost(energyCost);
				int newFusionEnergy2 = getEnergy().byType.get(ProductionSource.Fusion);
				double energyEfficiency = (newFusionEnergy2 - fusionEnergy) * thePlanetCount / energyTotalCost;
				energyUpgrade.revert();
				int newFusionEnergy3;
				double fusionUtilEfficiency;
				if (thePlanet.getFusionReactorUtilization() < 100) {
					thePlanet.setFusionReactorUtilization(thePlanet.getFusionReactorUtilization() + 10);
					fusionDeutCost = (production[2] - getProduction()[2]) / getTradeRates().getDeuterium() * upgradeROI.getSeconds()
						/ 3600.0;
					newFusionEnergy3 = getEnergy().byType.get(ProductionSource.Fusion);
					fusionUtilEfficiency = (newFusionEnergy3 - fusionEnergy) * thePlanetCount / fusionDeutCost;
					thePlanet.setFusionReactorUtilization(thePlanet.getFusionReactorUtilization() - 10);
				} else {
					newFusionEnergy3 = 0;
					fusionUtilEfficiency = 0;
				}
				if (fusionEfficiency >= energyEfficiency && fusionEfficiency > fusionUtilEfficiency) {
					energyUpgrade = upgrade(AccountUpgradeType.FusionReactor, thePlanet.getFusionReactor() + 1);
					fusionEnergy = newFusionEnergy1;
				} else if (energyEfficiency > fusionUtilEfficiency) {
					energyUpgrade = upgrade(AccountUpgradeType.Energy, theAccount.getResearch().getEnergy());
					fusionEnergy = newFusionEnergy2;
				} else {
					thePlanet.setFusionReactorUtilization(thePlanet.getFusionReactorUtilization() + 10);
					fusionEnergy = newFusionEnergy3;
				}
				energy = getEnergy();
				if (thePlanet.getSolarSatellites() > 0 && energy.totalProduction > energy.totalConsumption) {
					int satEnergy = theRules.economy().getSatelliteEnergy(getAccount(), thePlanet);
					int dropped = (energy.totalProduction - energy.totalConsumption) / satEnergy;
					thePlanet.setSolarSatellites(thePlanet.getSolarSatellites() - dropped);
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
			}
		}
		// See if we need to upgrade storage
		if (theDailyStorageRequirement > 0) {
			for (ResourceType resType : ResourceType.values()) {
				if (resType == ResourceType.Energy)
					continue;
				double storageAmount = theRules.economy().getStorage(thePlanet, resType);
				// No way a single mine upgrade would cause the need for 2 storage levels, but this is for completeness
				while (storageAmount < theDailyStorageRequirement / thePlanetCount * 24 * production[resType.ordinal()]) {
					AccountUpgradeType upgradeType = AccountUpgradeType.getStorage(resType);
					upgrade(upgradeType, upgradeType.getLevel(theAccount, thePlanet));
					storageAmount = theRules.economy().getStorage(thePlanet, resType);
				}
			}
		}
	}
}
