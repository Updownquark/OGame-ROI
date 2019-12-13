package org.quark.ogame.uni;

public enum ShipyardItemType {
	// Combat Ships
	LightFighter(false),
	HeavyFighter(false),
	Cruiser(false),
	Battleship(false), //
	Battlecruiser(false),
	Bomber(false),
	Destroyer(false),
	Deathstar(false), //
	Reaper(false),
	Pathfinder(false), //
	// Civil Ships
	SmallCargo(false),
	LargeCargo(false), //
	ColonyShip(false),
	Recycler(false),
	EspionageProbe(false), //
	SolarSatellite(false),
	Crawler(false), //

	// Defenses
	RocketLauncher(true), //
	LightLaser(true), //
	HeavyLaser(true), //
	GaussCannon(true), //
	IonCannon(true), //
	PlasmaTurret(true), //
	SmallShield(true), //
	LargeSheild(true), //
	AntiBallisticMissile(true), //
	InterPlanetaryMissile(true);

	public final boolean defense;

	private ShipyardItemType(boolean defense) {
		this.defense = defense;
	};
}
