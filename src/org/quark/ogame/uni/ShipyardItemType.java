package org.quark.ogame.uni;

public enum ShipyardItemType {
	// Combat Ships
	LightFighter(true, false),
	HeavyFighter(true, false),
	Cruiser(true, false),
	BattleShip(true, false), //
	BattleCruiser(true, false),
	Bomber(true, false),
	Destroyer(true, false),
	DeathStar(true, false), //
	Reaper(true, false),
	PathFinder(true, false), //
	// Civil Ships
	SmallCargo(true, false),
	LargeCargo(true, false), //
	ColonyShip(true, false),
	Recycler(true, false),
	EspionageProbe(true, false), //
	SolarSatellite(false, false),
	Crawler(false, false), //

	// Defenses
	RocketLauncher(false, true), //
	LightLaser(false, true), //
	HeavyLaser(false, true), //
	GaussCannon(false, true), //
	IonCannon(false, true), //
	PlasmaTurret(false, true), //
	SmallShield(false, true), //
	LargeShield(false, true), //
	AntiBallisticMissile(false, true), //
	InterPlanetaryMissile(false, true);

	public final boolean mobile;
	public final boolean defense;

	private ShipyardItemType(boolean mobile, boolean defense) {
		this.mobile = mobile;
		this.defense = defense;
	}
}
