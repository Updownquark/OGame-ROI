package org.quark.ogame.uni.versions;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.qommons.collect.BetterSortedSet;
import org.qommons.tree.BetterTreeSet;
import org.qommons.tree.SortedTreeList;
import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.AccountClass;
import org.quark.ogame.uni.AccountUpgrade;
import org.quark.ogame.uni.BuildingType;
import org.quark.ogame.uni.Moon;
import org.quark.ogame.uni.OGameEconomyRuleSet;
import org.quark.ogame.uni.Planet;
import org.quark.ogame.uni.ResourceType;
import org.quark.ogame.uni.RockyBody;
import org.quark.ogame.uni.UpgradeCost;

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
		final int baseEnergy;
		final double resourceExponent;
		final double energyExponent;

		public CostDescrip(int baseMetal, int baseCrystal, int baseDeuterium, int baseEnergy, double resourceExponent,
			double energyExponent) {
			this.baseMetal = baseMetal;
			this.baseCrystal = baseCrystal;
			this.baseDeuterium = baseDeuterium;
			this.baseEnergy = baseEnergy;
			this.resourceExponent = resourceExponent;
			this.energyExponent = energyExponent;
		}

		public CostDescrip(int baseMetal, int baseCrystal, int baseDeuterium, double exponent) {
			this(baseMetal, baseCrystal, baseDeuterium, 0, exponent, 1);
		}

		public CostDescrip(int baseMetal, int baseCrystal, int baseDeuterium) {
			this(baseMetal, baseCrystal, baseDeuterium, 0, 1, 1);
		}
	}

	private static final MineProduction METAL_PRODUCTION = new MineProduction(30, 30, 1.1, .0002, 1, 10);
	private static final MineProduction CRYSTAL_PRODUCTION = new MineProduction(15, 20, 1.1, .0002, .66, 10);
	private static final MineProduction DEUT_PRODUCTION = new MineProduction(0, 10, 1.1, .0002, .33, 20);
	private static final NavigableMap<Integer, Double> DEUT_MULTIPLIERS;

	private static final Map<AccountUpgrade, CostDescrip> COST_DESCRIPS;

	static {
		Map<AccountUpgrade, CostDescrip> costs = new EnumMap<>(AccountUpgrade.class);
		for (AccountUpgrade upgrade : AccountUpgrade.values()) {
			switch (upgrade) {
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
				costs.put(upgrade, new CostDescrip(900, 360, 180, 1.8));
				break;
			case MetalStorage:
				costs.put(upgrade, new CostDescrip(1000, 0, 0, 2));
				break;
			case CrystalStorage:
				costs.put(upgrade, new CostDescrip(1000, 500, 0, 2));
				break;
			case DeuteriumStorage:
				costs.put(upgrade, new CostDescrip(1000, 10000, 0, 2));
				break;
			case RoboticsFactory:
				costs.put(upgrade, new CostDescrip(400, 120, 200, 2));
				break;
			case Shipyard:
				costs.put(upgrade, new CostDescrip(400, 200, 100, 2));
				break;
			case ResearchLab:
				costs.put(upgrade, new CostDescrip(200, 400, 200, 2));
				break;
			case AllianceDepot:
				costs.put(upgrade, new CostDescrip(20000, 40000, 0, 2));
				break;
			case MissileSilo:
				costs.put(upgrade, new CostDescrip(20000, 20000, 1000, 2));
				break;
			case NaniteFactory:
				costs.put(upgrade, new CostDescrip(1000000, 500000, 100000, 2));
				break;
			case Terraformer:
				costs.put(upgrade, new CostDescrip(0, 50000, 100000, 1000, 2, 2));
				break;
			case SpaceDock:
				costs.put(upgrade, new CostDescrip(200, 0, 50, 50, 5, 2.5));
				break;
			case LunarBase:
				costs.put(upgrade, new CostDescrip(20000, 40000, 20000, 2));
				break;
			case SensorPhalanx:
				costs.put(upgrade, new CostDescrip(20000, 40000, 20000, 2));
				break;
			case JumpGate:
				costs.put(upgrade, new CostDescrip(2000000, 4000000, 2000000, 2));
				break;

			case Energy:
				costs.put(upgrade, new CostDescrip(0, 800, 400, 2));
				break;
			case Laser:
				costs.put(upgrade, new CostDescrip(200, 100, 0, 2));
				break;
			case Ion:
				costs.put(upgrade, new CostDescrip(1000, 300, 100, 2));
				break;
			case Hyperspace:
				costs.put(upgrade, new CostDescrip(0, 4000, 2000, 2));
				break;
			case Plasma:
				costs.put(upgrade, new CostDescrip(2000, 4000, 1000, 2));
				break;
			case Combustion:
				costs.put(upgrade, new CostDescrip(400, 600, 0, 2));
				break;
			case Impulse:
				costs.put(upgrade, new CostDescrip(2000, 4000, 600, 2));
				break;
			case Hyperdrive:
				costs.put(upgrade, new CostDescrip(10000, 20000, 6000, 2));
				break;
			case Espionage:
				costs.put(upgrade, new CostDescrip(200, 1000, 200, 2));
				break;
			case Computer:
				costs.put(upgrade, new CostDescrip(0, 400, 600, 2));
				break;
			case Astrophysics:
				costs.put(upgrade, new CostDescrip(4000, 8000, 7000, 1.75));
				break;
			case IntergalacticResearchNetwork:
				costs.put(upgrade, new CostDescrip(240000, 400000, 160000, 2));
				break;
			case Graviton:
				costs.put(upgrade, new CostDescrip(0, 0, 0, 300000, 0, 3));
				break;
			case Weapons:
				costs.put(upgrade, new CostDescrip(800, 200, 0, 2));
				break;
			case Shielding:
				costs.put(upgrade, new CostDescrip(200, 600, 0, 2));
				break;
			case Armor:
				costs.put(upgrade, new CostDescrip(1000, 0, 0, 2));
				break;

			case LightFighter:
				costs.put(upgrade, new CostDescrip(3000, 1000, 0));
				break;
			case HeavyFighter:
				costs.put(upgrade, new CostDescrip(6000, 4000, 0));
				break;
			case Cruiser:
				costs.put(upgrade, new CostDescrip(20000, 7000, 2000));
				break;
			case Battleship:
				costs.put(upgrade, new CostDescrip(45000, 15000, 0));
				break;
			case Battlecruiser:
				costs.put(upgrade, new CostDescrip(30000, 40000, 15000));
				break;
			case Bomber:
				costs.put(upgrade, new CostDescrip(50000, 25000, 15000));
				break;
			case Destroyer:
				costs.put(upgrade, new CostDescrip(60000, 50000, 15000));
				break;
			case Deathstar:
				costs.put(upgrade, new CostDescrip(5000000, 4000000, 1000000));
				break;
			case Reaper:
				costs.put(upgrade, new CostDescrip(85000, 55000, 20000));
				break;
			case Pathfinder:
				costs.put(upgrade, new CostDescrip(8000, 15000, 8000));
				break;
			case SmallCargo:
				costs.put(upgrade, new CostDescrip(2000, 2000, 0));
				break;
			case LargeCargo:
				costs.put(upgrade, new CostDescrip(6000, 6000, 0));
				break;
			case ColonyShip:
				costs.put(upgrade, new CostDescrip(10000, 20000, 10000));
				break;
			case Recycler:
				costs.put(upgrade, new CostDescrip(10000, 6000, 2000));
				break;
			case EspionageProbe:
				costs.put(upgrade, new CostDescrip(0, 1000, 0));
				break;
			case SolarSatellite:
				costs.put(upgrade, new CostDescrip(0, 2000, 500));
				break;
			case Crawler:
				costs.put(upgrade, new CostDescrip(2000, 2000, 1000));
				break;
			}
		}
		COST_DESCRIPS = Collections.unmodifiableMap(costs);

		NavigableMap<Integer, Double> deutMultipliers = new TreeMap<>();
		deutMultipliers.put(-136, 1.9);
		deutMultipliers.put(-110, 1.8);
		deutMultipliers.put(-76, 1.6);
		deutMultipliers.put(-46, 1.5);
		deutMultipliers.put(-16, 1.4);
		deutMultipliers.put(14, 1.3);
		deutMultipliers.put(44, 1.2);
		deutMultipliers.put(74, 1.1);
		deutMultipliers.put(104, 1.0);
		deutMultipliers.put(134, 0.9);
		deutMultipliers.put(164, 0.8);
		deutMultipliers.put(194, 0.7);
		deutMultipliers.put(224, 0.6);
		DEUT_MULTIPLIERS = Collections.unmodifiableNavigableMap(deutMultipliers);
	}

	/** Storage amount per storage building level, starting at level 0 */
	private final BetterSortedSet<Integer> STORAGE = new BetterTreeSet<>(false, Integer::compareTo).with(10);

	@Override
	public Production getProduction(Account account, Planet planet, ResourceType resourceType, double energyFactor) {
		EnumMap<ProductionSource, Integer> byType = new EnumMap<>(ProductionSource.class);
		int totalProduced = 0;
		int totalConsumed = 0;
		if (resourceType == ResourceType.Energy) {
			// Mines
			int typeAmount = getMineEnergy(METAL_PRODUCTION, //
				planet.getMetalMine(), planet.getMetalUtilization());
			byType.put(ProductionSource.MetalMine, typeAmount);
			totalConsumed+=typeAmount;
			typeAmount = getMineEnergy(CRYSTAL_PRODUCTION, //
				planet.getCrystalMine(), planet.getCrystalUtilization());
			byType.put(ProductionSource.CrystalMine, typeAmount);
			totalConsumed += typeAmount;
			typeAmount = getMineEnergy(DEUT_PRODUCTION, //
				planet.getDeuteriumSynthesizer(), planet.getDeuteriumUtilization());
			byType.put(ProductionSource.DeuteriumSynthesizer, typeAmount);
			totalConsumed += typeAmount;
			// Crawlers
			typeAmount = getUsableCrawlers(planet) * 50;
			byType.put(ProductionSource.Crawler, typeAmount);
			totalConsumed += typeAmount;

			// Producers
			typeAmount = (int) Math.floor(20 * planet.getSolarPlant() * Math.pow(1.1, planet.getSolarPlant()));
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

			if (account.getGameClass() == AccountClass.Collector) {
				typeAmount = (int) Math.round(totalProduced * 0.10);
				byType.put(ProductionSource.Collector, typeAmount);
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

			int typeAmount = production.base * account.getUniverse().getEconomySpeed();
			byType.put(ProductionSource.Base, typeAmount);
			totalProduced += typeAmount;

			// Mine production
			double mineP = production.multiplier * level * Math.pow(production.exponent, level) * account.getUniverse().getEconomySpeed()
				* energyFactor * (utilization / 100.0);
			if (resourceType == ResourceType.Deuterium) {
				double mult;
				Integer maxTemp = DEUT_MULTIPLIERS.ceilingKey((planet.getMinimumTemperature() + planet.getMaximumTemperature()) / 2);
				if (maxTemp != null) {
					mult = DEUT_MULTIPLIERS.get(maxTemp);
				} else {
					mult = DEUT_MULTIPLIERS.lastEntry().getValue();
				}
				mineP *= mult;
			}
			int mineProduction = (int) Math.floor(mineP);
			typeAmount = mineProduction;
			byType.put(mineType, typeAmount);
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

			// Crawler production
			int crawlers = getUsableCrawlers(planet);
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
		}
		return new Production(byType, totalProduced, totalConsumed);
	}

	protected int getMineEnergy(MineProduction production, int level, int utilization) {
		return (int) Math.floor(production.energyMultiplier * level * utilization / 100.0 * Math.pow(1.1, level));
	}

	protected int getUsableCrawlers(Planet planet) {
		int crawlers = planet.getCrawlers();
		int crawlerCap = (planet.getMetalMine() + planet.getCrystalMine() + planet.getDeuteriumSynthesizer()) * 8;
		return Math.min(crawlers, crawlerCap);
	}

	protected int getSatelliteEnergy(Account account, Planet planet) {
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
			STORAGE.add(5 * (int) Math.floor(2.5 * Math.exp(20.0 / 33 * STORAGE.size())));
		}
		return STORAGE.get(level) * 1000;
	}

	@Override
	public UpgradeCost getUpgradeCost(Account account, RockyBody planetOrMoon, AccountUpgrade upgrade, int fromLevel, int toLevel) {
		if (fromLevel == toLevel) {
			return new UpgradeCost(0, 0, 0, 0, Duration.ZERO);
		}
		CostDescrip cost = COST_DESCRIPS.get(upgrade);
		long[] resAmounts = getCost(upgrade, fromLevel, toLevel, cost, account, planetOrMoon);
		long seconds = getUpgradeTime(account, planetOrMoon, upgrade, resAmounts);
		return new UpgradeCost(resAmounts[0], resAmounts[1], resAmounts[2], (int) resAmounts[3], Duration.ofSeconds(seconds));
	}

	protected long[] getCost(AccountUpgrade upgrade, int fromLevel, int toLevel, CostDescrip cost, Account account,
		RockyBody planetOrMoon) {
		long[] resAmounts = new long[4];
		resAmounts[0] = cost.baseMetal;
		resAmounts[1] = cost.baseCrystal;
		resAmounts[2] = cost.baseDeuterium;
		resAmounts[3] = cost.baseEnergy;
		double resMult = 1;
		switch (upgrade.type) {
		case Building:
		case Research:
			resMult = Math.pow(cost.resourceExponent, Math.min(fromLevel, toLevel))
				* ((1 - Math.pow(cost.resourceExponent, Math.abs(toLevel - fromLevel))) / (1 - cost.resourceExponent));
			if (toLevel < fromLevel) {
				resMult = resMult / cost.resourceExponent * Math.max(1 - account.getResearch().getIon() * 0.04, 0);
			}
			break;
		case ShipyardItem:
			resMult = (toLevel - fromLevel);
			if (toLevel < fromLevel) {
				resMult *= 0.35; // Scrapping
			}
			break;
		}
		for (int i = 0; i < 3; i++) {
			resAmounts[i] = Math.round(resAmounts[i] * resMult);
		}
		if (cost.energyExponent > 1 && toLevel > fromLevel) {
			double mult = Math.pow(cost.energyExponent, Math.min(fromLevel, toLevel))
				* ((1 - Math.pow(cost.energyExponent, Math.abs(toLevel - fromLevel))) / (1 - cost.energyExponent));
			resAmounts[3] = Math.round(resAmounts[3] * mult);
		}
		return resAmounts;
	}

	protected long getUpgradeTime(Account account, RockyBody planetOrMoon, AccountUpgrade upgrade, long [] resAmounts){
		double hours = 0;
		switch (upgrade.type) {
		case Building:
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
		int levels = planetOrMoon.getBuildingLevel(BuildingType.ResearchLab);
		int irn = account.getResearch().getIntergalacticResearchNetwork();
		if (irn > 0) {
			SortedTreeList<Integer> labLevels = new SortedTreeList<>(false, (i1, i2) -> -Integer.compare(i1, i2));
			for (Planet planet : account.getPlanets().getValues()) {
				if (planet != planetOrMoon) {
					labLevels.add(planet.getBuildingLevel(BuildingType.ResearchLab));
				}
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
	public int getFields(Planet planet) {
		int fields = planet.getBaseFields();
		int terraformer = planet.getTerraformer();
		fields += getTerraformerFields(terraformer);
		return fields;
	}

	protected int getTerraformerFields(int terraformerLevel) {
		return (int) Math.ceil(terraformerLevel * 5.5);
	}

	@Override
	public int getFields(Moon moon) {
		return 1 + moon.getLunarBase() * 3 + moon.getFieldBonus();
	}
}
