package org.quark.ogame;

import java.time.Duration;

public class OGameImprovement {
	public final int metal;
	public final int crystal;
	public final int deut;
	public final int fusion;
	public final int energy;
	public final int plasma;
	public final int planets;
	public final int robotics;
	public final int nanites;
	public final int researchLab;
	public final int irn;

	public final OGameImprovementType type;
	public final int level;
	public final Duration roi;

	public final OGameCost accountValue;

	public OGameImprovement(OGameState state, OGameImprovementType type, int level, Duration roi) {
		metal = state.getBuildingLevel(OGameBuildingType.Metal);
		crystal = state.getBuildingLevel(OGameBuildingType.Crystal);
		deut = state.getBuildingLevel(OGameBuildingType.Deuterium);
		fusion = state.getBuildingLevel(OGameBuildingType.Fusion);
		energy = state.getEnergyTech();
		plasma = state.getPlasmaTech();
		planets = state.getPlanets();
		robotics = state.getBuildingLevel(OGameBuildingType.Robotics);
		nanites = state.getBuildingLevel(OGameBuildingType.Nanite);
		researchLab = state.getBuildingLevel(OGameBuildingType.ResearchLab);
		irn = state.getIRN();

		this.type = type;
		this.level = level;
		this.roi = roi;

		accountValue = state.getAccountValue();
	}
}