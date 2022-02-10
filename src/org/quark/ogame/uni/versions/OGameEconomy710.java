package org.quark.ogame.uni.versions;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.qommons.collect.BetterSortedSet;
import org.qommons.tree.BetterTreeSet;
import org.qommons.tree.SortedTreeList;
import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.AccountClass;
import org.quark.ogame.uni.AccountUpgradeType;
import org.quark.ogame.uni.BuildingType;
import org.quark.ogame.uni.Moon;
import org.quark.ogame.uni.OGameEconomyRuleSet;
import org.quark.ogame.uni.Planet;
import org.quark.ogame.uni.ResourceType;
import org.quark.ogame.uni.RockyBody;
import org.quark.ogame.uni.UpgradeCost;
import org.quark.ogame.uni.UpgradeType;
import org.quark.ogame.uni.Utilizable;

/** The OGame economy for version 7.1.0, just after the introduction of account classes and the class-specific ships */
public class OGameEconomy710 implements OGameEconomyRuleSet {
	static class MineProduction {
		final int base;
		final double multiplier;
		final double exponent;
		final double crawlerBonus;
		final double plasmaBonus;
		final double energyMultiplier;

		MineProduction(int base, double multiplier, double exponent, double crawlerBonus, double plasmaBonus, double energyMult) {
			this.base = base;
			this.multiplier = multiplier;
			this.exponent = exponent;
			this.crawlerBonus = crawlerBonus;
			this.plasmaBonus = plasmaBonus;
			this.energyMultiplier = energyMult;
		}
	}

	static class CostDescrip {
		final int baseMetal;
		final int baseCrystal;
		final int baseDeuterium;
		int baseEnergy;
		double resourceExponent;
		double energyExponent;
		double ecoWeight;
		double milWeight;
		List<Requirement> requirements;

		/** Building/research constructor */
		CostDescrip(int baseMetal, int baseCrystal, int baseDeuterium, double resourceExp) {
			this.baseMetal = baseMetal;
			this.baseCrystal = baseCrystal;
			this.baseDeuterium = baseDeuterium;
			this.baseEnergy = 0;
			this.resourceExponent = resourceExp;
			this.energyExponent = resourceExp;
			ecoWeight = 1;
			milWeight = 0;
			requirements = Collections.emptyList();
		}

		/** Shipyard type constructor */
		CostDescrip(int baseMetal, int baseCrystal, int baseDeuterium) {
			this(baseMetal, baseCrystal, baseDeuterium, 1);
		}

		public CostDescrip withEnergy(int base, double exponent) {
			baseEnergy = base;
			energyExponent = exponent;
			return this;
		}

		public CostDescrip withEcoWeight(double weight) {
			ecoWeight = weight;
			return this;
		}

		public CostDescrip withMilWeight(double weight) {
			milWeight = weight;
			return this;
		}

		public CostDescrip withRequirement(AccountUpgradeType type, int level) {
			if (requirements.isEmpty()) {
				requirements = new LinkedList<>();
			}
			requirements.add(new Requirement(type, level));
			return this;
		}
	}

	private static final MineProduction METAL_PRODUCTION = new MineProduction(30, 30, 1.1, .0002, 1, 10);
	private static final MineProduction CRYSTAL_PRODUCTION = new MineProduction(15, 20, 1.1, .0002, .66, 10);
	private static final MineProduction DEUT_PRODUCTION = new MineProduction(0, 10, 1.1, .0002, .33, 20);

	private static final Map<AccountUpgradeType, CostDescrip> COST_DESCRIPS;

