package org.quark.ogame.uni.versions;

import static org.quark.ogame.uni.AccountClass.Collector;
import static org.quark.ogame.uni.AccountClass.General;
import static org.quark.ogame.uni.ResearchType.Combustion;
import static org.quark.ogame.uni.ResearchType.Hyperdrive;
import static org.quark.ogame.uni.ResearchType.Impulse;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.AccountClass;
import org.quark.ogame.uni.FleetRules;
import org.quark.ogame.uni.Research;
import org.quark.ogame.uni.ResearchType;
import org.quark.ogame.uni.ShipyardItemType;
import org.quark.ogame.uni.Universe;

public class OGameFleet710 implements FleetRules {
	static class ClassBonus {
		final AccountClass clazz;
		final int speedBonus;
		final int fuelBonus;
		final int storageBonus;

		ClassBonus(AccountClass clazz, int speedBonus, int fuelBonus, int storageBonus) {
			this.clazz = clazz;
			this.speedBonus = speedBonus;
			this.fuelBonus = fuelBonus;
			this.storageBonus = storageBonus;
		}
	}

	static class ShipInfo {
		final int baseSpeed;
		final int baseFuelConsumption;
		final int baseCargo;
		final ResearchType driveType;
		int theImpulseUpgrade;
		int theHyperdriveUpgrade;
		List<ClassBonus> classBonuses;

		ShipInfo(int baseSpeed, int baseFuel, int baseCargo, ResearchType driveType) {
			this.baseSpeed = baseSpeed;
			this.baseFuelConsumption = baseFuel;
			this.baseCargo = baseCargo;
			this.driveType = driveType;
		}

		ShipInfo withDriveUpgrade(ResearchType type, int level) {
			switch (type) {
			case Impulse:
				theImpulseUpgrade = level;
				break;
			case Hyperdrive:
				theHyperdriveUpgrade = level;
				break;
			default:
				throw new IllegalArgumentException("Unrecognized drive upgrade type: " + type);
			}
			return this;
		}

		ShipInfo withClassBonus(AccountClass clazz, int speedBonus, int fuelBonus, int storageBonus) {
			if (classBonuses == null) {
				classBonuses = new LinkedList<>();
			}
			classBonuses.add(new ClassBonus(clazz, speedBonus, fuelBonus, storageBonus));
			return this;
		}

		ResearchType getDriveType(Research research) {
			if (theHyperdriveUpgrade > 0 && research.getHyperspaceDrive() >= theHyperdriveUpgrade) {
				return ResearchType.Hyperdrive;
			} else if (theImpulseUpgrade > 0 && research.getImpulseDrive() >= theImpulseUpgrade) {
				return ResearchType.Impulse;
			} else {
				return driveType;
			}
		}

		int getSpeedBonus(Account account) {
			if (classBonuses == null) {
				return 0;
			}
			for (ClassBonus bonus : classBonuses) {
				if (bonus.clazz == account.getGameClass()) {
					return bonus.speedBonus;
				}
			}
			return 0;
		}

		int getFuelBonus(Account account) {
			if (classBonuses == null) {
				return 0;
			}
			for (ClassBonus bonus : classBonuses) {
				if (bonus.clazz == account.getGameClass()) {
					return bonus.fuelBonus;
				}
			}
			return 0;
		}
	}

	private static Map<ShipyardItemType, ShipInfo> SHIP_INFO;

	static {
		Map<ShipyardItemType, ShipInfo> shipInfo = new LinkedHashMap<>();
		for (ShipyardItemType type : ShipyardItemType.values()) {
			switch (type) {
			case SmallCargo:
				shipInfo.put(type,
					new ShipInfo(10000, 20, 5000, Combustion)//
						.withClassBonus(Collector, 100, 0, 25)//
						.withClassBonus(General, 0, 25, 0)//
						.withDriveUpgrade(Impulse, 5));
				break;
			case LargeCargo:
				shipInfo.put(type, new ShipInfo(7500, 50, 25000, Combustion)//
					.withClassBonus(Collector, 100, 0, 25));
				break;
			case ColonyShip:
				shipInfo.put(type, new ShipInfo(2500, 1000, 7500, Impulse)//
					.withClassBonus(General, 0, 25, 0));
				break;
			case Recycler:
				shipInfo.put(type,
					new ShipInfo(2000, 300, 20000, Combustion)//
						.withClassBonus(General, 100, 25, 0)//
						.withDriveUpgrade(Impulse, 17).withDriveUpgrade(Hyperdrive, 15));
				break;
			case EspionageProbe:
				shipInfo.put(type, new ShipInfo(100_000_000, 1, 0, Combustion)//
					.withClassBonus(General, 0, 25, 0));
				break;
			case LightFighter:
				shipInfo.put(type, new ShipInfo(12500, 20, 50, Combustion)//
					.withClassBonus(General, 100, 25, 0));
				break;
			case HeavyFighter:
				shipInfo.put(type, new ShipInfo(10000, 75, 100, Impulse)//
					.withClassBonus(General, 100, 25, 0));
				break;
			case Cruiser:
				shipInfo.put(type, new ShipInfo(15000, 300, 800, Impulse)//
					.withClassBonus(General, 100, 25, 0));
				break;
			case BattleShip:
				shipInfo.put(type, new ShipInfo(10000, 500, 1500, Hyperdrive)//
					.withClassBonus(General, 100, 25, 0));
				break;
			case BattleCruiser:
				shipInfo.put(type, new ShipInfo(10000, 250, 750, Hyperdrive)//
					.withClassBonus(General, 100, 25, 0));
				break;
			case Bomber:
				shipInfo.put(type,
					new ShipInfo(5000, 700, 500, Impulse)//
						.withDriveUpgrade(Hyperdrive, 8)//
						.withClassBonus(General, 100, 25, 0));
				break;
			case Destroyer:
				shipInfo.put(type, new ShipInfo(5000, 1000, 2000, Hyperdrive)//
					.withClassBonus(General, 100, 25, 0));
				break;
			case DeathStar:
				shipInfo.put(type, new ShipInfo(100, 1, 1_000_000, Hyperdrive)//
					.withClassBonus(General, 100, 25, 0));
				break;
			case Reaper:
				shipInfo.put(type, new ShipInfo(7000, 1100, 10000, Hyperdrive)//
					.withClassBonus(General, 100, 25, 0));
				break;
			case PathFinder:
				shipInfo.put(type, new ShipInfo(12000, 300, 10000, Hyperdrive)//
					.withClassBonus(General, 0, 25, 0));
				break;
			case SolarSatellite:
			case Crawler:
			case RocketLauncher:
			case LightLaser:
			case HeavyLaser:
			case GaussCannon:
			case IonCannon:
			case PlasmaTurret:
			case SmallShield:
			case LargeShield:
			case AntiBallisticMissile:
			case InterPlanetaryMissile:
			}
		}
		SHIP_INFO = Collections.unmodifiableMap(shipInfo);
	}

