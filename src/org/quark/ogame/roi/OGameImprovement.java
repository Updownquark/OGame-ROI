package org.quark.ogame.roi;

import java.time.Duration;

import org.quark.ogame.uni.UpgradeCost;

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
	public final int metalStorage;
	public final int crystalStorage;
	public final int deutStorage;
	public final int crawlers;
	public final int buildings;

	public final OGameImprovementType type;
	public final int level;
	public final Duration roi;

	public final UpgradeCost accountValue;

	public OGameImprovement(OGameState2 state, OGameImprovementType type, int level, Duration roi) {
		metal = state.getPlanet().getMetalMine();
		crystal = state.getPlanet().getCrystalMine();
		deut = state.getPlanet().getDeuteriumSynthesizer();
		fusion = state.getPlanet().getFusionReactor();
		energy = state.getAccount().getResearch().getEnergy();
		plasma = state.getAccount().getResearch().getPlasma();
		planets = state.getPlanetCount();
		robotics = state.getPlanet().getRoboticsFactory();
		nanites = state.getPlanet().getNaniteFactory();
		researchLab = state.getPlanet().getResearchLab();
		irn = state.getAccount().getResearch().getIntergalacticResearchNetwork();
		metalStorage = state.getPlanet().getMetalStorage();
		crystalStorage = state.getPlanet().getCrystalStorage();
		deutStorage = state.getPlanet().getDeuteriumStorage();
		crawlers = state.getPlanet().getCrawlers();
		buildings = state.getPlanet().getUsedFields();

		this.type = type;
		this.level = level;
		this.roi = roi;

		accountValue = state.getAccountValue();
	}
}