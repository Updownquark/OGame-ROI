package org.quark.ogame.uni;

public interface Planet extends RockyBody {
	// Planet properties
	int getBaseFields();
	void setBaseFields(int baseFields);

	int getMinimumTemperature();
	void setMinimumTemperature(int minTemp);

	int getMaximumTemperature();
	void setMaximumTemperature(int maxTemp);

	// Resources
	int getMetalMine();
	void setMetalMine(int metalMine);

	int getCrystalMine();
	void setCrystalMine(int crystalMine);

	int getDeuteriumSynthesizer();
	void setDeuteriumSynthesizer(int deutSynth);

	int getSolarPlant();
	void setSolarPlant(int solarPlant);

	int getFusionReactor();
	void setFusionReactor(int fusionReactor);

	int getMetalStorage();
	void setMetalStorage(int metalStorage);

	int getCrystalStorage();
	void setCrystalStorage(int crystalStorage);

	int getDeuteriumStorage();
	void setDeuteriumStorage(int deuteriumStorage);

	// Facilities
	int getResearchLab();
	void setResearchLab(int researchLab);

	int getAllianceDepot();
	void setAllianceDepot(int allianceDepot);

	int getMissileSilo();
	void setMissileSilo(int missileSilo);

	int getNaniteFactory();
	void setNaniteFactory(int naniteFactory);

	int getTerraformer();
	void setTerraformer(int terraformer);

	int getSpaceDock();
	void setSpaceDock(int spaceDock);

	// Stationary Ships
	int getSolarSatellites();
	void setSolarSatellites(int satellites);

	int getCrawlers();
	void setCrawlers(int crawlers);

	Moon getMoon();

	@Override
	default int getTotalFields() {
		int fields = getBaseFields();
		int terraformer = getTerraformer();
		fields += getTerraformerFields(terraformer);
		return fields;
	}

	default void setTotalFields(int totalFields) {
		int terraformer = getTerraformer();
		int terraFields = getTerraformerFields(terraformer);
		setBaseFields(totalFields - terraFields);
	}

	// Utility methods
	@Override
	default int getUsedFields() {
		return RockyBody.super.getUsedFields()//
			+ getMetalMine() + getCrystalMine() + getDeuteriumSynthesizer()//
			+ getSolarPlant() + getFusionReactor()//
			+ getMetalStorage() + getCrystalStorage() + getDeuteriumStorage()//
			+ getResearchLab() + getAllianceDepot() + getMissileSilo() + getNaniteFactory() + getTerraformer();
	}

	static int getTerraformerFields(int terraformerLevel) {
		return (int) Math.ceil(terraformerLevel * 5.5);
	}
}
