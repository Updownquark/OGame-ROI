package org.quark.ogame;

import java.text.DecimalFormat;

import org.qommons.io.Format;
import org.quark.ogame.uni.ShipyardItemType;

public class OGameUtils {
	public static final Format<Double> FORMAT = Format.doubleFormat(5).printIntFor(6, false).withExpCondition(4, 0)//
		.withPrefix("K", 3)//
		.withPrefix("M", 6)//
		.withPrefix("B", 9)//
		.withPrefix("T", 12)//
		.withPrefix("Q", 12)//
		.build();

	public static String printResourceAmount(double amount) {
		return FORMAT.format(amount);
	}

	public static final DecimalFormat WHOLE_FORMAT = new DecimalFormat("#,##0");
	public static final DecimalFormat TWO_DIGIT_FORMAT = new DecimalFormat("#,##0.00");
	public static final DecimalFormat THREE_DIGIT_FORMAT = new DecimalFormat("#,##0.000");

	public static String abbreviate(ShipyardItemType type) {
		switch (type) {
		case SmallCargo:
			return "SC";
		case LargeCargo:
			return "LC";
		case ColonyShip:
			return "Col";
		case Recycler:
			return "Rec";
		case EspionageProbe:
			return "Probe";
		case LightFighter:
			return "LF";
		case HeavyFighter:
			return "HF";
		case Cruiser:
			return "Cr";
		case BattleShip:
			return "BS";
		case BattleCruiser:
			return "BC";
		case Bomber:
			return "Bomb";
		case Destroyer:
			return "Des";
		case DeathStar:
			return "RIP";
		case Reaper:
			return "Reap";
		case PathFinder:
			return "PF";
		case Crawler:
			return "Crawler";
		case SolarSatellite:
			return "Sat";
		case RocketLauncher:
			return "RL";
		case LightLaser:
			return "LL";
		case HeavyLaser:
			return "HL";
		case GaussCannon:
			return "GC";
		case IonCannon:
			return "IC";
		case PlasmaTurret:
			return "PT";
		case SmallShield:
			return "SS";
		case LargeShield:
			return "LS";
		case AntiBallisticMissile:
			return "ABM";
		case InterPlanetaryMissile:
			return "IPM";
		}
		throw new IllegalStateException("Unrecognized ship: " + type);
	}
}
