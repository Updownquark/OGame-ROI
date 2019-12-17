package org.quark.ogame.uni;

public interface Planet extends RockyBody {
	// Planet properties
	int getBaseFields();
	void setBaseFields(int baseFields);

	int getMinimumTemperature();
	void setMinimumTemperature(int minTemp);

	int getMaximumTemperature();
	void setMaximumTemperature(int maxTemp);

	Moon getMoon();

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

	@Override
	default int getBuildingLevel(BuildingType type) {
		switch (type) {
		case MetalMine:
			return getMetalMine();
		case CrystalMine:
			return getCrystalMine();
		case DeuteriumSynthesizer:
			return getDeuteriumSynthesizer();
		case SolarPlant:
			return getSolarPlant();
		case FusionReactor:
			return getFusionReactor();
		case MetalStorage:
			return getMetalStorage();
		case CrystalStorage:
			return getCrystalStorage();
		case DeuteriumStorage:
			return getDeuteriumStorage();
		case RoboticsFactory:
			return getRoboticsFactory();
		case Shipyard:
			return getShipyard();
		case ResearchLab:
			return getResearchLab();
		case AllianceDepot:
			return getAllianceDepot();
		case MissileSilo:
			return getMetalStorage();
		case NaniteFactory:
			return getNaniteFactory();
		case Terraformer:
			return getTerraformer();
		case SpaceDock:
			return getSpaceDock();
		case LunarBase:
		case SensorPhalanx:
		case JumpGate:
			return 0;
		}
		throw new IllegalStateException("Unrecognized building type " + type);
	}

	@Override
	default void setBuildingLevel(BuildingType type, int buildingLevel) {
		switch (type) {
		case MetalMine:
			setMetalMine(buildingLevel);
			break;
		case CrystalMine:
			setCrystalMine(buildingLevel);
			break;
		case DeuteriumSynthesizer:
			setDeuteriumSynthesizer(buildingLevel);
			break;
		case SolarPlant:
			setSolarPlant(buildingLevel);
			break;
		case FusionReactor:
			setFusionReactor(buildingLevel);
			break;
		case MetalStorage:
			setMetalStorage(buildingLevel);
			break;
		case CrystalStorage:
			setCrystalStorage(buildingLevel);
			break;
		case DeuteriumStorage:
			setDeuteriumStorage(buildingLevel);
			break;
		case RoboticsFactory:
			setRoboticsFactory(buildingLevel);
			break;
		case Shipyard:
			setShipyard(buildingLevel);
			break;
		case ResearchLab:
			setResearchLab(buildingLevel);
			break;
		case AllianceDepot:
			setAllianceDepot(buildingLevel);
			break;
		case MissileSilo:
			setMetalStorage(buildingLevel);
			break;
		case NaniteFactory:
			setNaniteFactory(buildingLevel);
			break;
		case Terraformer:
			setTerraformer(buildingLevel);
			break;
		case SpaceDock:
			setSpaceDock(buildingLevel);
			break;
		case LunarBase:
		case SensorPhalanx:
		case JumpGate:
			if (buildingLevel != 0) {
				throw new IllegalArgumentException();
			}
		}
	}

	// Stationary Ships
	int getSolarSatellites();
	void setSolarSatellites(int satellites);

	int getCrawlers();
	void setCrawlers(int crawlers);

	// Active Items

	@Override
	default int getStationedShips(ShipyardItemType type) {
		switch (type) {
		case SolarSatellite:
			return getSolarSatellites();
		case Crawler:
			return getCrawlers();
		default:
			return 0;
		}
	}

	@Override
	default void setStationedShips(ShipyardItemType type, int count) {
		switch (type) {
		case SolarSatellite:
			setSolarSatellites(count);
			break;
		case Crawler:
			setCrawlers(count);
			break;
		default:
			break;
		}
	}

	int getMetalBonus();
	void setMetalBonus(int metalBonus);

	int getCrystalBonus();
	void setCrystalBonus(int crystalBonus);

	int getDeuteriumBonus();
	void setDeuteriumBonus(int deutBonus);

	// Utilization Percentages

	int getMetalUtilization();
	void setMetalUtilization(int utilization);

	int getCrystalUtilization();
	void setCrystalUtilization(int utilization);

	int getDeuteriumUtilization();
	void setDeuteriumUtilization(int utilization);

	int getSolarPlantUtilization();
	void setSolarPlantUtilization(int utilization);

	int getFusionReactorUtilization();
	void setFusionReactorUtilization(int utilization);

	int getSolarSatelliteUtilization();
	void setSolarSatelliteUtilization(int utilization);

	int getCrawlerUtilization();
	void setCrawlerUtilization(int utilization);

	default int getBonus(ResourceType type) {
		switch (type) {
		case Metal:
			return getMetalBonus();
		case Crystal:
			return getCrystalBonus();
		case Deuterium:
			return getDeuteriumBonus();
		}
		throw new IllegalStateException("Unrecognized resource type for bonus: " + type);
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
}