	static {
		Map<AccountUpgradeType, CostDescrip> costs = new EnumMap<>(AccountUpgradeType.class);
		for (AccountUpgradeType upgrade : AccountUpgradeType.values()) {
			switch (upgrade) {
			// Resource Buildings
			case MetalMine:
				costs.put(upgrade, new CostDescrip(60, 15, 0, 1.5));
				break;
			case CrystalMine:
				costs.put(upgrade, new CostDescrip(48, 24, 0, 1.6));
				break;
			case DeuteriumSynthesizer:
				costs.put(upgrade, new CostDescrip(225, 75, 0, 1.5));
				break;
			case SolarPlant:
				costs.put(upgrade, new CostDescrip(75, 30, 0, 1.5));
				break;
			case FusionReactor:
				costs.put(upgrade,
					new CostDescrip(900, 360, 180, 1.8)//
					.withRequirement(AccountUpgradeType.DeuteriumSynthesizer, 5)//
						.withRequirement(AccountUpgradeType.Energy, 3));
				break;
			case MetalStorage:
				costs.put(upgrade, new CostDescrip(1000, 0, 0, 2));
				break;
			case CrystalStorage:
				costs.put(upgrade, new CostDescrip(1000, 500, 0, 2));
				break;
			case DeuteriumStorage:
				costs.put(upgrade, new CostDescrip(1000, 1000, 0, 2));
				break;
			// Facilities
			case RoboticsFactory:
				costs.put(upgrade, new CostDescrip(400, 120, 200, 2));
				break;
			case Shipyard:
				costs.put(upgrade, new CostDescrip(400, 200, 100, 2)//
					.withRequirement(AccountUpgradeType.RoboticsFactory, 2));
				break;
			case ResearchLab:
				costs.put(upgrade, new CostDescrip(200, 400, 200, 2));
				break;
			case AllianceDepot:
				costs.put(upgrade, new CostDescrip(20_000, 40_000, 0, 2));
				break;
			case MissileSilo:
				costs.put(upgrade, new CostDescrip(20_000, 20_000, 1000, 2)//
					.withRequirement(AccountUpgradeType.Shipyard, 1));
				break;
			case NaniteFactory:
				costs.put(upgrade,
					new CostDescrip(1_000_000, 500_000, 100000, 2)//
					.withRequirement(AccountUpgradeType.RoboticsFactory, 10)//
						.withRequirement(AccountUpgradeType.Computer, 10));
				break;
			case Terraformer:
				costs.put(upgrade,
					new CostDescrip(0, 50_000, 100_000, 2).withEnergy(1000, 2)//
						.withRequirement(AccountUpgradeType.NaniteFactory, 1)//
						.withRequirement(AccountUpgradeType.Energy, 12));
				break;
			case SpaceDock:
				costs.put(upgrade, new CostDescrip(200, 0, 50, 5).withEnergy(50, 2.5)//
					.withRequirement(AccountUpgradeType.Shipyard, 2));
				break;
			case LunarBase:
				costs.put(upgrade, new CostDescrip(20_000, 40_000, 20_000, 2));
				break;
			case SensorPhalanx:
				costs.put(upgrade, new CostDescrip(20_000, 40_000, 20_000, 2).withEcoWeight(.5).withMilWeight(.5)//
					.withRequirement(AccountUpgradeType.LunarBase, 1));
				break;
			case JumpGate:
				costs.put(upgrade,
					new CostDescrip(2_000_000, 4_000_000, 2_000_000, 2).withEcoWeight(.5).withMilWeight(.5)//
						.withRequirement(AccountUpgradeType.LunarBase, 1)//
						.withRequirement(AccountUpgradeType.Hyperspace, 7));
				break;

			// Research
			case Energy:
				costs.put(upgrade, new CostDescrip(0, 800, 400, 2)//
					.withRequirement(AccountUpgradeType.ResearchLab, 1));
				break;
			case Laser:
				costs.put(upgrade,
					new CostDescrip(200, 100, 0, 2)//
					.withRequirement(AccountUpgradeType.ResearchLab, 1)//
						.withRequirement(AccountUpgradeType.Energy, 2));
				break;
			case Ion:
				costs.put(upgrade,
					new CostDescrip(1000, 300, 100, 2)//
					.withRequirement(AccountUpgradeType.ResearchLab, 4)//
					.withRequirement(AccountUpgradeType.Energy, 4)//
						.withRequirement(AccountUpgradeType.Laser, 5));
				break;
			case Hyperspace:
				costs.put(upgrade,
					new CostDescrip(0, 4000, 2000, 2)//
					.withRequirement(AccountUpgradeType.ResearchLab, 7)//
					.withRequirement(AccountUpgradeType.Energy, 5)//
						.withRequirement(AccountUpgradeType.Shielding, 5));
				break;
			case Plasma:
				costs.put(upgrade,
					new CostDescrip(2000, 4000, 1000, 2)//
					.withRequirement(AccountUpgradeType.ResearchLab, 4)//
					.withRequirement(AccountUpgradeType.Energy, 8)//
					.withRequirement(AccountUpgradeType.Laser, 10)//
						.withRequirement(AccountUpgradeType.Ion, 5));
				break;
			case Combustion:
				costs.put(upgrade,
					new CostDescrip(400, 600, 0, 2)//
						.withRequirement(AccountUpgradeType.ResearchLab, 1)//
						.withRequirement(AccountUpgradeType.Energy, 1));
				break;
			case Impulse:
				costs.put(upgrade,
					new CostDescrip(2000, 4000, 600, 2)//
					.withRequirement(AccountUpgradeType.ResearchLab, 2)//
						.withRequirement(AccountUpgradeType.Energy, 1));
				break;
			case Hyperdrive:
				costs.put(upgrade,
					new CostDescrip(10000, 20000, 6000, 2)//
						.withRequirement(AccountUpgradeType.ResearchLab, 7)//
						.withRequirement(AccountUpgradeType.Hyperspace, 3));
				break;
			case Espionage:
				costs.put(upgrade, new CostDescrip(200, 1000, 200, 2)//
					.withRequirement(AccountUpgradeType.ResearchLab, 3));
				break;
			case Computer:
				costs.put(upgrade, new CostDescrip(0, 400, 600, 2)//
					.withRequirement(AccountUpgradeType.ResearchLab, 1));
				break;
			case Astrophysics:
				costs.put(upgrade,
					new CostDescrip(4000, 8000, 4000, 1.75)//
					.withRequirement(AccountUpgradeType.ResearchLab, 3)//
					.withRequirement(AccountUpgradeType.Espionage, 4)//
						.withRequirement(AccountUpgradeType.Impulse, 3));
				break;
			case IntergalacticResearchNetwork:
				costs.put(upgrade,
					new CostDescrip(240000, 400000, 160000, 2)//
					.withRequirement(AccountUpgradeType.ResearchLab, 10)//
					.withRequirement(AccountUpgradeType.Computer, 8)//
						.withRequirement(AccountUpgradeType.Hyperspace, 8));
				break;
			case Graviton:
				costs.put(upgrade, new CostDescrip(0, 0, 0, 0).withEnergy(300000, 3)//
					.withRequirement(AccountUpgradeType.ResearchLab, 12));
				break;
			case Weapons:
				costs.put(upgrade, new CostDescrip(800, 200, 0, 2)//
					.withRequirement(AccountUpgradeType.ResearchLab, 4));
				break;
			case Shielding:
				costs.put(upgrade,
					new CostDescrip(200, 600, 0, 2)//
						.withRequirement(AccountUpgradeType.ResearchLab, 6)//
						.withRequirement(AccountUpgradeType.Energy, 3));
				break;
			case Armor:
				costs.put(upgrade, new CostDescrip(1000, 0, 0, 2)//
					.withRequirement(AccountUpgradeType.ResearchLab, 2));
				break;

			// Ships
			case LightFighter:
				costs.put(upgrade,
					new CostDescrip(3000, 1000, 0).withEcoWeight(0).withMilWeight(1)//
						.withRequirement(AccountUpgradeType.Shipyard, 1)//
						.withRequirement(AccountUpgradeType.Combustion, 1));
				break;
			case HeavyFighter:
				costs.put(upgrade,
					new CostDescrip(6000, 4000, 0).withEcoWeight(0).withMilWeight(1)//
						.withRequirement(AccountUpgradeType.Shipyard, 3)//
						.withRequirement(AccountUpgradeType.Armor, 2)//
						.withRequirement(AccountUpgradeType.Impulse, 2));
				break;
			case Cruiser:
				costs.put(upgrade,
					new CostDescrip(20_000, 7000, 2000).withEcoWeight(0).withMilWeight(1)//
						.withRequirement(AccountUpgradeType.Shipyard, 5)//
						.withRequirement(AccountUpgradeType.Impulse, 4)//
						.withRequirement(AccountUpgradeType.Ion, 2));
				break;
			case BattleShip:
				costs.put(upgrade,
					new CostDescrip(45_000, 15_000, 0).withEcoWeight(0).withMilWeight(1)//
						.withRequirement(AccountUpgradeType.Shipyard, 7)//
						.withRequirement(AccountUpgradeType.Hyperdrive, 4));
				break;
			case BattleCruiser:
				costs.put(upgrade,
					new CostDescrip(30_000, 40_000, 15_000).withEcoWeight(0).withMilWeight(1)//
						.withRequirement(AccountUpgradeType.Shipyard, 8)//
						.withRequirement(AccountUpgradeType.Hyperspace, 5)//
						.withRequirement(AccountUpgradeType.Hyperdrive, 5)//
						.withRequirement(AccountUpgradeType.Laser, 12));
				break;
			case Bomber:
				costs.put(upgrade,
					new CostDescrip(50_000, 25_000, 15_000).withEcoWeight(0).withMilWeight(1)//
						.withRequirement(AccountUpgradeType.Shipyard, 8)//
						.withRequirement(AccountUpgradeType.Impulse, 6)//
						.withRequirement(AccountUpgradeType.Plasma, 5));
				break;
			case Destroyer:
				costs.put(upgrade,
					new CostDescrip(60_000, 50_000, 15_000).withEcoWeight(0).withMilWeight(1)//
						.withRequirement(AccountUpgradeType.Shipyard, 9)//
						.withRequirement(AccountUpgradeType.Hyperspace, 5)//
						.withRequirement(AccountUpgradeType.Hyperdrive, 6));
				break;
			case DeathStar:
				costs.put(upgrade,
					new CostDescrip(5_000_000, 4_000_000, 1_000_000).withEcoWeight(0).withMilWeight(1)//
						.withRequirement(AccountUpgradeType.Shipyard, 12)//
						.withRequirement(AccountUpgradeType.Hyperspace, 6)//
						.withRequirement(AccountUpgradeType.Hyperdrive, 7)//
						.withRequirement(AccountUpgradeType.Graviton, 1));
				break;
			case Reaper:
				costs.put(upgrade,
					new CostDescrip(85_000, 55_000, 20_000).withEcoWeight(0).withMilWeight(1)//
						.withRequirement(AccountUpgradeType.Shipyard, 10)//
						.withRequirement(AccountUpgradeType.Hyperspace, 6)//
						.withRequirement(AccountUpgradeType.Hyperdrive, 7)//
						.withRequirement(AccountUpgradeType.Shielding, 6));
				break;
			case PathFinder:
				costs.put(upgrade,
					new CostDescrip(8000, 15_000, 8000).withEcoWeight(0).withMilWeight(1)//
						.withRequirement(AccountUpgradeType.Shipyard, 5)//
						.withRequirement(AccountUpgradeType.Hyperspace, 2)//
						.withRequirement(AccountUpgradeType.Shielding, 4));
				break;
			case SmallCargo:
				costs.put(upgrade,
					new CostDescrip(2000, 2000, 0).withEcoWeight(.5).withMilWeight(.5)//
						.withRequirement(AccountUpgradeType.Shipyard, 2)//
						.withRequirement(AccountUpgradeType.Combustion, 2));
				break;
			case LargeCargo:
				costs.put(upgrade,
					new CostDescrip(6000, 6000, 0).withEcoWeight(.5).withMilWeight(.5)//
						.withRequirement(AccountUpgradeType.Shipyard, 4)//
						.withRequirement(AccountUpgradeType.Combustion, 6));
				break;
			case ColonyShip:
				costs.put(upgrade,
					new CostDescrip(10_000, 20_000, 10_000).withEcoWeight(.5).withMilWeight(.5)//
						.withRequirement(AccountUpgradeType.Shipyard, 4)//
						.withRequirement(AccountUpgradeType.Impulse, 3));
				break;
			case Recycler:
				costs.put(upgrade,
					new CostDescrip(10_000, 6000, 2000).withEcoWeight(.5).withMilWeight(.5)//
						.withRequirement(AccountUpgradeType.Shipyard, 4)//
						.withRequirement(AccountUpgradeType.Combustion, 6)//
						.withRequirement(AccountUpgradeType.Shielding, 2));
				break;
			case EspionageProbe:
				costs.put(upgrade,
					new CostDescrip(0, 1000, 0).withEcoWeight(.5).withMilWeight(.5)//
						.withRequirement(AccountUpgradeType.Shipyard, 3)//
						.withRequirement(AccountUpgradeType.Combustion, 3)//
						.withRequirement(AccountUpgradeType.Espionage, 2));
				break;
			case SolarSatellite:
				costs.put(upgrade, new CostDescrip(0, 2000, 500).withEcoWeight(.5).withMilWeight(.5)//
					.withRequirement(AccountUpgradeType.Shipyard, 1));
				break;
			case Crawler:
				costs.put(upgrade,
					new CostDescrip(2000, 2000, 1000).withEcoWeight(.5).withMilWeight(.5)//
						.withRequirement(AccountUpgradeType.Shipyard, 5)//
						.withRequirement(AccountUpgradeType.Combustion, 4)//
						.withRequirement(AccountUpgradeType.Armor, 4)//
						.withRequirement(AccountUpgradeType.Laser, 4));
				break;
			// StationaryStructures
			case RocketLauncher:
				costs.put(upgrade, new CostDescrip(2000, 0, 0).withMilWeight(1)//
					.withRequirement(AccountUpgradeType.Shipyard, 1));
				break;
			case LightLaser:
				costs.put(upgrade,
					new CostDescrip(1500, 500, 0).withMilWeight(1)//
						.withRequirement(AccountUpgradeType.Shipyard, 2)//
						.withRequirement(AccountUpgradeType.Energy, 1)//
						.withRequirement(AccountUpgradeType.Laser, 3));
				break;
			case HeavyLaser:
				costs.put(upgrade,
					new CostDescrip(6000, 2000, 0).withMilWeight(1)//
						.withRequirement(AccountUpgradeType.Shipyard, 4)//
						.withRequirement(AccountUpgradeType.Energy, 3)//
						.withRequirement(AccountUpgradeType.Laser, 6));
				break;
			case GaussCannon:
				costs.put(upgrade,
					new CostDescrip(20_000, 15_000, 2000).withMilWeight(1)//
						.withRequirement(AccountUpgradeType.Shipyard, 6)//
						.withRequirement(AccountUpgradeType.Energy, 6)//
						.withRequirement(AccountUpgradeType.Weapons, 3)//
						.withRequirement(AccountUpgradeType.Shielding, 1));
				break;
			case IonCannon:
				costs.put(upgrade,
					new CostDescrip(5000, 3000, 0).withMilWeight(1)//
						.withRequirement(AccountUpgradeType.Shipyard, 4)//
						.withRequirement(AccountUpgradeType.Ion, 4));
				break;
			case PlasmaTurret:
				costs.put(upgrade,
					new CostDescrip(50_000, 50_000, 30_000).withMilWeight(1)//
						.withRequirement(AccountUpgradeType.Shipyard, 8)//
						.withRequirement(AccountUpgradeType.Plasma, 7));
				break;
			case SmallShield:
				costs.put(upgrade,
					new CostDescrip(10_000, 10_000, 0).withMilWeight(1)//
						.withRequirement(AccountUpgradeType.Shipyard, 1)//
						.withRequirement(AccountUpgradeType.Shielding, 2));
				break;
			case LargeShield:
				costs.put(upgrade,
					new CostDescrip(50_000, 50_000, 0).withMilWeight(1)//
						.withRequirement(AccountUpgradeType.Shipyard, 6)//
						.withRequirement(AccountUpgradeType.Shielding, 6));
				break;
			// Missiles
			case AntiBallisticMissile:
				costs.put(upgrade,
					new CostDescrip(8000, 2000, 0).withMilWeight(1)//
						.withRequirement(AccountUpgradeType.Shipyard, 1)//
						.withRequirement(AccountUpgradeType.MissileSilo, 2));
				break;
			case InterPlanetaryMissile:
				costs.put(upgrade,
					new CostDescrip(12_500, 2500, 10_000).withMilWeight(1)//
						.withRequirement(AccountUpgradeType.Shipyard, 1)//
						.withRequirement(AccountUpgradeType.MissileSilo, 4)//
						.withRequirement(AccountUpgradeType.Impulse, 1));
				break;
			}
		}
		COST_DESCRIPS = Collections.unmodifiableMap(costs);
	}

