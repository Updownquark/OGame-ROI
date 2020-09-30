package org.quark.ogame.uni;

import static org.quark.ogame.uni.UpgradeType.Building;
import static org.quark.ogame.uni.UpgradeType.Research;
import static org.quark.ogame.uni.UpgradeType.ShipyardItem;

public enum AccountUpgradeType {
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

	// Ships
	// Combat Ships
	LightFighter(ShipyardItem),
	HeavyFighter(ShipyardItem),
	Cruiser(ShipyardItem),
	BattleShip(ShipyardItem), //
	BattleCruiser(ShipyardItem),
	Bomber(ShipyardItem),
	Destroyer(ShipyardItem),
	DeathStar(ShipyardItem), //
	Reaper(ShipyardItem),
	PathFinder(ShipyardItem), //
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
	LargeShield(ShipyardItem), //
	AntiBallisticMissile(ShipyardItem), //
	InterPlanetaryMissile(ShipyardItem);

	public final UpgradeType type;
	public final BuildingType building;
	public final ResearchType research;
	public final ShipyardItemType shipyardItem;

	public int getLevel(Account account, RockyBody planetOrMoon) {
		switch (type) {
		case Building:
			if (planetOrMoon == null) {
				throw new NullPointerException(); //Good place to stick a breakpoint
			}
			return planetOrMoon.getBuildingLevel(building);
		case Research:
			return account.getResearch().getResearchLevel(research);
		case ShipyardItem:
			if (planetOrMoon == null) {
				throw new NullPointerException(); //Good place to stick a breakpoint
			}
			return planetOrMoon.getStationedShips(shipyardItem);
		}
		throw new IllegalStateException();
	}

	public void setLevel(Account account, RockyBody planetOrMoon, int level) {
		switch (type) {
		case Building:
			planetOrMoon.setBuildingLevel(building, level);
			return;
		case Research:
			account.getResearch().setResearchLevel(research, level);
			return;
		case ShipyardItem:
			planetOrMoon.setStationedShips(shipyardItem, level);
			return;
		}
		throw new IllegalStateException();
	}

	private AccountUpgradeType(UpgradeType type) {
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

	public static AccountUpgradeType getBuildingUpgrade(BuildingType type) {
		for (AccountUpgradeType upgrade : values()) {
			if (upgrade.building == type) {
				return upgrade;
			}
		}
		throw new IllegalStateException();
	}

	public static AccountUpgradeType getResearchUpgrade(ResearchType type) {
		for (AccountUpgradeType upgrade : values()) {
			if (upgrade.research == type) {
				return upgrade;
			}
		}
		throw new IllegalStateException();
	}

	public static AccountUpgradeType getShipyardItemUpgrade(ShipyardItemType type) {
		for (AccountUpgradeType upgrade : values()) {
			if (upgrade.shipyardItem == type) {
				return upgrade;
			}
		}
		throw new IllegalStateException();
	}

	public static AccountUpgradeType getStorage(ResourceType resType) {
		switch (resType) {
		case Metal:
			return MetalStorage;
		case Crystal:
			return AccountUpgradeType.CrystalStorage;
		case Deuterium:
			return DeuteriumStorage;
		default:
			throw new IllegalArgumentException(resType + " does not have storage");
		}
	}
}
