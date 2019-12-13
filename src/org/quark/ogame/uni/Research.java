package org.quark.ogame.uni;

public interface Research {
	int getEnergy();
	void setEnergy(int energy);

	int getLaser();
	void setLaser(int laser);

	int getIon();
	void setIon(int ion);

	int getHyperspace();
	void setHyperspace(int hyperspace);

	int getPlasma();
	void setPlasma(int plasma);

	int getCombustionDrive();
	void setCombustionDrive(int combustion);

	int getImpulseDrive();
	void setImpulseDrive(int impulse);

	int getHyperspaceDrive();
	void setHyperspaceDrive(int hyperdrive);

	int getEspionage();
	void setEspionage(int espionage);

	int getComputer();
	void setComputer(int computer);

	int getAstrophysics();
	void setAstrophysics(int astro);

	int getIntergalacticResearchNetwork();
	void setIntergalacticResearchNetwork(int irn);

	int getGraviton();
	void setGraviton(int graviton);

	int getWeapons();
	void setWeapons(int weapons);

	int getShielding();
	void setShielding(int shielding);

	int getArmor();
	void setArmor(int armor);

	default int getResearchLevel(ResearchType type) {
		switch (type) {
		case Energy:
			return getEnergy();
		case Laser:
			return getLaser();
		case Ion:
			return getIon();
		case Hyperspace:
			return getHyperspace();
		case Plasma:
			return getPlasma();
		case Combustion:
			return getCombustionDrive();
		case Impulse:
			return getImpulseDrive();
		case Hyperdrive:
			return getHyperspaceDrive();
		case Espionage:
			return getEspionage();
		case Computer:
			return getComputer();
		case Astrophysics:
			return getAstrophysics();
		case IntergalacticResearchNetwork:
			return getIntergalacticResearchNetwork();
		case Graviton:
			return getGraviton();
		case Weapons:
			return getWeapons();
		case Shielding:
			return getShielding();
		case Armor:
			return getArmor();
		}
		throw new IllegalStateException("Unrecognized research type " + type);
	}
}
