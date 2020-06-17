package org.quark.ogame.uni;

public interface StationaryStructures extends ShipyardItemSet {
	int getSolarSatellites();
	StationaryStructures setSolarSatellites(int solarSatellites);

	int getCrawlers();
	StationaryStructures setCrawlers(int crawlers);

	int getRocketLaunchers();
	StationaryStructures setRocketLaunchers(int rocketLaunchers);

	int getLightLasers();
	StationaryStructures setLightLasers(int lightLasers);

	int getHeavyLasers();
	StationaryStructures setHeavyLasers(int heavyLasers);

	int getGaussCannons();
	StationaryStructures setGaussCannons(int gaussCannons);

	int getIonCannons();
	StationaryStructures setIonCannons(int ionCannons);

	int getPlasmaTurrets();
	StationaryStructures setPlasmaTurrets(int plasmaTurrets);

	int getSmallShieldDomes();
	StationaryStructures setSmallShieldDomes(int smallShieldDomes);

	int getLargeShieldDomes();

	StationaryStructures setLargeShieldDomes(int largeShieldDomes);

	int getAntiBallisticMissiles();
	StationaryStructures setAntiBallisticMissiles(int antiBallisticMissiles);

	int getInterPlanetaryMissiles();
	StationaryStructures setInterPlanetaryMissiles(int interPlanetaryMissiles);

	@Override
	default int getItems(ShipyardItemType type) {
		if (type.mobile) {
			throw new IllegalArgumentException(type + " is not a stationary structure");
		}
		switch (type) {
		case SolarSatellite:
			return getSolarSatellites();
		case Crawler:
			return getCrawlers();
		case RocketLauncher:
			return getRocketLaunchers();
		case LightLaser:
			return getLightLasers();
		case HeavyLaser:
			return getHeavyLasers();
		case GaussCannon:
			return getGaussCannons();
		case IonCannon:
			return getIonCannons();
		case PlasmaTurret:
			return getPlasmaTurrets();
		case SmallShield:
			return getSmallShieldDomes();
		case LargeShield:
			return getLargeShieldDomes();
		case AntiBallisticMissile:
			return getAntiBallisticMissiles();
		case InterPlanetaryMissile:
			return getInterPlanetaryMissiles();
		default:
			throw new IllegalStateException("Unrecognized stationary structure: " + type);
		}
	}

	@Override
	default StationaryStructures setItems(ShipyardItemType type, int number) {
		if (type.mobile) {
			throw new IllegalArgumentException(type + " is not a stationary structure");
		}
		switch (type) {
		case SolarSatellite:
			return setSolarSatellites(number);
		case Crawler:
			return setCrawlers(number);
		case RocketLauncher:
			return setRocketLaunchers(number);
		case LightLaser:
			return setLightLasers(number);
		case HeavyLaser:
			return setHeavyLasers(number);
		case GaussCannon:
			return setGaussCannons(number);
		case IonCannon:
			return setIonCannons(number);
		case PlasmaTurret:
			return setPlasmaTurrets(number);
		case SmallShield:
			return setSmallShieldDomes(number);
		case LargeShield:
			return setLargeShieldDomes(number);
		case AntiBallisticMissile:
			return setAntiBallisticMissiles(number);
		case InterPlanetaryMissile:
			return setInterPlanetaryMissiles(number);
		default:
			throw new IllegalStateException("Unrecognized stationary structure: " + type);
		}
	}
}