	/** Storage amount per storage building level, starting at level 0 */
	private final BetterSortedSet<Long> STORAGE = BetterTreeSet.<Long> buildTreeSet(Long::compareTo).build().with(10L);

	@Override
	public Production getProduction(Account account, Planet planet, ResourceType resourceType, double energyFactor) {
		EnumMap<ProductionSource, Integer> byType = new EnumMap<>(ProductionSource.class);
		int totalProduced = 0;
		int totalConsumed = 0;
		if (resourceType == ResourceType.Energy) {
			// Mines
			int typeAmount = getMineEnergy(METAL_PRODUCTION, //
				planet.getMetalMine(), planet.getMetalUtilization());
			byType.put(ProductionSource.MetalMine, -typeAmount);
			totalConsumed+=typeAmount;
			typeAmount = getMineEnergy(CRYSTAL_PRODUCTION, //
				planet.getCrystalMine(), planet.getCrystalUtilization());
			byType.put(ProductionSource.CrystalMine, -typeAmount);
			totalConsumed += typeAmount;
			typeAmount = getMineEnergy(DEUT_PRODUCTION, //
				planet.getDeuteriumSynthesizer(), planet.getDeuteriumUtilization());
			byType.put(ProductionSource.DeuteriumSynthesizer, -typeAmount);
			totalConsumed += typeAmount;
			// Crawlers
			int energyPerCrawler = 50;
			int crawlers = getUsableCrawlers(account, planet);
			typeAmount = (int) Math.floor(crawlers * energyPerCrawler * Math.min(100, planet.getCrawlerUtilization()) / 100.0);
			int overclock = (planet.getCrawlerUtilization() - 100) / 10;
			if (overclock > 0) {
				// Overclocking crawlers is available in 7.5.0, but I'm putting it here because it's easier and it's not possible
				// to overclock before this patch.
				// Overclocking drains twice as much energy
				typeAmount += crawlers * energyPerCrawler * overclock / 5;
			}
			byType.put(ProductionSource.Crawler, -typeAmount);
			totalConsumed += typeAmount;

			// Producers
			typeAmount = (int) Math
				.floor(20 * planet.getSolarPlant() * Math.pow(1.1, planet.getSolarPlant()) * planet.getSolarPlantUtilization() / 100.0);
			byType.put(ProductionSource.Solar, typeAmount);
			totalProduced += typeAmount;
			typeAmount = (int) Math.floor(30.0 * planet.getFusionReactor() * planet.getFusionReactorUtilization() / 100.0
				* Math.pow(1.05 + (.01 * account.getResearch().getEnergy()), planet.getFusionReactor()));
			byType.put(ProductionSource.Fusion, typeAmount);
			totalProduced += typeAmount;
			typeAmount = (int) Math.floor(
				getSatelliteEnergy(account, planet) * planet.getSolarSatellites() * 1.0 * planet.getSolarSatelliteUtilization() / 100.0);
			byType.put(ProductionSource.Satellite, typeAmount);
			totalProduced += typeAmount;
			int baseProduced = totalProduced;

			int bonus = planet.getEnergyBonus();
			typeAmount = (int) Math.round(baseProduced * 1.0 * bonus / 100.0);
			byType.put(ProductionSource.Item, typeAmount);
			totalProduced += typeAmount;

			if (account.getGameClass() == AccountClass.Collector) {
				typeAmount = (int) Math.floor(baseProduced * 0.10);
				byType.put(ProductionSource.Collector, typeAmount);
				totalProduced += typeAmount;
			}
			if (account.getOfficers().isEngineer()) {
				typeAmount = (int) Math.floor(baseProduced * 0.10);
				byType.put(ProductionSource.Engineer, typeAmount);
				totalProduced += typeAmount;
			}
			if (account.getOfficers().isCommandingStaff()) {
				typeAmount = (int) Math.floor(baseProduced * 0.02);
				byType.put(ProductionSource.CommandingStaff, typeAmount);
				totalProduced += typeAmount;
			}
		} else {
			MineProduction production = null;
			int level = 0, bonus = 0, utilization = 0;
			ProductionSource mineType=null;
			switch (resourceType) {
			case Metal:
				production = METAL_PRODUCTION;
				level = planet.getMetalMine();
				bonus = planet.getMetalBonus();
				utilization = planet.getMetalUtilization();
				mineType = ProductionSource.MetalMine;
				break;
			case Crystal:
				production = CRYSTAL_PRODUCTION;
				level = planet.getCrystalMine();
				bonus = planet.getCrystalBonus();
				utilization = planet.getCrystalUtilization();
				mineType = ProductionSource.CrystalMine;
				break;
			case Deuterium:
				production = DEUT_PRODUCTION;
				level = planet.getDeuteriumSynthesizer();
				bonus = planet.getDeuteriumBonus();
				utilization = planet.getDeuteriumUtilization();
				mineType = ProductionSource.DeuteriumSynthesizer;
				break;
			case Energy:
				break;
			}
			if (production == null) {
				throw new IllegalStateException();
			}

			if (energyFactor < 0) {
				energyFactor = 0;
			} else if (energyFactor > 1) {
				energyFactor=1;
			}
			int typeAmount = production.base * account.getUniverse().getEconomySpeed();
			byType.put(ProductionSource.Base, typeAmount);
			totalProduced += typeAmount;

			// Mine production
			double mineP = production.multiplier * level * Math.pow(production.exponent, level) * account.getUniverse().getEconomySpeed()
				* energyFactor * (utilization / 100.0);
			if (resourceType == ResourceType.Deuterium) {
				int avgT = (planet.getMinimumTemperature() + planet.getMaximumTemperature()) / 2;
				double mult = 1.36 - 0.004 * avgT;
				mineP *= mult;
			}
			int mineProduction = (int) Math.floor(mineP);
			typeAmount = mineProduction;
			byType.put(mineType, typeAmount);
			totalProduced += typeAmount;

			double multiplier = getSlotProductionMultiplier(account, planet, resourceType);
			if (multiplier > 0) {
				typeAmount = (int) Math.round(totalProduced * multiplier);
				mineProduction = (int) Math.round((1 + multiplier) * mineProduction);
				byType.put(ProductionSource.Slot, typeAmount);
				totalProduced += typeAmount;
			} else {
				byType.put(ProductionSource.Slot, 0);
			}

			// Crawler production
			int crawlers = getUsableCrawlers(account, planet);
			double crawlerBonus = production.crawlerBonus;
			if (account.getGameClass() == AccountClass.Collector) {
				crawlerBonus *= 1.5;
			}
			typeAmount = (int) Math
				.round(mineProduction * crawlerBonus * crawlers * (planet.getCrawlerUtilization() / 100.0));
			byType.put(ProductionSource.Crawler, typeAmount);
			totalProduced += typeAmount;

			// Plasma bonus
			typeAmount = (int) Math.round(mineProduction * production.plasmaBonus / 100 * account.getResearch().getPlasma());
			byType.put(ProductionSource.Plasma, typeAmount);
			totalProduced += typeAmount;

			// Class bonus
			if (account.getGameClass() == AccountClass.Collector) {
				int collectorBonus = resourceType == ResourceType.Energy ? account.getUniverse().getCollectorEnergyBonus()
					: account.getUniverse().getCollectorProductionBonus();
				typeAmount = (int) Math.round(mineProduction * 1.0 * collectorBonus / 100.0);
			} else {
				typeAmount = 0;
			}
			byType.put(ProductionSource.Collector, typeAmount);
			totalProduced += typeAmount;

			// Fusion consumption
			if (resourceType == ResourceType.Deuterium) {
				typeAmount = -(int) Math.floor(10.0 * planet.getFusionReactor() * Math.pow(1.1, planet.getFusionReactor())
					* (planet.getFusionReactorUtilization() / 100.0) * account.getUniverse().getEconomySpeed());
				byType.put(ProductionSource.Fusion, typeAmount);
				totalConsumed += -typeAmount;
			} else {
				byType.put(ProductionSource.Fusion, 0);
			}

			// Active items
			typeAmount = (int) Math.round(mineProduction * 1.0 * bonus / 100.0);
			byType.put(ProductionSource.Item, typeAmount);
			totalProduced += typeAmount;

			// Officers
			typeAmount = 0;
			if (account.getOfficers().isGeologist()) {
				typeAmount = (int) Math.round(mineProduction * 0.1);
			}
			byType.put(ProductionSource.Geologist, typeAmount);
			totalProduced += typeAmount;
			typeAmount = 0;
			if (account.getOfficers().isCommandingStaff()) {
				typeAmount = (int) Math.round(mineProduction * 0.02);
			}
			byType.put(ProductionSource.CommandingStaff, typeAmount);
			totalProduced += typeAmount;
		}
		return new Production(byType, totalProduced, totalConsumed);
	}