	@Override
	public int getCargoSpace(ShipyardItemType type, Account account) {
		ShipInfo info = SHIP_INFO.get(type);
		if (info == null) {
			return 0;
		}
		double bonus = 0;
		switch (type) {
		case SmallCargo:
		case LargeCargo:
			if (account.getGameClass() == AccountClass.Collector) {
				bonus += .25;
			}
			break;
		default:
			break;
		}
		bonus += account.getUniverse().getHyperspaceCargoBonus() / 100 * account.getResearch().getHyperspace();
		return (int) Math.floor(info.baseCargo * (1 + bonus));
	}

	@Override
	public int getDistance(Universe universe, int sourceGalaxy, int sourceSystem, int sourceSlot, int destGalaxy, int destSystem,
		int destSlot) {
		if (sourceGalaxy != destGalaxy) {
			return 20000 * getDistance(sourceGalaxy, destGalaxy, //
				universe.getGalaxies(), universe.isCircularUniverse());
		} else if (sourceSystem != destSystem) {
			return 2700 + 95 * getDistance(sourceSystem, destSystem, 499, universe.isCircularGalaxies());
		} else if (sourceSlot != destSlot) {
			return 1000 + 5 * Math.abs(sourceSlot - destSlot);
		} else {
			return 5; // Between Planet/Moon/DF or vice versa
		}
	}

	private static int getDistance(int source, int dest, int max, boolean circular) {
		int dist = Math.abs(dest - source);
		if (dist >= max - 1) {
			dist = max - dist;
		}
		return dist;
	}

	@Override
	public int getFuelConsumption(ShipyardItemType type, Account account) {
		ShipInfo info = SHIP_INFO.get(type);
		if (info == null) {
			return 0;
		}
		int baseFuel = info.baseFuelConsumption;
		int bonus = info.getFuelBonus(account);
		if (bonus == 0) {
			return baseFuel;
		}
		return (int) Math.ceil(baseFuel * (1 - bonus / 100.0));
	}

	@Override
	public int getSpeed(ShipyardItemType type, Account account) {
		ShipInfo info = SHIP_INFO.get(type);
		if (info == null) {
			return 0;
		}
		int bonus = info.getSpeedBonus(account);
		ResearchType driveType = info.getDriveType(account.getResearch());
		int driveBonus;
		switch (driveType) {
		case Combustion:
			driveBonus = 10;
			break;
		case Impulse:
			driveBonus = 20;
			break;
		case Hyperdrive:
			driveBonus = 30;
			break;
		default:
			throw new IllegalStateException("Unrecognized drive type: " + driveType);
		}
		int research = account.getResearch().getResearchLevel(driveType);
		int totalBonus = bonus + research * driveBonus;
		return (int) Math.floor(info.baseSpeed * (1 + totalBonus / 100.0));
	}

	@Override
	public Duration getFlightTime(int maxSpeed, int distance, int speedPercent) {
		long seconds = Math.round(35000 * 10.0 / speedPercent * Math.sqrt(distance * 10.0 / maxSpeed) + 10);
		return Duration.ofSeconds(seconds);
	}

	@Override
	public double getFuelConsumption(ShipyardItemType type, Account account, int distance, Duration flightTime) {
		int maxSpeed = getSpeed(type, account);
		double speed = 35000 / (flightTime.getSeconds() - 10.0) * Math.sqrt(distance * 10.0 / maxSpeed);
		double speedOver10Plus1 = speed / 10.0 + 1;
		int rate = getFuelConsumption(type, account);
		return rate * distance / 35000.0 * speedOver10Plus1 * speedOver10Plus1;
	}
}
