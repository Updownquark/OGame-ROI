package org.quark.ogame;

import java.text.DecimalFormat;
import java.util.Arrays;

import org.qommons.ArrayUtils;
import org.qommons.BiTuple;
import org.qommons.io.Format;
import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.OGameEconomyRuleSet;
import org.quark.ogame.uni.OGameEconomyRuleSet.FullProduction;
import org.quark.ogame.uni.OGameEconomyRuleSet.Production;
import org.quark.ogame.uni.OGameEconomyRuleSet.ProductionSource;
import org.quark.ogame.uni.Planet;
import org.quark.ogame.uni.ResourceType;
import org.quark.ogame.uni.ShipyardItemType;
import org.quark.ogame.uni.TradeRatios;
import org.quark.ogame.uni.Utilizable;

public class OGameUtils {
	public static final Format<Double> FORMAT = Format.doubleFormat(5).printIntFor(6, false).withExpCondition(4, -1)//
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

	public static int getRequiredSatellites(Account account, Planet planet, OGameEconomyRuleSet economy) {
		Production energy = economy.getProduction(account, planet, ResourceType.Energy, 1);
		int energyNeeded = -energy.totalNet + energy.byType.get(ProductionSource.Satellite);
		int newSats;
		if (energyNeeded < 0) {
			newSats = 0;
		} else {
			int satEnergy = economy.getSatelliteEnergy(account, planet);
			if (satEnergy <= 0) {
				return -1;
			}
			newSats = (int) Math.ceil(energyNeeded * 1.0 / satEnergy);
			int preSats = planet.getSolarSatellites();
			planet.setSolarSatellites(newSats);
			int energyExcess = economy.getProduction(account, planet, ResourceType.Energy, 1).totalNet;
			if (energyExcess > satEnergy) {
				// Bonuses like for Collector or Officers can bump this up
				int removeSats = (int) Math.floor(energyExcess * 1.0 / (energyExcess + energyNeeded) * newSats);
				newSats -= removeSats;
				planet.setSolarSatellites(newSats);
				energyExcess = economy.getProduction(account, planet, ResourceType.Energy, 1).totalNet;
				if (energyExcess < 0) {
					newSats++;
				}
			}
			planet.setSolarSatellites(preSats);
		}
		return newSats;
	}

	public static FullProduction optimizeEnergy(Account account, Planet planet, OGameEconomyRuleSet economy) {
		int maxFusion = economy.getMaxUtilization(Utilizable.FusionReactor, account, planet);
		int maxCrawler = economy.getMaxUtilization(Utilizable.Crawler, account, planet);
		planet.setFusionReactorUtilization(maxFusion);
		planet.setCrawlerUtilization(maxCrawler);
		// Find the fusion/crawler utilization combination with the best production
		BiTuple<FullProduction, Double> bestProduction = optimizeCrawlerUtil(account, planet, economy);
		if (planet.getFusionReactor() > 0) {
			for (int f = maxFusion - 10; f >= 0; f -= 10) {
				int preCrawlerUtil = planet.getCrawlerUtilization();
				planet.setFusionReactorUtilization(f);
				BiTuple<FullProduction, Double> production = optimizeCrawlerUtil(account, planet, economy);
				if (production.getValue2() < bestProduction.getValue2()) {
					planet.setFusionReactorUtilization(f + 10);
					planet.setCrawlerUtilization(preCrawlerUtil);
					break;
				} else {
					bestProduction=production;
				}
			}
		}
		return bestProduction.getValue1();
	}

	private static BiTuple<FullProduction, Double> optimizeCrawlerUtil(Account account, Planet planet, OGameEconomyRuleSet eco) {
		int maxCrawler = eco.getMaxUtilization(Utilizable.Crawler, account, planet);
		double[] productions = new double[maxCrawler / 10 + 1];
		FullProduction[] fullProductions = new FullProduction[productions.length];
		Arrays.fill(productions, Double.NaN);
		TradeRatios tr = account.getUniverse().getTradeRatios();
		int best = ArrayUtils.binarySearch(0, productions.length, util -> {
			if (Double.isNaN(productions[util])) {
				planet.setCrawlerUtilization(util * 10);
				fullProductions[util] = eco.getFullProduction(account, planet);
				productions[util] = fullProductions[util].asCost().getMetalValue(tr);
			}
			if (util > 0 && Double.isNaN(productions[util - 1])) {
				planet.setCrawlerUtilization((util - 1) * 10);
				fullProductions[util - 1] = eco.getFullProduction(account, planet);
				productions[util - 1] = fullProductions[util - 1].asCost().getMetalValue(tr);
				if (productions[util - 1] > productions[util]) {
					return -1;
				}
			}
			if (util < productions.length - 1 && Double.isNaN(productions[util + 1])) {
				planet.setCrawlerUtilization((util + 1) * 10);
				fullProductions[util + 1] = eco.getFullProduction(account, planet);
				productions[util + 1] = fullProductions[util + 1].asCost().getMetalValue(tr);
				if (productions[util + 1] > productions[util]) {
					return 1;
				}
			}
			return 0;
		});
		planet.setCrawlerUtilization(best * 10);
		return new BiTuple<>(fullProductions[best], productions[best]);
	}
}
