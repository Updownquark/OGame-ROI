package org.quark.ogame.uni;

public interface CondensedResearch extends Research {
	@Override
	default int getEnergy() {
		return getResearchLevel(ResearchType.Energy);
	}

	@Override
	default void setEnergy(int energy) {
		setResearchLevel(ResearchType.Energy, energy);
	}

	@Override
	default int getLaser() {
		return getResearchLevel(ResearchType.Laser);
	}

	@Override
	default void setLaser(int laser) {
		setResearchLevel(ResearchType.Laser, laser);
	}

	@Override
	default int getIon() {
		return getResearchLevel(ResearchType.Ion);
	}

	@Override
	default void setIon(int ion) {
		setResearchLevel(ResearchType.Ion, ion);
	}

	@Override
	default int getHyperspace() {
		return getResearchLevel(ResearchType.Hyperspace);
	}

	@Override
	default void setHyperspace(int hyperspace) {
		setResearchLevel(ResearchType.Hyperspace, hyperspace);
	}

	@Override
	default int getPlasma() {
		return getResearchLevel(ResearchType.Plasma);
	}

	@Override
	default void setPlasma(int plasma) {
		setResearchLevel(ResearchType.Plasma, plasma);
	}

	@Override
	default int getCombustionDrive() {
		return getResearchLevel(ResearchType.Combustion);
	}

	@Override
	default void setCombustionDrive(int combustion) {
		setResearchLevel(ResearchType.Combustion, combustion);
	}

	@Override
	default int getImpulseDrive() {
		return getResearchLevel(ResearchType.Impulse);
	}

	@Override
	default void setImpulseDrive(int impulse) {
		setResearchLevel(ResearchType.Impulse, impulse);
	}

	@Override
	default int getHyperspaceDrive() {
		return getResearchLevel(ResearchType.Hyperdrive);
	}

	@Override
	default void setHyperspaceDrive(int hyperdrive) {
		setResearchLevel(ResearchType.Hyperdrive, hyperdrive);
	}

	@Override
	default int getEspionage() {
		return getResearchLevel(ResearchType.Espionage);
	}

	@Override
	default void setEspionage(int espionage) {
		setResearchLevel(ResearchType.Espionage, espionage);
	}

	@Override
	default int getComputer() {
		return getResearchLevel(ResearchType.Computer);
	}

	@Override
	default void setComputer(int computer) {
		setResearchLevel(ResearchType.Computer, computer);
	}

	@Override
	default int getAstrophysics() {
		return getResearchLevel(ResearchType.Astrophysics);
	}

	@Override
	default void setAstrophysics(int astro) {
		setResearchLevel(ResearchType.Astrophysics, astro);
	}

	@Override
	default int getIntergalacticResearchNetwork() {
		return getResearchLevel(ResearchType.IntergalacticResearchNetwork);
	}

	@Override
	default void setIntergalacticResearchNetwork(int irn) {
		setResearchLevel(ResearchType.IntergalacticResearchNetwork, irn);
	}

	@Override
	default int getGraviton() {
		return getResearchLevel(ResearchType.Graviton);
	}

	@Override
	default void setGraviton(int graviton) {
		setResearchLevel(ResearchType.Graviton, graviton);
	}

	@Override
	default int getWeapons() {
		return getResearchLevel(ResearchType.Weapons);
	}

	@Override
	default void setWeapons(int weapons) {
		setResearchLevel(ResearchType.Weapons, weapons);
	}

	@Override
	default int getShielding() {
		return getResearchLevel(ResearchType.Shielding);
	}

	@Override
	default void setShielding(int shielding) {
		setResearchLevel(ResearchType.Shielding, shielding);
	}

	@Override
	default int getArmor() {
		return getResearchLevel(ResearchType.Armor);
	}

	@Override
	default void setArmor(int armor) {
		setResearchLevel(ResearchType.Armor, armor);
	}

	@Override
	abstract int getResearchLevel(ResearchType type);

	@Override
	abstract void setResearchLevel(ResearchType type, int level);
}
