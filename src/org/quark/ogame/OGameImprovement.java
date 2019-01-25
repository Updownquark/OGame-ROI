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

	public final OGameImprovementType type;
	public final int level;
	public final Duration roi;

	public final OGameCost accountValue;

	public OGameImprovement(OGameState state, OGameImprovementType type, int level, Duration roi) {
		metal = state.getBuildingLevel(0);
		crystal = state.getBuildingLevel(1);
		deut = state.getBuildingLevel(2);
		fusion = state.getBuildingLevel(3);
		energy = state.getEnergyTech();
		plasma = state.getPlasmaTech();
		planets = state.getPlanets();

		this.type = type;
		this.level = level;
		this.roi = roi;

		accountValue = state.getAccountValue();
	}
}