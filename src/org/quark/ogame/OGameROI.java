package org.quark.ogame;

import java.time.Duration;
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
	private final SettableValue<Boolean> withFusion;

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
		withFusion = new SimpleSettableValue<>(boolean.class, false);
		withFusion.set(false, null);
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

	public SettableValue<Boolean> isWithFusion() {
		return withFusion;
	}

	public ROIComputation compute() {
		return new ROIComputation(theUniSpeed.get(), thePlanetTemp.get(), withFusion.get(), theMetalTradeRate.get(),
				theCrystalTradeRate.get(),
				theDeutTradeRate.get());
	}

	public static class ROIComputation implements Spliterator<OGameImprovement> {
		private final OGameState theState;
		private final double[] theTradeRates;
		private double[] theCurrentProduction;

		ROIComputation(int uniSpeed, int planetTemp, boolean withFusion, double metalTradeRate, double crystalTradeRate,
				double deutTradeRate) {
			theTradeRates = new double[] { metalTradeRate, crystalTradeRate, deutTradeRate };
			theState = new OGameState(new OGameRules(), uniSpeed, planetTemp, withFusion);
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
			OGameImprovementType bestType = null;
			Duration bestROI = null;
			if (theState.isWithFusion() && theState.getEnergyTech() == 0 && theState.getSatelliteEnergy() > 15000) {
				// Make the switch to fusion
				for (int i = 0; i < 10; i++) {
					theState.upgrade(OGameImprovementType.Energy).effect();
				}
				action.accept(new OGameImprovement(theState, OGameImprovementType.Energy, 10, bestROI));
				for (int i = 0; i < 10; i++) {
					theState.upgrade(OGameImprovementType.Fusion).effect();
				}
				// This is actually bad in general, but here it's ok because of how this is used
				action.accept(new OGameImprovement(theState, OGameImprovementType.Fusion, 10, bestROI));
				return true;
			}
			OGameCost bestCost = null;
			for (OGameImprovementType type : OGameImprovementType.values()) {
				if (type.helpers.isEmpty()) {
					continue; // A helper improvement, no upgrade benefit by itself
				}
				OGameCost cost = theState.getImprovementCost(type);
				OGameState.Upgrade upgrade = theState.upgrade(type);
				double[] production = theState.getProduction();
				Duration roi = calculateROI(cost, theCurrentProduction, production);
				if (bestROI == null || roi.compareTo(bestROI) < 0) {
					bestType = type;
					bestCost = cost;
					bestROI = roi;
				}
				upgrade.undo();
			}
			// See if any helpers' upgrade time improvements make enough difference
			for (boolean helped = true; helped;) {
				helped = false;
				double[] preProduction = theState.getProduction();
				for (int i = 0; i < bestType.helpers.size(); i++) {
					OGameState.Upgrade preHelpUpgrade = theState.upgrade(bestType);
					double[] postProduction = theState.getProduction();
					Duration preUpgradeTime = preHelpUpgrade.getCost().getUpgradeTime();
					preHelpUpgrade.undo();
					double addedProductionRate = calcValue(postProduction[0] - preProduction[0], postProduction[1] - preProduction[1],
							postProduction[2] - preProduction[2]);

					OGameState.Upgrade helperUpgrade = theState.upgrade(bestType.helpers.get(i));
					double helperCost = calcValueCost(helperUpgrade.getCost());

					OGameState.Upgrade postHelpUpgrade = theState.upgrade(bestType);
					Duration postUpgradeTime = postHelpUpgrade.getCost().getUpgradeTime();
					postHelpUpgrade.undo();
					Duration upgradeTimeDiff = preUpgradeTime.minus(postUpgradeTime);

					double addedProduction = addedProductionRate * (upgradeTimeDiff.getSeconds() / 3600.0);
					// addedProduction is now the amount of extra value that would be generated
					// as a result of finishing the upgrade sooner because of the helper.
					// But the helper actually helps more than that.
					// It will generate increased production for future upgrades as well.
					// The question we need answered is whether the helper will generate value enough to cover its cost
					// faster than upgrading something else.
					// We'll apply a multiplier to approximate how much value the helper will create over the current ROI time
					double roiMult = calcValue(postProduction[0], postProduction[1], postProduction[2]) / calcValueCost(bestCost)//
						* (bestROI.getSeconds() / 3600.0);
					addedProduction *= roiMult;

					if (addedProduction >= helperCost) {
						helped = true;
						action.accept(new OGameImprovement(theState, bestType.helpers.get(i), helperUpgrade.effect(), bestROI));
						System.out.println(bestType.helpers.get(i) + ": " + theState.getAccountValue());
					} else {
						helperUpgrade.undo();
					}
				}
			}
			int level = theState.upgrade(bestType).effect();
			theCurrentProduction = theState.getProduction();
			action.accept(new OGameImprovement(theState, bestType, level, bestROI));
			System.out.println(bestType + ": " + theState.getAccountValue());
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
			return Duration.ofSeconds((int) (hours * 60 * 60));
		}
	}
}
