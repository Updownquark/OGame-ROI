package org.quark.ogame.roi;

import org.observe.SettableValue;
import org.observe.SimpleSettableValue;

public class OGameROI {
	private final SettableValue<Integer> theEconomySpeed;
	private final SettableValue<Integer> theResearchSpeed;
	private final SettableValue<Integer> thePlanetTemp;
	private final SettableValue<Boolean> isUsingCrawlers;
	private final SettableValue<Boolean> isMiningClass;
	private final SettableValue<Double> theMetalTradeRate;
	private final SettableValue<Double> theCrystalTradeRate;
	private final SettableValue<Double> theDeutTradeRate;
	private final SettableValue<Double> theFusionContribution;
	private final SettableValue<Double> theDailyProductionStorageRequired;
	private final SettableValue<Boolean> withAggressiveHelpers;

	public OGameROI() {
		theEconomySpeed = new SimpleSettableValue<>(int.class, false)//
			.filterAccept(v -> v <= 0 ? "Economy speed must be > 0" : null).withValue(7, null);
		theResearchSpeed = new SimpleSettableValue<>(int.class, false)//
			.filterAccept(v -> v <= 0 ? "Resarch speed must be > 0" : null).withValue(7, null);
		thePlanetTemp = new SimpleSettableValue<>(int.class, false).withValue(30, null);
		isMiningClass = new SimpleSettableValue<>(boolean.class, false).withValue(true, null);
		isUsingCrawlers = new SimpleSettableValue<>(boolean.class, false).withValue(true, null);
		theMetalTradeRate = new SimpleSettableValue<>(double.class, false)//
			.filterAccept(v -> v <= 0 ? "Trade rates must be > 0" : null).withValue(2.5, null);
		theCrystalTradeRate = new SimpleSettableValue<>(double.class, false)//
			.filterAccept(v -> v <= 0 ? "Trade rates must be > 0" : null).withValue(1.5, null);
		theDeutTradeRate = new SimpleSettableValue<>(double.class, false)//
			.filterAccept(v -> v <= 0 ? "Trade rates must be > 0" : null).withValue(1.0, null);
		theFusionContribution = new SimpleSettableValue<>(double.class, false)//
			.filterAccept(v -> (v < 0.0 || v > 1.0) ? "Fusion contribution must be between 0 and 100%" : null).withValue(0.0, null);
		theDailyProductionStorageRequired = new SimpleSettableValue<>(double.class, false)//
			.filterAccept(v -> v < 0 ? "Daily storage requirement cannot be negative" : null).withValue(1.0, null);
		withAggressiveHelpers = new SimpleSettableValue<>(boolean.class, false).withValue(false, null);
	}

	public SettableValue<Integer> getEconomySpeed() {
		return theEconomySpeed;
	}

	public SettableValue<Integer> getResearchSpeed() {
		return theResearchSpeed;
	}

	public SettableValue<Integer> getPlanetTemp() {
		return thePlanetTemp;
	}

	public SettableValue<Boolean> getMiningClass() {
		return isMiningClass;
	}

	public SettableValue<Boolean> getUsingCrawlers() {
		return isUsingCrawlers;
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

	public SettableValue<Double> getDailyProductionStorageRequired() {
		return theDailyProductionStorageRequired;
	}

	public SettableValue<Boolean> isWithAggressiveHelpers() {
		return withAggressiveHelpers;
	}

	public ROIComputation compute() {
		return new ROIComputation(theEconomySpeed.get(), theResearchSpeed.get(), thePlanetTemp.get(), isMiningClass.get(),
			isUsingCrawlers.get(), //
			theFusionContribution.get(), theDailyProductionStorageRequired.get(), withAggressiveHelpers.get(), //
			theMetalTradeRate.get(), theCrystalTradeRate.get(), theDeutTradeRate.get());
	}

	/** Switching to fusion before a given economy level causes problems with the algorithm */
	static final int PLANETS_BEFORE_FUSION = 3;
}
