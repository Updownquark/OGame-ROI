package org.quark.ogame;

public class OGameState {
	public static interface Upgrade {
		int effect();

		OGameCost getCost();

		void undo();
	}

	private final OGameRules theRules;
	private final int theUniSpeed;
	private final int theAvgPlanetTemp;
	private final int theSatEnergy;

	private int metal;
	private int crystal;
	private int deut;
	private int fusion;
	private int energy;
	private int plasma;
	private int planets;
	private int theSatellites;

	private int theRobotics;
	private int theNanites;
	private int theResearchLab;
	private int theIRN;

	/** Metal/crystal/deut/fusion %usage */
	private final int[] theUtilizations;
	private final int[] thePreviousUtilizations;
	private OGameCost theAccountValue;

	public OGameState(OGameRules rules, int uniSpeed, int planetTemp) {
		theRules = rules;
		theUniSpeed = uniSpeed;
		theAvgPlanetTemp = planetTemp;
		theSatEnergy = (int) Math.floor((theAvgPlanetTemp + 160) / 6);
		theUtilizations = new int[] { 100, 100, 100, 100 };
		thePreviousUtilizations = theUtilizations.clone();
		theAccountValue = OGameCost.ZERO;

		planets = 1;
	}

	public int getUniSpeed() {
		return theUniSpeed;
	}

	public int getAvgPlanetTemp() {
		return theAvgPlanetTemp;
	}

	public double getSatelliteEnergy() {
		return theSatellites * 1.0 * theSatEnergy;
	}

	public int getPlanets() {
		return planets;
	}

	public int getPlasmaTech() {
		return plasma;
	}

	public int getEnergyTech() {
		return energy;
	}

	public double[] getEnergyProductionConsumption() {
		return theRules.getEnergyProductionConsumption(this);
	}

	public int getIRN() {
		return theIRN;
	}

	public int getBuildingLevel(OGameBuildingType buildingType) {
		switch (buildingType) {
		case Metal:
			return metal;
		case Crystal:
			return crystal;
		case Deuterium:
			return deut;
		case Fusion:
			return fusion;
		case Robotics:
			return theRobotics;
		case Nanite:
			return theNanites;
		case ResearchLab:
			return theResearchLab;
		}
		throw new IllegalArgumentException("No such building type: " + buildingType);
	}

	public double getUtilization(int type) {
		return theUtilizations[type] / 100.0;
	}

	public OGameCost getAccountValue() {
		return theAccountValue;
	}

	public double[] getProduction() {
		return theRules.getResourceProduction(this);
	}

	public OGameCost getImprovementCost(OGameImprovementType improvement) {
		int prevLevel = -1;
		switch (improvement) {
		case Metal:
			prevLevel = metal;
			break;
		case Crystal:
			prevLevel = crystal;
			break;
		case Deut:
			prevLevel = deut;
			break;
		case Plasma:
			prevLevel = plasma;
			break;
		case Planet:
			prevLevel = planets;
			break;
		case Fusion:
			prevLevel = fusion;
			break;
		case Energy:
			prevLevel = energy;
			break;
		case Robotics:
			prevLevel = theRobotics;
			break;
		case Nanite:
			prevLevel = theNanites;
			break;
		case ResearchLab:
			prevLevel = theResearchLab;
			break;
		case IRN:
			prevLevel = theIRN;
			break;
		}
		if (prevLevel < 0) {
			throw new IllegalStateException("Unrecognized improvement type: " + improvement);
		}
		return theRules.getUpgradeCost(this, improvement, prevLevel, prevLevel + 1);
	}

	public Upgrade upgrade(OGameImprovementType improvement) {
		switch (improvement) {
		case Metal:
			return testUpgrade(//
					() -> metal++, () -> metal--, improvement, metal + 1, true);
		case Crystal:
			return testUpgrade(//
					() -> crystal++, () -> crystal--, improvement, crystal + 1, true);
		case Deut:
			return testUpgrade(//
					() -> deut++, () -> deut--, improvement, deut + 1, true);
		case Fusion:
			return testUpgrade(//
					() -> fusion++, () -> fusion--, improvement, fusion + 1, true);
		case Energy:
			return testUpgrade(//
					() -> energy++, () -> energy--, improvement, energy + 1, true);
		case Plasma:
			return testUpgrade(//
					() -> plasma++, () -> plasma--, improvement, plasma + 1, false);
		case Planet:
			return testUpgrade(//
					() -> planets++, () -> planets--, improvement, planets + 1, false);
		case Robotics:
			return testUpgrade(//
					() -> theRobotics++, () -> theRobotics--, improvement, theRobotics + 1, false);
		case Nanite:
			if (theRobotics < 10) { // Can't upgrade nanite if robotics<10
				return testUpgrade(//
						() -> theRobotics++, () -> theRobotics--, improvement, theRobotics + 1, false);
			} else {
				return testUpgrade(//
						() -> theNanites++, () -> theNanites--, improvement, theNanites + 1, false);
			}
		case ResearchLab:
			return testUpgrade(//
					() -> theResearchLab++, () -> theResearchLab--, improvement, theResearchLab + 1, false);
		case IRN:
			return testUpgrade(//
					() -> theIRN++, () -> theIRN--, improvement, theIRN + 1, false);
		}
		throw new IllegalStateException("Unrecognized improvement type: " + improvement);
	}

	private Upgrade testUpgrade(Runnable effect, Runnable undo, OGameImprovementType type, int newLevel, boolean affectsUtilization) {
		OGameCost cost = getImprovementCost(type);
		effect.run();
		int preSats = theSatellites;
		if (affectsUtilization) {
			double[] energyPC;
			theSatellites = 0;
			if (fusion > 0) {
				System.arraycopy(theUtilizations, 0, thePreviousUtilizations, 0, 4);
				theUtilizations[3] = 100;
				energyPC = theRules.getEnergyProductionConsumption(this);
				double production = energyPC[0];
				double consumption = energyPC[1];
				if (production > consumption) {
					// Assume that the best production is always with mines running at 100%
					theUtilizations[3] = (int) Math.ceil(consumption / production * 10) * 10;
				}
			} else {
				energyPC = theRules.getEnergyProductionConsumption(this);
			}
			double requiredEnergy = energyPC[1] - energyPC[0];
			theSatellites = Math.max(0, (int) Math.ceil(requiredEnergy / theSatEnergy));
		}
		return new Upgrade() {
			@Override
			public int effect() {
				theAccountValue = theAccountValue.plus(cost);
				return newLevel;
			}

			@Override
			public OGameCost getCost() {
				return cost;
			}

			@Override
			public void undo() {
				undo.run();
				if (affectsUtilization) {
					theSatellites = preSats;
					System.arraycopy(thePreviousUtilizations, 0, theUtilizations, 0, 4);
				}
			}
		};
	}
}
