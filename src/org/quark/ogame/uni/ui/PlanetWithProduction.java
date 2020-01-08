package org.quark.ogame.uni.ui;

import org.quark.ogame.uni.OGameEconomyRuleSet.Production;
import org.quark.ogame.uni.Planet;

public class PlanetWithProduction {
	public final Planet planet;
	private Production theEnergy;
	private Production theMetal;
	private Production theCrystal;
	private Production theDeuterium;

	public PlanetWithProduction(Planet planet) {
		this.planet = planet;
	}

	public PlanetWithProduction setProduction(Production energy, Production metal, Production crystal, Production deuterium) {
		theEnergy = energy;
		theMetal = metal;
		theCrystal = crystal;
		theDeuterium = deuterium;
		return this;
	}

	public Production getEnergy() {
		return theEnergy;
	}

	public Production getMetal() {
		return theMetal;
	}

	public Production getCrystal() {
		return theCrystal;
	}

	public Production getDeuterium() {
		return theDeuterium;
	}

	@Override
	public String toString() {
		return planet.getName();
	}
}