	protected int getMineEnergy(MineProduction production, int level, int utilization) {
		return (int) Math.floor(production.energyMultiplier * level * utilization / 100.0 * Math.pow(1.1, level));
	}

	protected double getSlotProductionMultiplier(Account account, Planet planet, ResourceType resource) {
		return 0;
	}

	protected int getUsableCrawlers(Account account, Planet planet) {
		int crawlers = planet.getCrawlers();
		int crawlerCap = getMaxCrawlers(account, planet);
		return Math.min(crawlers, crawlerCap);
	}

	@Override
	public int getMaxCrawlers(Account account, Planet planet) {
		return (planet.getMetalMine() + planet.getCrystalMine() + planet.getDeuteriumSynthesizer()) * 8;
	}

	@Override
	public int getMaxUtilization(Utilizable utilizable, Account account, Planet planet) {
		return 100;
	}

	@Override
	public int getSatelliteEnergy(Account account, Planet planet) {
		return (int) Math.floor(((planet.getMinimumTemperature() + planet.getMaximumTemperature()) / 2.0 + 160) / 6);
	}

	@Override
	public synchronized long getStorage(Planet planet, ResourceType resourceType) {
		int level;
		switch (resourceType) {
		case Metal:
			level = planet.getMetalStorage();
			break;
		case Crystal:
			level = planet.getCrystalStorage();
			break;
		case Deuterium:
			level = planet.getDeuteriumStorage();
			break;
		default:
			return Long.MAX_VALUE;
		}
		if (level < 0) {
			throw new IndexOutOfBoundsException(level + "<0");
		}
		while (level >= STORAGE.size()) {
			STORAGE.add(5 * (long) Math.floor(2.5 * Math.exp(20.0 / 33 * STORAGE.size())));
		}
		return STORAGE.get(level).longValue() * 1000;
	}

