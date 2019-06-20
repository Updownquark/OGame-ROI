package org.quark.ogame;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;

import org.observe.SettableValue;
import org.observe.SimpleSettableValue;

public class OGameROI {
	private final SettableValue<Integer> theUniSpeed;
	private final SettableValue<Integer> thePlanetTemp;
	private final SettableValue<Double> theMetalTradeRate;
	private final SettableValue<Double> theCrystalTradeRate;
	private final SettableValue<Double> theDeutTradeRate;
	private final SettableValue<Double> theFusionContribution;
	private final SettableValue<Boolean> withAggressiveHelpers;

	public OGameROI() {
		theUniSpeed = new SimpleSettableValue<>(int.class, false);
		theUniSpeed.set(7, null);
		thePlanetTemp = new SimpleSettableValue<>(int.class, false);
		thePlanetTemp.set(30, null);
		theMetalTradeRate = new SimpleSettableValue<>(double.class, false);
		theMetalTradeRate.set(2.5, null);
		theCrystalTradeRate = new SimpleSettableValue<>(double.class, false);
		theCrystalTradeRate.set(1.5, null);
		theDeutTradeRate = new SimpleSettableValue<>(double.class, false);
		theDeutTradeRate.set(1.0, null);
		theFusionContribution = new SimpleSettableValue<>(double.class, false)//
			.filterAccept(v -> (v < 0.0 || v > 1.0) ? "Fusion contribution must be between 0 and 1" : null);
		theFusionContribution.set(0.0, null);
		withAggressiveHelpers = new SimpleSettableValue<>(boolean.class, false);
		withAggressiveHelpers.set(false, null);
	}

	public SettableValue<Integer> getUniSpeed() {
		return theUniSpeed;
	}

	public SettableValue<Integer> getPlanetTemp() {
		return thePlanetTemp;
	}

	public SettableValue<Double> getMetalTradeRate() {
		return theMetalTradeRate;
	}

	public SettableValue<Double> getCrystalTradeRate() {
		return theCrystalTradeRate;
	}

	public SettableValue<Double> getDeutTradeRate() {
		return theDeutTradeRate;
	}

	public SettableValue<Double> getFusionContribution() {
		return theFusionContribution;
	}

	public SettableValue<Boolean> isWithAggressiveHelpers() {
		return withAggressiveHelpers;
	}

	public ROIComputation compute() {
		return new ROIComputation(theUniSpeed.get(), thePlanetTemp.get(), theFusionContribution.get(), withAggressiveHelpers.get(), //
			theMetalTradeRate.get(), theCrystalTradeRate.get(), theDeutTradeRate.get());
	}

	/** Switching to fusion before a given economy level causes problems with the algorithm */
	private static final int PLANETS_BEFORE_FUSION = 3;

	public static class ROIComputation implements Spliterator<OGameImprovement> {
		private final OGameState theState;
		private final double[] theTradeRates;
		private final double theFusionContribution;
		private double[] theCurrentProduction;
		private final boolean isWithAggressiveHelpers;
		private int theImprovementCounter; // Just debugging

		ROIComputation(int uniSpeed, int planetTemp, double fusionContribution, boolean aggressiveHelpers, //
			double metalTradeRate, double crystalTradeRate, double deutTradeRate) {
			theTradeRates = new double[] { metalTradeRate, crystalTradeRate, deutTradeRate };
			isWithAggressiveHelpers = aggressiveHelpers;
			theFusionContribution = fusionContribution;
			theState = new OGameState(new OGameRules(), uniSpeed, planetTemp);
			for (int i = 0; i < 12; i++) {
				theState.upgrade(OGameImprovementType.Energy).effect();
			}
			theCurrentProduction = theState.getProduction();
		}

		public OGameState getState() {
			return theState;
		}

		public double[] getCurrentProduction() {
			return theCurrentProduction;
		}

