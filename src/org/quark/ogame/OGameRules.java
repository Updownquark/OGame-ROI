package org.quark.ogame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OGameRules {
	private static abstract class ImprovementScheme {
		private final double theInitialMetalCost;
		private final double theInitialCrystalCost;
		private final double theInitialDeutCost;
		private final double theLevelExponent;

		public ImprovementScheme(double initialMetalCost, double initialCrystalCost, double initialDeutCost, double levelExponent) {
			theInitialMetalCost = initialMetalCost;
			theInitialCrystalCost = initialCrystalCost;
			theInitialDeutCost = initialDeutCost;
			theLevelExponent = levelExponent;
		}

		double[] getUpgradeCost(OGameState initState, int preLevel, int postLevel, boolean research) {
			double[] cost = new double[3];
			if (postLevel == 0) {
				return cost;
			}
			// POW(exp, preLevel)*((1-POW(exp, ABS(postLevel-preLevel)))/(1-exp))
			double levelUp = Math.pow(theLevelExponent, preLevel)
					* ((1 - Math.pow(theLevelExponent, postLevel - preLevel)) / (1 - theLevelExponent));
			cost[0] = theInitialMetalCost * levelUp;
			cost[1] = theInitialCrystalCost * levelUp;
			cost[2] = theInitialDeutCost * levelUp;
			return cost;
		}
	}

	private static class BuildingImprovementScheme extends ImprovementScheme {
		BuildingImprovementScheme(double initialMetalCost, double initialCrystalCost, double initialDeutCost, double levelExponent) {
			super(initialMetalCost, initialCrystalCost, initialDeutCost, levelExponent);
		}

		@Override
		double[] getUpgradeCost(OGameState initState, int preLevel, int postLevel, boolean research) {
			if (research) {
				return new double[3];
			}
			double[] cost = super.getUpgradeCost(initState, preLevel, postLevel, false);
			cost[0] *= initState.getPlanets();
			cost[1] *= initState.getPlanets();
			cost[2] *= initState.getPlanets();
			return cost;
		}
	}

	private static class TechImprovementScheme extends ImprovementScheme {
		TechImprovementScheme(double initialMetalCost, double initialCrystalCost, double initialDeutCost, double levelExponent) {
			super(initialMetalCost, initialCrystalCost, initialDeutCost, levelExponent);
		}

		@Override
		double[] getUpgradeCost(OGameState initState, int preLevel, int postLevel, boolean research) {
			if (!research) {
				return new double[3];
			}
			return super.getUpgradeCost(initState, preLevel, postLevel, true);
		}
	}

	private static class ColonyImprovementScheme extends TechImprovementScheme {
		ColonyImprovementScheme(double initialMetalCost, double initialCrystalCost, double initialDeutCost, double levelExponent) {
			super(initialMetalCost, initialCrystalCost, initialDeutCost, levelExponent);
		}

		@Override
		double[] getUpgradeCost(OGameState initState, int preLevel, int postLevel, boolean research) {
			if (research) {
				return super.getUpgradeCost(initState, preLevel * 2 - 3, postLevel * 2 - 3, true);
			} else {
				double [] cost=new double[3];
				cost[0] = initState.getBuildingCost(0) / initState.getPlanets();
				cost[1] = initState.getBuildingCost(1) / initState.getPlanets();
				cost[2] = initState.getBuildingCost(2) / initState.getPlanets();
				return cost;
			}
		}
	}

	private interface ProductionScheme {
		void addProduction(OGameState state, double[] production, double energyFactor); // Metal/crystal/deut production

		double getEnergyConsumption(OGameState state);
	}

	private static class MineProductionScheme implements ProductionScheme {
		private final int theResourceType;
		private final double theBaseProduction;
		private final double theProductionFactor;
		private final double thePlasmaBonus;
		private final double theTempBonusOffset;
		private final double theTempBonusMult;
		private final int theEnergyMult;

		MineProductionScheme(int resourceType, double baseProduction, double productionFactor, double plasmaBonus, double tempBonusOffset,
				double tempBonusMult, int energyMult) {
			theResourceType = resourceType;
			theBaseProduction = baseProduction;
			theProductionFactor = productionFactor;
			thePlasmaBonus = plasmaBonus;
			theTempBonusOffset = tempBonusOffset;
			theTempBonusMult = tempBonusMult;
			theEnergyMult = energyMult;
		}

		@Override
		public void addProduction(OGameState state, double[] production, double energyFactor) {
			int mineLevel = state.getBuildingLevel(theResourceType);
			double p = theBaseProduction + theProductionFactor * mineLevel * Math.pow(1.1, mineLevel);
			p *= (1 + thePlasmaBonus / 100 * state.getPlasmaTech());// Plasma adjustment
			p *= state.getUtilization(theResourceType);
			p *= Math.min(1, energyFactor);
			p *= state.getUniSpeed() * state.getPlanets();
			production[theResourceType] += p;
		}

		@Override
		public double getEnergyConsumption(OGameState state) {
			int mineLevel = state.getBuildingLevel(theResourceType);
			return -theEnergyMult * mineLevel * Math.pow(1.1, mineLevel) * state.getUtilization(theResourceType);
		}
	}

	private static class FusionProductionScheme implements ProductionScheme {
		@Override
		public void addProduction(OGameState state, double[] production, double energyFactor) {
			production[2] -= 10 * state.getUniSpeed() * state.getBuildingLevel(3) * Math.pow(1.1, state.getBuildingLevel(3))
					* state.getUtilization(3);
		}

		@Override
		public double getEnergyConsumption(OGameState state) {
			return 30 * state.getBuildingLevel(3) * state.getUtilization(3)
					* Math.pow(1.05 + (0.01 * state.getEnergyTech()), state.getBuildingLevel(3));
		}
	}

	private final Map<OGameImprovementType, ImprovementScheme> theImprovementSchemes;
	private final List<ProductionScheme> theProductionSchemes;

	public OGameRules() {
		theImprovementSchemes = new HashMap<>();
		theImprovementSchemes.put(OGameImprovementType.Metal, new BuildingImprovementScheme(60, 15, 0, 1.5));
		theImprovementSchemes.put(OGameImprovementType.Crystal, new BuildingImprovementScheme(48, 24, 0, 1.6));
		theImprovementSchemes.put(OGameImprovementType.Deut, new BuildingImprovementScheme(225, 75, 0, 1.5));
		theImprovementSchemes.put(OGameImprovementType.Fusion, new BuildingImprovementScheme(900, 360, 180, 1.8));
		theImprovementSchemes.put(OGameImprovementType.Energy, new TechImprovementScheme(0, 800, 400, 2));
		theImprovementSchemes.put(OGameImprovementType.Plasma, new TechImprovementScheme(2000, 4000, 1000, 2));
		theImprovementSchemes.put(OGameImprovementType.Planet, new ColonyImprovementScheme(4000, 8000, 4000, 1.75));

		theProductionSchemes = new ArrayList<>();
		theProductionSchemes.add(new MineProductionScheme(0, 30, 30, 1, 0, 0, 10));
		theProductionSchemes.add(new MineProductionScheme(1, 15, 20, .66, 0, 0, 10));
		theProductionSchemes.add(new MineProductionScheme(2, 0, 10, .33, 1.36, 0.004, 20));
		theProductionSchemes.add(new FusionProductionScheme());
	}

	public double[] getUpgradeCost(OGameState initState, OGameImprovementType improvement, int preLevel, int postLevel) {
		boolean research;
		switch (improvement) {
		case Metal:
		case Crystal:
		case Deut:
		case Fusion:
			research = false;
			break;
		default:
			research = true;
			break;
		}
		return theImprovementSchemes.get(improvement).getUpgradeCost(initState, preLevel, postLevel, research);
	}

	public double[] getEnergyProductionConsumption(OGameState state) {
		double production = state.getSatelliteEnergy(), consumption = 0;
		for (ProductionScheme scheme : theProductionSchemes) {
			double energy = scheme.getEnergyConsumption(state);
			if (energy > 0) {
				production += energy;
			} else {
				consumption -= energy;
			}
		}
		return new double[] { production, consumption };
	}

	public double[] getResourceProduction(OGameState state) {
		double[] energy = getEnergyProductionConsumption(state);
		double energyFactor = energy[0] == 0 ? 0 : energy[0] / energy[1];
		double[] production = new double[3];
		for (ProductionScheme scheme : theProductionSchemes) {
			scheme.addProduction(state, production, energyFactor);
		}
		return production;
	}
}
