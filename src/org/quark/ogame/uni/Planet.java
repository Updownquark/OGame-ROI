package org.quark.ogame.uni;

import org.observe.config.ParentReference;
import org.observe.util.Identified;

public interface Planet extends RockyBody, Identified {
	@ParentReference
	Account getAccount();

	Coordinate getCoordinates();

	// Planet properties
	int getBaseFields();
	Planet setBaseFields(int baseFields);

	int getMinimumTemperature();
	Planet setMinimumTemperature(int minTemp);

	int getMaximumTemperature();
	Planet setMaximumTemperature(int maxTemp);

	Moon getMoon();

	@Override
	Planet setName(String name);
	@Override
	Planet setRoboticsFactory(int roboticsFactory);
	@Override
	Planet setShipyard(int shipyard);
	@Override
	Planet setCurrentUpgrade(BuildingType building);

	@Override
	default Planet setStationedShips(ShipyardItemType type, int count) {
		RockyBody.super.setStationedShips(type, count);
		return this;
	}

	// Resources
	int getMetalMine();
	Planet setMetalMine(int metalMine);

	int getCrystalMine();
	Planet setCrystalMine(int crystalMine);

	int getDeuteriumSynthesizer();
	Planet setDeuteriumSynthesizer(int deutSynth);

	int getSolarPlant();
	Planet setSolarPlant(int solarPlant);

	int getFusionReactor();
	Planet setFusionReactor(int fusionReactor);

	int getMetalStorage();
	Planet setMetalStorage(int metalStorage);

	int getCrystalStorage();
	Planet setCrystalStorage(int crystalStorage);

	int getDeuteriumStorage();
	Planet setDeuteriumStorage(int deuteriumStorage);

	// Facilities
	int getResearchLab();
	Planet setResearchLab(int researchLab);

	int getAllianceDepot();
	Planet setAllianceDepot(int allianceDepot);

	int getMissileSilo();
	Planet setMissileSilo(int missileSilo);

	int getNaniteFactory();
	Planet setNaniteFactory(int naniteFactory);

	int getTerraformer();
	Planet setTerraformer(int terraformer);

	int getSpaceDock();
	Planet setSpaceDock(int spaceDock);

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
			return getMissileSilo();
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
	default Planet setBuildingLevel(BuildingType type, int buildingLevel) {
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
			setMissileSilo(buildingLevel);
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
		return this;
	}

	default int getSolarSatellites() {
		return getStationaryStructures().getSolarSatellites();
	}
	default Planet setSolarSatellites(int solarSatellites) {
		getStationaryStructures().setSolarSatellites(solarSatellites);
		return this;
	}

	default int getCrawlers() {
		return getStationaryStructures().getCrawlers();
	}
	default Planet setCrawlers(int crawlers) {
		getStationaryStructures().setCrawlers(crawlers);
		return this;
	}

	// Active Items

	int getMetalBonus();
	Planet setMetalBonus(int metalBonus);

	int getCrystalBonus();
	Planet setCrystalBonus(int crystalBonus);

	int getDeuteriumBonus();
	Planet setDeuteriumBonus(int deutBonus);

	int getEnergyBonus();
	Planet setEnergyBonus(int energyBonus);

	// Utilization Percentages

	int getMetalUtilization();
	Planet setMetalUtilization(int utilization);

	int getCrystalUtilization();
	Planet setCrystalUtilization(int utilization);

	int getDeuteriumUtilization();
	Planet setDeuteriumUtilization(int utilization);

	int getSolarPlantUtilization();
	Planet setSolarPlantUtilization(int utilization);

	int getFusionReactorUtilization();
	Planet setFusionReactorUtilization(int utilization);

	int getSolarSatelliteUtilization();
	Planet setSolarSatelliteUtilization(int utilization);

	int getCrawlerUtilization();
	Planet setCrawlerUtilization(int utilization);

	default int getBonus(ResourceType type) {
		switch (type) {
		case Metal:
			return getMetalBonus();
		case Crystal:
			return getCrystalBonus();
		case Deuterium:
			return getDeuteriumBonus();
		case Energy:
			return getEnergyBonus();
		default:
			throw new IllegalStateException("Unrecognized resource type for bonus: " + type);
		}
	}

	default Planet setBonus(ResourceType type, int level) {
		switch (type) {
		case Metal:
			setMetalBonus(level);
			break;
		case Crystal:
			setCrystalBonus(level);
			break;
		case Deuterium:
			setDeuteriumBonus(level);
			break;
		case Energy:
			setEnergyBonus(level);
		default:
			throw new IllegalStateException("Unrecognized resource type for bonus: " + type);
		}
		return this;
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