		@Override
		public boolean tryAdvance(Consumer<? super OGameImprovement> action) {
			List<OGameImprovementType> bestType = new ArrayList<>();
			Duration bestROI = null;
			OGameCost bestCost = null;
			double[] postUpgradeProduction = null;
			List<OGameState.Upgrade> tempUpgrades = new ArrayList<>();
			for (OGameImprovementType type : OGameImprovementType.values()) {
				if (type.energyType || type.helpers.isEmpty()) {
					continue; // No upgrade benefit by itself
				}
				OGameCost cost = theState.getImprovementCost(type);
				tempUpgrades.add(theState.upgrade(type));
				double[] production = theState.getProduction();
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
					OGameState.Upgrade helperUpgrade = theState.upgrade(helper);
					double helperCost = calcValueCost(helperUpgrade.getCost());
					Duration postUpgradeTime = Duration.ZERO;
					for (OGameImprovementType type : bestType) {
						OGameState.Upgrade postHelpUpgrade = theState.upgrade(type);
						postUpgradeTime = postUpgradeTime.plus(postHelpUpgrade.getCost().getUpgradeTime());
						tempUpgrades.add(postHelpUpgrade);
					}
					// Undo in reverse order to restore state
					for (int i = tempUpgrades.size() - 1; i >= 0; i--) {
						tempUpgrades.get(i).undo();
					}
					tempUpgrades.clear();

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
			if (theState.getPlanets() >= PLANETS_BEFORE_FUSION && theFusionContribution > 0.0) {
				double[] energy = theState.getEnergyProductionConsumption();
				double fusionEnergy = energy[0] - theState.getSatelliteEnergy();
				int newFusion = 0;
				int newEnergy = 0;
				boolean fusionInit = theState.getBuildingLevel(OGameBuildingType.Fusion) == 0;
				while (fusionEnergy / energy[1] < theFusionContribution) {
					// More fusion power
					OGameCost fusionCost = theState.getImprovementCost(OGameImprovementType.Fusion);
					OGameState.Upgrade energyUpgrade = theState.upgrade(OGameImprovementType.Fusion);
					double fusionBuildingCost = calcValueCost(fusionCost);
					double fusionDeutCost = (postUpgradeProduction[2] - theState.getProduction()[2]) / theTradeRates[2]
						* bestROI.getSeconds() / 3600.0;
					double fusionTotalCost = fusionBuildingCost + fusionDeutCost;
					double newFusionEnergy1 = theState.getEnergyProductionConsumption()[0] - theState.getSatelliteEnergy();
					double fusionEfficiency = (newFusionEnergy1 - fusionEnergy) * theState.getPlanets() / fusionTotalCost;
					energyUpgrade.undo();
					OGameCost energyCost = theState.getImprovementCost(OGameImprovementType.Energy);
					energyUpgrade = theState.upgrade(OGameImprovementType.Energy);
					double energyTotalCost = calcValueCost(energyCost);
					double newFusionEnergy2 = theState.getEnergyProductionConsumption()[0] - theState.getSatelliteEnergy();
					double energyEfficiency = (newFusionEnergy2 - fusionEnergy) * theState.getPlanets() / energyTotalCost;
					energyUpgrade.undo();
					if (fusionEfficiency >= energyEfficiency) {
						bestCost = fusionCost;
						energyUpgrade = theState.upgrade(OGameImprovementType.Fusion);
						newFusion = energyUpgrade.effect();
						if (!fusionInit) {
							action.accept(new OGameImprovement(theState, OGameImprovementType.Fusion, newFusion, null));
							System.out.println(OGameImprovementType.Fusion + " " + newFusion);
						}
						fusionEnergy = newFusionEnergy1;
					} else {
						bestCost = energyCost;
						energyUpgrade = theState.upgrade(OGameImprovementType.Energy);
						newEnergy = energyUpgrade.effect();
						if (!fusionInit) {
							action.accept(new OGameImprovement(theState, OGameImprovementType.Energy, newEnergy, null));
							System.out.println(OGameImprovementType.Energy + " " + newEnergy);
						}
						fusionEnergy = newFusionEnergy2;
					}
					energy = theState.getEnergyProductionConsumption();
					postUpgradeProduction = theState.getProduction();
				}
				if (fusionInit) {
					action.accept(new OGameImprovement(theState, OGameImprovementType.Fusion, newFusion, null));
					System.out.println(OGameImprovementType.Fusion + " " + newFusion);
					action.accept(new OGameImprovement(theState, OGameImprovementType.Energy, newEnergy, null));
					System.out.println(OGameImprovementType.Energy + " " + newEnergy);
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

		private double calcValueCost(OGameCost upgradeCost) {
			return calcValue(upgradeCost.getTotalCost(0), upgradeCost.getTotalCost(1), upgradeCost.getTotalCost(2));
		}

		private double calcValue(double metal, double crystal, double deuterium) {
			return metal / theTradeRates[0] + crystal / theTradeRates[1] + deuterium / theTradeRates[2];
		}

		private Duration calculateROI(OGameCost upgradeCost, double[] previousProduction, double[] postProduction) {
			double valueCost = calcValueCost(upgradeCost);
			double productionValueDiff = (postProduction[0] - previousProduction[0]) / theTradeRates[0]//
				+ (postProduction[1] - previousProduction[1]) / theTradeRates[1]//
				+ (postProduction[2] - previousProduction[2]) / theTradeRates[2];
			double hours = valueCost / productionValueDiff;
			if (hours < 0) {
				return Duration.ofDays(365000000);
			}
			return Duration.ofSeconds((long) (hours * 60 * 60));
		}
	}
}