	@Override
	public List<Requirement> getRequirements(AccountUpgradeType target) {
		CostDescrip cost = COST_DESCRIPS.get(target);
		return cost.requirements.isEmpty() ? cost.requirements : Collections.unmodifiableList(cost.requirements);
	}

	@Override
	public UpgradeCost getUpgradeCost(Account account, RockyBody planetOrMoon, AccountUpgradeType upgrade, int fromLevel, int toLevel) {
		if (fromLevel == toLevel) {
			return UpgradeCost.ZERO;
		}
		CostDescrip cost = COST_DESCRIPS.get(upgrade);
		long[] resAmounts = new long[4];
		resAmounts[0] = cost.baseMetal;
		resAmounts[1] = cost.baseCrystal;
		resAmounts[2] = cost.baseDeuterium;
		resAmounts[3] = cost.baseEnergy;
		char t = 0;
		switch (upgrade.type) {
		case Building:
		case Research:
			double resMult = 1;
			t = upgrade.type.name().charAt(0);
			resMult = Math.pow(cost.resourceExponent, Math.min(fromLevel, toLevel))
				* ((1 - Math.pow(cost.resourceExponent, Math.abs(toLevel - fromLevel))) / (1 - cost.resourceExponent));
			if (toLevel < fromLevel) {
				resMult = resMult / cost.resourceExponent * Math.max(1 - account.getResearch().getIon() * 0.04, 0);
			}
			for (int i = 0; i < 3; i++) {
				resAmounts[i] = Math.round(resAmounts[i] * resMult);
			}
			break;
		case ShipyardItem:
			t = 's';
			if (toLevel < fromLevel) {
				resMult = (toLevel - fromLevel) * 0.35; // Scrapping
				for (int i = 0; i < 3; i++) {
					resAmounts[i] = -Math.round(resAmounts[i] * resMult);
				}
			} else {
				for (int i = 0; i < 3; i++) {
					resAmounts[i] = Math.round(resAmounts[i] * (toLevel - fromLevel));
				}
			}
			break;
		}
		if (cost.energyExponent > 1 && toLevel > fromLevel) {
			double mult = Math.pow(cost.energyExponent, Math.min(fromLevel, toLevel))
				* ((1 - Math.pow(cost.energyExponent, Math.abs(toLevel - fromLevel))) / (1 - cost.energyExponent));
			resAmounts[3] = (long) Math.floor(resAmounts[3] * mult);
		}
		long seconds = getUpgradeTime(account, planetOrMoon, upgrade, resAmounts);
		Duration time = Duration.ofSeconds(seconds);
		if (upgrade.type == UpgradeType.Research) {
			return UpgradeCost.of(t, resAmounts[0], resAmounts[1], resAmounts[2], (int) resAmounts[3], time, 0, 1, 0);
		} else {
			return UpgradeCost.of(t, resAmounts[0], resAmounts[1], resAmounts[2], (int) resAmounts[3], time, cost.ecoWeight, 0,
				cost.milWeight);
		}
	}

