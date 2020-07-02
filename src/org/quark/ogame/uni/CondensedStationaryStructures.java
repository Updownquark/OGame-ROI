package org.quark.ogame.uni;

public interface CondensedStationaryStructures extends StationaryStructures {
	@Override
	default int getSolarSatellites() {
		return getItems(ShipyardItemType.SolarSatellite);
	}

	@Override
	default StationaryStructures setSolarSatellites(int solarSatellites) {
		return setItems(ShipyardItemType.SolarSatellite, solarSatellites);
	}

	@Override
	default int getCrawlers() {
		return getItems(ShipyardItemType.Crawler);
	}

	@Override
	default StationaryStructures setCrawlers(int crawlers) {
		return setItems(ShipyardItemType.Crawler, crawlers);
	}

	@Override
	default int getRocketLaunchers() {
		return getItems(ShipyardItemType.RocketLauncher);
	}

	@Override
	default StationaryStructures setRocketLaunchers(int rocketLaunchers) {
		return setItems(ShipyardItemType.RocketLauncher, rocketLaunchers);
	}

	@Override
	default int getLightLasers() {
		return getItems(ShipyardItemType.LightLaser);
	}

	@Override
	default StationaryStructures setLightLasers(int lightLasers) {
		return setItems(ShipyardItemType.LightLaser, lightLasers);
	}

	@Override
	default int getHeavyLasers() {
		return getItems(ShipyardItemType.HeavyLaser);
	}

	@Override
	default StationaryStructures setHeavyLasers(int heavyLasers) {
		return setItems(ShipyardItemType.HeavyLaser, heavyLasers);
	}

	@Override
	default int getGaussCannons() {
		return getItems(ShipyardItemType.GaussCannon);
	}

	@Override
	default StationaryStructures setGaussCannons(int gaussCannons) {
		return setItems(ShipyardItemType.GaussCannon, gaussCannons);
	}

	@Override
	default int getIonCannons() {
		return getItems(ShipyardItemType.IonCannon);
	}

	@Override
	default StationaryStructures setIonCannons(int ionCannons) {
		return setItems(ShipyardItemType.IonCannon, ionCannons);
	}

	@Override
	default int getPlasmaTurrets() {
		return getItems(ShipyardItemType.PlasmaTurret);
	}

	@Override
	default StationaryStructures setPlasmaTurrets(int plasmaTurrets) {
		return setItems(ShipyardItemType.PlasmaTurret, plasmaTurrets);
	}

	@Override
	default int getSmallShieldDomes() {
		return getItems(ShipyardItemType.SmallShield);
	}

	@Override
	default StationaryStructures setSmallShieldDomes(int smallShieldDomes) {
		return setItems(ShipyardItemType.SmallShield, smallShieldDomes);
	}

	@Override
	default int getLargeShieldDomes() {
		return getItems(ShipyardItemType.LargeShield);
	}

	@Override
	default StationaryStructures setLargeShieldDomes(int largeShieldDomes) {
		return setItems(ShipyardItemType.LargeShield, largeShieldDomes);
	}

	@Override
	default int getAntiBallisticMissiles() {
		return getItems(ShipyardItemType.AntiBallisticMissile);
	}

	@Override
	default StationaryStructures setAntiBallisticMissiles(int antiBallisticMissiles) {
		return setItems(ShipyardItemType.AntiBallisticMissile, antiBallisticMissiles);
	}

	@Override
	default int getInterPlanetaryMissiles() {
		return getItems(ShipyardItemType.InterPlanetaryMissile);
	}

	@Override
	default StationaryStructures setInterPlanetaryMissiles(int interPlanetaryMissiles) {
		return setItems(ShipyardItemType.InterPlanetaryMissile, interPlanetaryMissiles);
	}

	@Override
	abstract int getItems(ShipyardItemType type);

	@Override
	abstract StationaryStructures setItems(ShipyardItemType type, int number);
}
