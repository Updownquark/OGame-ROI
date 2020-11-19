package org.quark.ogame.uni.ui;

import org.quark.ogame.uni.OGameEconomyRuleSet.Production;
import org.quark.ogame.uni.Planet;
import org.quark.ogame.uni.ResourceType;
import org.quark.ogame.uni.UpgradeAccount.UpgradePlanet;

public class PlanetWithProduction {
	public final Planet planet;
	public final UpgradePlanet upgradePlanet;

	private Production theEnergy;
	private Production theMetal;
	private Production theCrystal;
	private Production theDeuterium;

	private Production theUpgradeEnergy;
	private Production theUpgradeMetal;
	private Production theUpgradeCrystal;
	private Production theUpgradeDeuterium;

	public PlanetWithProduction(Planet planet, UpgradePlanet upgradePlanet) {
		this.planet = planet;
		this.upgradePlanet = upgradePlanet;
	}

	public PlanetWithProduction setProduction(Production energy, Production metal, Production crystal, Production deuterium) {
		theEnergy = energy;
		theMetal = metal;
		theCrystal = crystal;
		theDeuterium = deuterium;
		return this;
	}

	public PlanetWithProduction setUpgradeProduction(Production energy, Production metal, Production crystal, Production deuterium) {
		theUpgradeEnergy = energy;
		theUpgradeMetal = metal;
		theUpgradeCrystal = crystal;
		theUpgradeDeuterium = deuterium;
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

	public Production getUpgradeEnergy() {
		return theUpgradeEnergy;
	}

	public Production getUpgradeMetal() {
		return theUpgradeMetal;
	}

	public Production getUpgradeCrystal() {
		return theUpgradeCrystal;
	}

	public Production getUpgradeDeuterium() {
		return theUpgradeDeuterium;
	}

	public Production getEnergy(boolean goal) {
		return goal ? theUpgradeEnergy : theEnergy;
	}

	public Production getMetal(boolean upgrade) {
		return upgrade ? theUpgradeMetal : theMetal;
	}

	public Production getCrystal(boolean upgrade) {
		return upgrade ? theUpgradeCrystal : theCrystal;
	}

	public Production getDeuterium(boolean upgrade) {
		return upgrade ? theUpgradeDeuterium : theDeuterium;
	}

	public Production getProduction(ResourceType resource, boolean upgrade) {
		switch (resource) {
		case Energy:
			return getEnergy(upgrade);
		case Metal:
			return getMetal(upgrade);
		case Crystal:
			return getCrystal(upgrade);
		case Deuterium:
			return getDeuterium(upgrade);
		}
		throw new IllegalStateException(resource.name());
	}

	@Override
	public String toString() {
		if (upgradePlanet != null) {
			return upgradePlanet.toString();
		} else if (planet != null) {
			return planet.toString();
		} else {
			return "";
		}
	}
}