	protected long getUpgradeTime(Account account, RockyBody planetOrMoon, AccountUpgradeType upgrade, long [] resAmounts){
		double hours = 0;
		switch (upgrade.type) {
		case Building:
			if (planetOrMoon == null) {
				return 0; //Null planet means they don't care about the time
			}
			hours = (resAmounts[0] + resAmounts[1]) / 2500 / account.getUniverse().getEconomySpeed();
			hours /= (1 + planetOrMoon.getRoboticsFactory());
			int nanite = planetOrMoon.getBuildingLevel(BuildingType.NaniteFactory);
			if (nanite > 0) {
				hours /= Math.pow(2, nanite);
			}
			break;
		case Research:
			hours = (resAmounts[0] + resAmounts[1]) / 1000 / account.getUniverse().getResearchSpeed();
			int labLevels=getTotalLabLevels(account, planetOrMoon);
			hours/=labLevels;
			break;
		case ShipyardItem:
			if (planetOrMoon == null) {
				return 0; //Null planet means they don't care about the time
			}
			hours = (resAmounts[0] + resAmounts[1]) / 2500 / account.getUniverse().getEconomySpeed();
			hours /= (1 + planetOrMoon.getShipyard());
			nanite = planetOrMoon.getBuildingLevel(BuildingType.NaniteFactory);
			if (nanite > 0) {
				hours /= Math.pow(2, nanite);
			}
			break;
		}
		long seconds = Math.round(hours * 3600);
		if (seconds == 0) {
			seconds = 1;
		}
		return seconds;
	}

