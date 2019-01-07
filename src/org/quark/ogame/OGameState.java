package org.quark.ogame;

import java.util.Arrays;

public class OGameState {
	public static interface Upgrade {
		int effect();

		void undo();
	}

	private final OGameRules theRules;
	private final int theUniSpeed;
	private final int theAvgPlanetTemp;
	private final boolean withFusion;
	private final int theSatEnergy;

	private int metal;
	private int crystal;
	private int deut;
	private int fusion;
	private int energy;
	private int plasma;
	private int planets;
	private int theSatellites;

	/** Metal/crystal/deut/fusion %usage */
	private final int[] theUtilizations;
	private final int[] thePreviousUtilizations;
	private final double[] theBuildingCost;
	private final double[] theResearchCost;

	public OGameState(OGameRules rules, int uniSpeed, int planetTemp, boolean withFusion) {
		theRules = rules;
		theUniSpeed = uniSpeed;
		theAvgPlanetTemp = planetTemp;
		theSatEnergy = (int) Math.floor((theAvgPlanetTemp + 160) / 6);
		theUtilizations = new int[] { 100, 100, 100, 100 };
		thePreviousUtilizations = theUtilizations.clone();
		theBuildingCost = new double[3];
		theResearchCost = new double[3];
		this.withFusion = withFusion;

		planets = 1;
	}

	public int getUniSpeed() {
		return theUniSpeed;
	}

	public int getAvgPlanetTemp() {
		return theAvgPlanetTemp;
	}

	public boolean isWithFusion() {
		return withFusion;
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

	public int getBuildingLevel(int buildingType) {
		switch (buildingType) {
		case 0:
			return metal;
		case 1:
			return crystal;
		case 2:
			return deut;
		case 3:
			return fusion;
		}
		throw new IllegalArgumentException("No such building type: " + buildingType);
	}

	public double getUtilization(int type) {
		return theUtilizations[type] / 100.0;
	}

	public double getBuildingCost(int resourceType) {
		return theBuildingCost[resourceType];
	}

	public double[] getProduction() {
		return theRules.getResourceProduction(this);
	}

	public double[] getImprovementCost(OGameImprovementType improvement) {
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
		}
		if (prevLevel < 0) {
			throw new IllegalStateException("Unrecognized improvement type: "+improvement);
		}
		return theRules.getUpgradeCost(this, improvement, prevLevel, prevLevel + 1);
	}

	public Upgrade upgrade(OGameImprovementType improvement) {
		switch (improvement) {
		case Metal:
			return adjustUtilization(//
					() -> metal++, () -> metal--, improvement, metal + 1);
		case Crystal:
			return adjustUtilization(//
					() -> crystal++, () -> crystal--, improvement, crystal + 1);
		case Deut:
			return adjustUtilization(//
					() -> deut++, () -> deut--, improvement, deut + 1);
		case Fusion:
			return adjustUtilization(//
					() -> fusion++, () -> fusion--, improvement, fusion + 1);
		case Energy:
			return adjustUtilization(//
					() -> energy++, () -> energy--, improvement, energy + 1);
		case Plasma:
			plasma++;
			return new Upgrade() {
				@Override
				public int effect() {
					plasma--;
					double[] cost = getImprovementCost(improvement);
					plasma++;
					theResearchCost[0] += cost[0];
					theResearchCost[1] += cost[1];
					theResearchCost[2] += cost[2];
					return plasma;
				}

				@Override
				public void undo() {
					plasma--;
				}
			};
		case Planet:
			planets++;
			return new Upgrade() {
				@Override
				public int effect() {
					planets--;
					double[] cost = getImprovementCost(improvement);
					theResearchCost[0] += cost[0] - theBuildingCost[0];
					theResearchCost[1] += cost[1] - theBuildingCost[1];
					theResearchCost[2] += cost[2] - theBuildingCost[2];
					theBuildingCost[0] *= (planets + 1) / planets;
					theBuildingCost[1] *= (planets + 1) / planets;
					theBuildingCost[2] *= (planets + 1) / planets;
					planets++;
					return planets;
				}

				@Override
				public void undo() {
					planets--;
				}
			};
		}
		throw new IllegalStateException("Unrecognized improvement type: " + improvement);
	}

	private Upgrade adjustUtilization(Runnable effect, Runnable undo, OGameImprovementType type, int newLevel) {
		effect.run();
		if (withFusion && fusion > 0) {
			int preSats = theSatellites;
			theSatellites = 0;
			System.arraycopy(theUtilizations, 0, thePreviousUtilizations, 0, 4);
			Arrays.fill(theUtilizations, 100);
			double[] energyPC = theRules.getEnergyProductionConsumption(this);
			double production = energyPC[0];
			double consumption = energyPC[1];
			if (production > consumption) {
				// Assume that the best production is always with mines running at 100%
				theUtilizations[3] = (int) Math.ceil(consumption / production * 10) * 10;
			} else {
				// For this, we'll assume that we want to maximize energy consumption up to production,
				int utilization = (int) Math.floor(production / consumption * 10) * 10;
				theUtilizations[0] = theUtilizations[1] = theUtilizations[2] = utilization;
				for (int i = 0; i < 3 && energyPC[1] < energyPC[0]; i++) {
					theUtilizations[i] += 10;
					energyPC = theRules.getEnergyProductionConsumption(this);
				}
			}
			return new Upgrade() {
				@Override
				public int effect() {
					undo.run();
					double[] cost = getImprovementCost(type);
					effect.run();
					switch (type) {
					case Metal:
					case Crystal:
					case Deut:
					case Fusion:
						theBuildingCost[0] += cost[0];
						theBuildingCost[1] += cost[1];
						theBuildingCost[2] += cost[2];
						break;
					default:
						theResearchCost[0] += cost[0];
						theResearchCost[1] += cost[1];
						theResearchCost[2] += cost[2];
						break;
					}
					return newLevel;
				}

				@Override
				public void undo() {
					theSatellites = preSats;
					System.arraycopy(thePreviousUtilizations, 0, theUtilizations, 0, 4);
					undo.run();
				}
			};
		} else {
			int prevSats = theSatellites;
			theSatellites = (int) Math.ceil(theRules.getEnergyProductionConsumption(this)[1] / theSatEnergy);
			return new Upgrade() {
				@Override
				public int effect() {
					undo.run();
					double[] cost = getImprovementCost(type);
					effect.run();
					switch (type) {
					case Metal:
					case Crystal:
					case Deut:
					case Fusion:
						theBuildingCost[0] += cost[0];
						theBuildingCost[1] += cost[1];
						theBuildingCost[2] += cost[2];
						break;
					default:
						theResearchCost[0] += cost[0];
						theResearchCost[1] += cost[1];
						theResearchCost[2] += cost[2];
						break;
					}
					return newLevel;
				}

				@Override
				public void undo() {
					theSatellites = prevSats;
					undo.run();
				}
			};
		}
	}
}
