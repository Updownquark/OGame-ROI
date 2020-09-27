package org.quark.ogame.uni;

public interface CondensedPlanet extends CondensedRockyBody, Planet {
	@Override
	abstract int getBuildingLevel(BuildingType type);

	@Override
	abstract CondensedPlanet setBuildingLevel(BuildingType type, int buildingLevel);

	@Override
	default CondensedPlanet setRoboticsFactory(int roboticsFactory) {
		CondensedRockyBody.super.setRoboticsFactory(roboticsFactory);
		return this;
	}

	@Override
	default CondensedPlanet setShipyard(int shipyard) {
		CondensedRockyBody.super.setShipyard(shipyard);
		return this;
	}

	@Override
	default int getMetalMine() {
		return getBuildingLevel(BuildingType.MetalMine);
	}

	@Override
	default CondensedPlanet setMetalMine(int metalMine) {
		setBuildingLevel(BuildingType.MetalMine, metalMine);
		return this;
	}

	@Override
	default int getCrystalMine() {
		return getBuildingLevel(BuildingType.CrystalMine);
	}

	@Override
	default CondensedPlanet setCrystalMine(int crystalMine) {
		setBuildingLevel(BuildingType.CrystalMine, crystalMine);
		return this;
	}

	@Override
	default int getDeuteriumSynthesizer() {
		return getBuildingLevel(BuildingType.DeuteriumSynthesizer);
	}

	@Override
	default CondensedPlanet setDeuteriumSynthesizer(int deutSynth) {
		setBuildingLevel(BuildingType.DeuteriumSynthesizer, deutSynth);
		return this;
	}

	@Override
	default int getSolarPlant() {
		return getBuildingLevel(BuildingType.SolarPlant);
	}

	@Override
	default CondensedPlanet setSolarPlant(int solarPlant) {
		setBuildingLevel(BuildingType.SolarPlant, solarPlant);
		return this;
	}

	@Override
	default int getFusionReactor() {
		return getBuildingLevel(BuildingType.FusionReactor);
	}

	@Override
	default CondensedPlanet setFusionReactor(int fusionReactor) {
		setBuildingLevel(BuildingType.FusionReactor, fusionReactor);
		return this;
	}

	@Override
	default int getMetalStorage() {
		return getBuildingLevel(BuildingType.MetalStorage);
	}

	@Override
	default CondensedPlanet setMetalStorage(int metalStorage) {
		setBuildingLevel(BuildingType.MetalStorage, metalStorage);
		return this;
	}

	@Override
	default int getCrystalStorage() {
		return getBuildingLevel(BuildingType.CrystalStorage);
	}

	@Override
	default CondensedPlanet setCrystalStorage(int crystalStorage) {
		setBuildingLevel(BuildingType.CrystalStorage, crystalStorage);
		return this;
	}

	@Override
	default int getDeuteriumStorage() {
		return getBuildingLevel(BuildingType.DeuteriumStorage);
	}

	@Override
	default CondensedPlanet setDeuteriumStorage(int deuteriumStorage) {
		setBuildingLevel(BuildingType.DeuteriumStorage, deuteriumStorage);
		return this;
	}

	@Override
	default int getResearchLab() {
		return getBuildingLevel(BuildingType.ResearchLab);
	}

	@Override
	default CondensedPlanet setResearchLab(int researchLab) {
		setBuildingLevel(BuildingType.ResearchLab, researchLab);
		return this;
	}

	@Override
	default int getAllianceDepot() {
		return getBuildingLevel(BuildingType.AllianceDepot);
	}

	@Override
	default CondensedPlanet setAllianceDepot(int allianceDepot) {
		setBuildingLevel(BuildingType.AllianceDepot, allianceDepot);
		return this;
	}

	@Override
	default int getMissileSilo() {
		return getBuildingLevel(BuildingType.MissileSilo);
	}

	@Override
	default CondensedPlanet setMissileSilo(int missileSilo) {
		setBuildingLevel(BuildingType.MissileSilo, missileSilo);
		return this;
	}

	@Override
	default int getNaniteFactory() {
		return getBuildingLevel(BuildingType.NaniteFactory);
	}

	@Override
	default CondensedPlanet setNaniteFactory(int naniteFactory) {
		setBuildingLevel(BuildingType.NaniteFactory, naniteFactory);
		return this;
	}

	@Override
	default int getTerraformer() {
		return getBuildingLevel(BuildingType.Terraformer);
	}

	@Override
	default CondensedPlanet setTerraformer(int terraformer) {
		setBuildingLevel(BuildingType.Terraformer, terraformer);
		return this;
	}

	@Override
	default int getSpaceDock() {
		return getBuildingLevel(BuildingType.SpaceDock);
	}

	@Override
	default CondensedPlanet setSpaceDock(int spaceDock) {
		setBuildingLevel(BuildingType.SpaceDock, spaceDock);
		return this;
	}

	@Override
	default int getCrawlers() {
		return getStationaryStructures().getItems(ShipyardItemType.Crawler);
	}

	@Override
	default Planet setCrawlers(int crawlers) {
		getStationaryStructures().setItems(ShipyardItemType.Crawler, crawlers);
		return this;
	}

	@Override
	default int getMetalBonus() {
		return getBonus(ResourceType.Metal);
	}

	@Override
	default Planet setMetalBonus(int metalBonus) {
		return setBonus(ResourceType.Metal, metalBonus);
	}

	@Override
	default int getCrystalBonus() {
		return getBonus(ResourceType.Crystal);
	}

	@Override
	default Planet setCrystalBonus(int crystalBonus) {
		return setBonus(ResourceType.Crystal, crystalBonus);
	}

	@Override
	default int getDeuteriumBonus() {
		return getBonus(ResourceType.Deuterium);
	}

	@Override
	default Planet setDeuteriumBonus(int deutBonus) {
		return setBonus(ResourceType.Deuterium, deutBonus);
	}

	@Override
	default int getEnergyBonus() {
		return getBonus(ResourceType.Energy);
	}

	@Override
	default Planet setEnergyBonus(int energyBonus) {
		return setBonus(ResourceType.Energy, energyBonus);
	}

	@Override
	abstract int getBonus(ResourceType type);

	@Override
	abstract Planet setBonus(ResourceType type, int level);

	@Override
	CondensedPlanet setCurrentUpgrade(BuildingType building);

	@Override
	CondensedPlanet setName(String name);
}