	protected int getTotalLabLevels(Account account, RockyBody planetOrMoon) {
		if (planetOrMoon == null) {
			if (account.getPlanets().getValues().isEmpty()) {
				return 1; //Just to avoid getting arithmetic exceptions
			}
			planetOrMoon=account.getPlanets().getValues().getFirst();
		}
		int levels = planetOrMoon.getBuildingLevel(BuildingType.ResearchLab);
		int irn = account.getResearch().getIntergalacticResearchNetwork();
		if (irn > 0) {
			SortedTreeList<Integer> labLevels = SortedTreeList.<Integer> buildTreeList((i1, i2) -> -Integer.compare(i1, i2)).build();
			for (Planet planet : account.getPlanets().getValues()) {
				// if (planet != planetOrMoon) { The app doesn't actually care where the research starts from, so just use the best case
					labLevels.add(planet.getBuildingLevel(BuildingType.ResearchLab));
				// }
			}
			int linkedLabs = 0;
			for (Integer lab : labLevels) {
				levels += lab;
				if (++linkedLabs == irn) {
					break;
				}
			}
		}
		return levels;
	}

	@Override
	public int getMaxPlanets(Account account) {
		int astro = account.getResearch().getAstrophysics();
		return (astro + 1) / 2 + 1;
	}

	@Override
	public int getFields(Planet planet) {
		int fields = planet.getBaseFields();
		int terraformer = planet.getTerraformer();
		fields += getTerraformerFields(terraformer);
		return fields;
	}

	protected int getTerraformerFields(int terraformerLevel) {
		return (int) Math.floor(terraformerLevel * 5.5);
	}

	@Override
	public int getFields(Moon moon) {
		return 1 + moon.getLunarBase() * 3 + moon.getFieldBonus();
	}
}
