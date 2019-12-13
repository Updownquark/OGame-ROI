package org.quark.ogame.uni;

import static org.quark.ogame.uni.UpgradeType.Building;
import static org.quark.ogame.uni.UpgradeType.Research;
import static org.quark.ogame.uni.UpgradeType.ShipyardItem;

public enum AccountUpgrade {
	// Buildings
	MetalMine(Building),
	CrystalMine(Building),
	DeuteriumSynthesizer(Building), //
	SolarPlant(Building),
	FusionReactor(Building), //
	MetalStorage(Building),
	CrystalStorage(Building),
	DeuteriumStorage(Building), //
	RoboticsFactory(Building),
	Shipyard(Building),
	ResearchLab(Building), //
	AllianceDepot(Building),
	MissileSilo(Building),
	NaniteFactory(Building), //
	Terraformer(Building),
	SpaceDock(Building), //
	// Moon-only Buildings
	LunarBase(Building),
	SensorPhalanx(Building),
	JumpGate(Building), //

	// Research
	Energy(Research),
	Laser(Research),
	Ion(Research),
	Hyperspace(Research), //
	Plasma(Research),
	Combustion(Research),
	Impulse(Research),
	Hyperdrive(Research), //
	Espionage(Research),
	Computer(Research),
	Astrophysics(Research),
	IntergalacticResearchNetwork(Research), //
	Graviton(Research),
	Weapons(Research),
	Shielding(Research),
	Armor(Research), //

	// Ships
	// Combat Ships
	LightFighter(ShipyardItem),
	HeavyFighter(ShipyardItem),
	Cruiser(ShipyardItem),
	Battleship(ShipyardItem), //
	Battlecruiser(ShipyardItem),
	Bomber(ShipyardItem),
	Destroyer(ShipyardItem),
	Deathstar(ShipyardItem), //
	Reaper(ShipyardItem),
	Pathfinder(ShipyardItem), //
	// Civil Ships
	SmallCargo(ShipyardItem),
	LargeCargo(ShipyardItem), //
	ColonyShip(ShipyardItem),
	Recycler(ShipyardItem),
	EspionageProbe(ShipyardItem), //
	SolarSatellite(ShipyardItem),
	Crawler(ShipyardItem), //

	// Defenses
	RocketLauncher(ShipyardItem), //
	LightLaser(ShipyardItem), //
	HeavyLaser(ShipyardItem), //
	GaussCannon(ShipyardItem), //
	IonCannon(ShipyardItem), //
	PlasmaTurret(ShipyardItem), //
	SmallShield(ShipyardItem), //
	LargeSheild(ShipyardItem), //
	AntiBallisticMissile(ShipyardItem), //
	InterPlanetaryMissile(ShipyardItem);

	public final UpgradeType type;
	public final BuildingType building;
	public final ResearchType research;
	public final ShipyardItemType shipyardItem;

	public int getLevel(Account account, RockyBody planetOrMoon) {
		switch (type) {
		case Building:
			return planetOrMoon.getBuildingLevel(building);
		case Research:
			return account.getResearch().getResearchLevel(research);
		case ShipyardItem:
			switch (shipyardItem) {
			case SolarSatellite:
				return planetOrMoon instanceof Planet ? ((Planet) planetOrMoon).getSolarSatellites() : 0;
			case Crawler:
				return planetOrMoon instanceof Planet ? ((Planet) planetOrMoon).getCrawlers() : 0;
			default:
				return 0;
			}
		}
		throw new IllegalStateException();
	}

	private AccountUpgrade(UpgradeType type) {
		this.type = type;
		switch (type) {
		case Building:
			building = BuildingType.valueOf(name());
			research = null;
			shipyardItem = null;
			break;
		case Research:
			building = null;
			research = ResearchType.valueOf(name());
			shipyardItem = null;
			break;
		case ShipyardItem:
			building = null;
			research = null;
			shipyardItem = ShipyardItemType.valueOf(name());
			break;
		default:
			throw new IllegalStateException("Unrecognized upgrade type " + type);
		}
	}

	public static AccountUpgrade getBuildingUpgrade(BuildingType type) {
		for (AccountUpgrade upgrade : values()) {
			if (upgrade.building == type) {
				return upgrade;
			}
		}
		throw new IllegalStateException();
	}

	public static AccountUpgrade getResearchUpgrade(ResearchType type) {
		for (AccountUpgrade upgrade : values()) {
			if (upgrade.research == type) {
				return upgrade;
			}
		}
		throw new IllegalStateException();
	}

	public static AccountUpgrade getShipyardItemUpgrade(ShipyardItemType type) {
		for (AccountUpgrade upgrade : values()) {
			if (upgrade.shipyardItem == type) {
				return upgrade;
			}
		}
		throw new IllegalStateException();
	}
}
