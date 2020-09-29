package org.quark.ogame.roi;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

import org.qommons.QommonsUtils;
import org.qommons.collect.BetterList;
import org.qommons.tree.BetterTreeList;
import org.quark.ogame.OGameUtils;
import org.quark.ogame.roi.RoiAccount.RoiPlanet;
import org.quark.ogame.roi.RoiAccount.RoiRockyBody;
import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.AccountUpgradeType;
import org.quark.ogame.uni.BuildingType;
import org.quark.ogame.uni.Moon;
import org.quark.ogame.uni.OGameEconomyRuleSet.Production;
import org.quark.ogame.uni.OGameEconomyRuleSet.ProductionSource;
import org.quark.ogame.uni.OGameEconomyRuleSet.Requirement;
import org.quark.ogame.uni.OGameRuleSet;
import org.quark.ogame.uni.Planet;
import org.quark.ogame.uni.ResourceType;
import org.quark.ogame.uni.ShipyardItemType;
import org.quark.ogame.uni.TradeRatios;
import org.quark.ogame.uni.UpgradeCost;

public class RoiSequence {
	public static final int MIN_FUSION_PLANET = 5;
	public static final List<AccountUpgradeType> CORE_UPGRADES;
	public static final List<AccountUpgradeType> BUILD_HELPERS;

	static {
		BetterList<AccountUpgradeType> helpers = new BetterTreeList<>(false);
		helpers.with(//
			AccountUpgradeType.MetalMine, AccountUpgradeType.CrystalMine, AccountUpgradeType.DeuteriumSynthesizer, //
			AccountUpgradeType.Astrophysics, AccountUpgradeType.Plasma);
		CORE_UPGRADES = QommonsUtils.unmodifiableCopy(helpers);

		helpers.clear();
		helpers.with(//
			AccountUpgradeType.RoboticsFactory, AccountUpgradeType.NaniteFactory, //
			AccountUpgradeType.ResearchLab, AccountUpgradeType.IntergalacticResearchNetwork);
		BUILD_HELPERS = QommonsUtils.unmodifiableCopy(helpers);
	}

	private final OGameRuleSet theRules;
	private final Account theAccount;
	private int theNewPlanetSlot;
	private int theNewPlanetFields;
	private int theNewPlanetTemp;

	private ProductionSource theEnergyType;

	private Moon theTemplateMoon;
	private int theTargetPlanet;
	private long theStorageContainment;
	private double theDefense;
	private boolean isWithHoldingCargoes;
	private boolean isWithHarvestingCargoes;

	public RoiSequence(OGameRuleSet rules, Account account) {
		theRules = rules;
		theAccount = account;
		theNewPlanetSlot = 8;
		theNewPlanetFields = 200;
		theNewPlanetTemp = 30;
		theTargetPlanet = Math.max(15, account.getPlanets().getValues().size() + 2);
	}

	public OGameRuleSet getRules() {
		return theRules;
	}

	public Account getAccount() {
		return theAccount;
	}

	public int getNewPlanetSlot() {
		return theNewPlanetSlot;
	}

	public RoiSequence setNewPlanetSlot(int newPlanetSlot) {
		theNewPlanetSlot = newPlanetSlot;
		return this;
	}

	public int getNewPlanetFields() {
		return theNewPlanetFields;
	}

	public RoiSequence setNewPlanetFields(int newPlanetFields) {
		theNewPlanetFields = newPlanetFields;
		return this;
	}

	public int getNewPlanetTemp() {
		return theNewPlanetTemp;
	}

	public RoiSequence setNewPlanetTemp(int newPlanetTemp) {
		theNewPlanetTemp = newPlanetTemp;
		return this;
	}

	public ProductionSource getEnergyType() {
		return theEnergyType;
	}

	public RoiSequence setEnergyType(ProductionSource energyType) {
		theEnergyType = energyType;
		return this;
	}

	public Moon getTemplateMoon() {
		return theTemplateMoon;
	}

	public RoiSequence setTemplateMoon(Moon templateMoon) {
		theTemplateMoon = templateMoon;
		return this;
	}

	public int getTargetPlanet() {
		return theTargetPlanet;
	}

	public RoiSequence setTargetPlanet(int targetPlanet) {
		theTargetPlanet = targetPlanet;
		return this;
	}

	public long getStorageContainment() {
		return theStorageContainment;
	}

	public RoiSequence setStorageContainment(long storageContainment) {
		theStorageContainment = storageContainment;
		return this;
	}

	public double getDefense() {
		return theDefense;
	}

	public RoiSequence setDefense(double defense) {
		theDefense = defense;
		return this;
	}

	public boolean isWithHoldingCargoes() {
		return isWithHoldingCargoes;
	}

	public RoiSequence setWithHoldingCargoes(boolean withHoldingCargoes) {
		isWithHoldingCargoes = withHoldingCargoes;
		return this;
	}

	public boolean isWithHarvestingCargoes() {
		return isWithHarvestingCargoes;
	}

	public RoiSequence setWithHarvestingCargoes(boolean withHarvestingCargoes) {
		isWithHarvestingCargoes = withHarvestingCargoes;
		return this;
	}

	public void produceSequence(BetterList<RoiSequenceElement> sequence) {
		/*
Also, if selected, account for cargoes required to accommodate all holdings plus daily production
Insert energy upgrades and usage adjustments as needed to supply energy for optimal production, regardless of ROI
Add storage in keeping with the storage preference (if selected)
Add defense to defend a certain multiple of daily production (if selected)
Insert helpers similar to shakedown algorithm below, finding the optimal spot based on its overall effect on the lifetime metric
Add terraformer levels when more room is required
	Cost of terraformer includes satellites required for energy, minus scrapped resources

Apply the shakedown algorithm
	Determine lifetime account metric
		Go through the sequence over time as if playing the game.  Accumulate resource and purchse the upgrades in sequence.
			Take into account: build time, regular fleet saving costs, ancillary spending habits, etc.
		Save the metric value (account value + holdings)
	Go through the sequence from the beginning.  At each spot, search for its optimal spot to the left or right of its existing spot
		With each move, re-calculate the lifetime account metric and compare with the previous
		Whenever an upgrade is moved, start over from the beginning
		 */
		
		RoiAccount copy=new RoiAccount(this, theAccount);
		{
			int maxPlanets = getMaxPlanets(copy);
			//First, if the new account has more planet slots than planets, catch it up
			while (copy.RoiPlanets().size() < maxPlanets) {
				copy.getPlanets().create();
			}
			copy.flush();
		}
		
		/*The basic way this works is:
		 * * Lay out a scaffolding of core upgrades
		 * * Fill it in with supporting upgrades
		 * * Poke it around to see if slight reordering is warranted 
		 */

		// First, lay out "core" production upgrades by ROI, assuming full energy supply and max crawlers
		TradeRatios tr=theAccount.getUniverse().getTradeRatios();
		while (copy.RoiPlanets().size() < theTargetPlanet) {
			AccountUpgradeType bestUpgrade=null;
			RoiPlanet bestPlanet = null;
			long bestRoi=0;
			long preProduction = copy.getProduction();
			for(AccountUpgradeType upgrade : CORE_UPGRADES){
				UpgradeCost cost;
				if (upgrade.research != null) {
					cost = coreUpgrade(copy, null, upgrade);
					long postProduction = copy.getProduction();
					long roi = Math.round(cost.getMetalValue(tr) / (postProduction - preProduction));
					if (bestUpgrade == null || roi < bestRoi) {
						bestUpgrade = upgrade;
						bestPlanet = null;
						bestRoi = roi;
					}
					copy.clear();
				} else {
					//Find the best planet to upgrade
					cost = null;
					for (RoiPlanet planet : copy.RoiPlanets()) {
						UpgradeCost planetCost = coreUpgrade(copy, planet, upgrade);
						long postProduction = copy.getProduction();
						long planetRoi = Math.round(planetCost.getMetalValue(tr) / (postProduction - preProduction));
						if (bestUpgrade == null || planetRoi < bestRoi) {
							bestUpgrade=upgrade;
							bestPlanet=planet;
							bestRoi = planetRoi;
							cost = planetCost;
						}
						// Now reset the account
						copy.clear();
					}
				}
			}

			int currentLevel = bestUpgrade.getLevel(copy, bestPlanet);
			int targetLevel;
			if (bestUpgrade == AccountUpgradeType.Astrophysics && currentLevel % 2 == 1) {
				targetLevel = currentLevel + 2;
			} else {
				targetLevel = currentLevel + 1;
			}
			int planetIdx = bestPlanet == null ? -1 : copy.RoiPlanets().indexOf(bestPlanet);
			// AccountUpgradeType
			coreUpgrade(copy, bestPlanet, bestUpgrade);
			copy.flush();
			sequence.add(new RoiSequenceElement(bestUpgrade, planetIdx, bestRoi).setTargetLevel(targetLevel));
		}

		copy.reset();
		// Now, insert actual energy production needed to supply full energy, plus crawlers
		ListIterator<RoiSequenceElement> sequenceIter = sequence.listIterator();
		while (sequenceIter.hasNext()) {
			RoiSequenceElement el = sequenceIter.next();
			if (el.planetIndex < 0) {
				coreUpgrade(copy, null, el.upgrade);
				copy.flush();
				continue;
			}
			RoiPlanet planet = copy.RoiPlanets().get(el.planetIndex);
			coreUpgrade(copy, planet, el.upgrade);
			copy.flush();
			if (el.upgrade.building == null || el.upgrade.building.isMine() == null) {
				continue;
			}
			Production energy = theRules.economy().getProduction(copy, planet, ResourceType.Energy, 0);
			sequenceIter.add(new RoiSequenceElement(AccountUpgradeType.Crawler, el.planetIndex, 0));
			upgrade(copy, planet, AccountUpgradeType.Crawler);
			copy.flush();

			if (theEnergyType == ProductionSource.Fusion && copy.RoiPlanets().size() >= MIN_FUSION_PLANET) {
				while (energy.totalNet >= 0) {
					if (planet.getFusionReactor() == 0) {
						planet.upgrade(AccountUpgradeType.SolarSatellite, -planet.getSolarSatellites());
						upgrade(copy, planet, AccountUpgradeType.FusionReactor, planet.getFusionReactor() + 1);
						copy.flush();
						continue;
					}
					// Can't really control well for deut usage here, so just go by best energy value
					// We'll account for absolutely everything in the final step
					int preDeutUsage = theRules.economy().getProduction(copy, planet, ResourceType.Deuterium,
						energy.totalProduction / energy.totalConsumption).byType.getOrDefault(ProductionSource.Fusion, 0);
					UpgradeCost fusionCost = upgrade(copy, planet, AccountUpgradeType.FusionReactor);
					copy.getProduction(); // Update the energy and production value
					Production fusionEnergy = theRules.economy().getProduction(copy, planet, ResourceType.Energy, 0);
					int fusionDeutUsage = theRules.economy().getProduction(copy, planet, ResourceType.Deuterium,
						fusionEnergy.totalProduction / fusionEnergy.totalConsumption).byType.getOrDefault(ProductionSource.Fusion, 0);
					fusionCost = fusionCost
						.plus(UpgradeCost.of('b', 0, 0, Math.round((fusionDeutUsage - preDeutUsage) * el.roi), 0, Duration.ZERO, 1, 0, 0));
					copy.clear();
					UpgradeCost energyCost = upgrade(copy, planet, AccountUpgradeType.Energy, copy.getResearch().getEnergy() + 1);
					int fusionEnergyBump = fusionEnergy.byType.get(ProductionSource.Fusion) - energy.byType.get(ProductionSource.Fusion);
					int researchEnergyBump = theRules.economy().getProduction(copy, planet, ResourceType.Energy, 0).byType
						.get(ProductionSource.Fusion) - energy.byType.get(ProductionSource.Fusion);
					researchEnergyBump *= copy.RoiPlanets().size();
					double fusionEfficiency = fusionEnergyBump / fusionCost.getMetalValue(tr);
					double researchEfficiency = researchEnergyBump / energyCost.getMetalValue(tr);
					if (researchEfficiency > fusionEfficiency) {
						sequenceIter.add(new RoiSequenceElement(AccountUpgradeType.Energy, el.planetIndex, 0));
						copy.flush();
					} else {
						sequenceIter.add(new RoiSequenceElement(AccountUpgradeType.FusionReactor, el.planetIndex, 0));
						copy.clear();
						upgrade(copy, planet, AccountUpgradeType.FusionReactor);
						copy.flush();
					}
				}
			} else if (theEnergyType == ProductionSource.Solar) {
				while (energy.totalNet >= 0) {
					sequenceIter.add(new RoiSequenceElement(AccountUpgradeType.SolarPlant, el.planetIndex, 0));
					upgrade(copy, planet, AccountUpgradeType.SolarPlant, planet.getSolarPlant() + 1);
					copy.flush();
					energy = theRules.economy().getProduction(copy, planet, ResourceType.Energy, 0);
				}
			} else {
				sequenceIter.add(new RoiSequenceElement(AccountUpgradeType.SolarSatellite, el.planetIndex, 0));
				upgrade(copy, planet, AccountUpgradeType.SolarSatellite);
				copy.flush();
			}
		}

		copy.reset();
		// Now, add "helper" builds that make buildings and research complete faster
		long lifetimeMetric = getLifetimeMetric(copy, sequence);
		sequenceIter = sequence.listIterator();
		while (sequenceIter.hasNext()) {
			RoiSequenceElement el = sequenceIter.next();
			List<AccountUpgradeType> helperTypes = getHelperTypes(el.upgrade);
			AccountUpgradeType bestHelper;
			do {
				bestHelper = null;
				long bestLifetime = -1;
				boolean first = true;
				for (AccountUpgradeType helper : helperTypes) {
					if (first) {
						sequenceIter.add(new RoiSequenceElement(helper, el.planetIndex, 0));
						sequenceIter.next();
						first = false;
					} else {
						sequenceIter.set(new RoiSequenceElement(helper, el.planetIndex, 0));
					}
					long helpedLifetime = getLifetimeMetric(copy, sequence);
					if (lifetimeMetric <= helpedLifetime) {
						continue;
					} else if (helpedLifetime < bestLifetime) {
						bestHelper = helper;
						bestLifetime = helpedLifetime;
					}
				}
				if (bestLifetime >= 0) {
					lifetimeMetric = bestLifetime;
				} else if (!first) {
					sequenceIter.remove();
				}
			} while (bestHelper != null);
		}

		/* Now, go through the sequence adjusting individual items to optimize the final lifetime metric
		 * Take into account:
		 * * Storage building costs
		 * * Defense building costs
		 * * Cargo building costs
		 * * Regular fleet saving costs
		 * * Ancillary spending habits
		 */
		boolean moved;
		do {
			moved = false;
			for (int i = 0; i < sequence.size() - 1; i++) {
				RoiSequenceElement el = sequence.remove(i);
				long newLifetime = getLifetimeMetric(copy, sequence);
				int bestIndex = -1;
				for (int j = i + 1; j < sequence.size(); j++) {
					sequence.add(j, el);
					long tempLifetime = getLifetimeMetric(copy, sequence);
					if (tempLifetime < newLifetime) {
						bestIndex = j;
						newLifetime = tempLifetime;
					}
					sequence.remove(j);
				}
				if (newLifetime < lifetimeMetric) {
					moved = true;
					lifetimeMetric = newLifetime;
					sequence.add(bestIndex, el);
				} else {
					sequence.add(i, el);
				}
			}
		} while (moved);
		// TODO
		// TODO Insert the actual storage buildings into the sequence
		// Last, insert defense and cargo building in large chunks
	}

	private static List<AccountUpgradeType> getHelperTypes(AccountUpgradeType upgrade) {
		switch (upgrade.type) {
		case Building:
			return Arrays.asList(AccountUpgradeType.RoboticsFactory, AccountUpgradeType.NaniteFactory);
		case Research:
			return Arrays.asList(AccountUpgradeType.ResearchLab, AccountUpgradeType.IntergalacticResearchNetwork);
		case ShipyardItem:
			return Arrays.asList(AccountUpgradeType.Shipyard, AccountUpgradeType.NaniteFactory);
		}
		throw new IllegalStateException(upgrade.name());
	}

	private UpgradeCost coreUpgrade(RoiAccount account, RoiPlanet planet, AccountUpgradeType upgrade) {
		switch (upgrade) {
		case MetalMine:
		case CrystalMine:
		case DeuteriumSynthesizer:
			// Find the best planet to upgrade
			int currentLevel = planet.getBuildingLevel(upgrade.building);
			UpgradeCost cost = upgrade(account, planet, upgrade, //
				currentLevel + 1);
			int currentCrawlers = planet.getStationaryStructures().getCrawlers();
			int maxCrawlers = theRules.economy().getMaxCrawlers(account, planet);
			if (maxCrawlers > currentCrawlers) {
				cost = cost.plus(upgrade(account, planet, AccountUpgradeType.Crawler, maxCrawlers));
				planet.setCrawlers(maxCrawlers);
			}
			planet.setSolarSatellites(OGameUtils.getRequiredSatellites(account, planet, theRules.economy()));
			return cost;
		case Astrophysics:
			currentLevel = account.getResearch().getAstrophysics();
			int targetLevel = currentLevel + ((currentLevel % 2 == 0) ? 1 : 2);
			cost = upgrade(account, null, upgrade, targetLevel);
			return cost;
		case Plasma:
			currentLevel = account.getResearch().getPlasma();
			cost = upgrade(account, null, upgrade, currentLevel + 1);
			return cost;
		default:
			throw new IllegalStateException("Unrecognized core upgrade: " + upgrade);
		}
	}

	private UpgradeCost upgrade(RoiAccount account, RoiRockyBody body, AccountUpgradeType upgrade) {
		return upgrade(account, body, upgrade, getTargetLevel(account, body, upgrade));
	}

	private int getTargetLevel(RoiAccount account, RoiRockyBody body, AccountUpgradeType upgrade) {
		int currentLevel = upgrade.getLevel(account, body);
		int targetLevel;
		switch (upgrade.type) {
		case Research:
			switch (upgrade.research) {
			case Astrophysics:
				targetLevel = currentLevel + ((currentLevel % 2 == 0) ? 1 : 2);
				break;
			default:
				targetLevel = currentLevel + 1;
				break;
			}
			break;
		case Building:
			targetLevel = currentLevel + 1;
			break;
		case ShipyardItem:
			switch (upgrade.shipyardItem) {
			case Crawler:
				targetLevel = theRules.economy().getMaxCrawlers(account, (Planet) body);
				break;
			case SolarSatellite:
				targetLevel = OGameUtils.getRequiredSatellites(account, (Planet) body, theRules.economy());
				break;
			default:
				return currentLevel + 1; // ?
			}
			break;
		default:
			throw new IllegalStateException(upgrade.name());
		}
		return targetLevel;
	}

	private UpgradeCost upgrade(RoiAccount account, RoiRockyBody body, AccountUpgradeType upgrade, int targetLevel) {
		int currentLevel = upgrade.getLevel(account, body);
		UpgradeCost cost = UpgradeCost.ZERO;
		if (currentLevel == 0) {
			// For all upgrades, calculate ROI based on the cost of not only the building itself,
			// but of all required buildings and researches needed to be able to build it
			for (Requirement req : theRules.economy().getRequirements(upgrade)) {
				int currentReqLevel = req.type.getLevel(account, body);
				if (currentReqLevel < req.level) {
					cost = cost.plus(upgrade(account, body, req.type, req.level));
				}
			}
		}
		cost = cost.plus(theRules.economy().getUpgradeCost(account, body, upgrade, currentLevel, targetLevel));
		account.upgrade(upgrade, body, targetLevel - currentLevel);
		if (upgrade == AccountUpgradeType.Astrophysics && account.RoiPlanets().size() < getMaxPlanets(account)) {
			RoiPlanet newPlanet = (RoiPlanet) account.getPlanets().newValue();
			cost = cost.plus(upgradeToLevel(account, newPlanet));
		}
		return cost;
	}

	private int getMaxPlanets(Account account) {
		int astro = account.getResearch().getAstrophysics();
		return (astro + 1) / 2 + 1;
	}

	private UpgradeCost upgradeToLevel(RoiAccount account, RoiPlanet planet) {
		// TODO Build up the fresh new planet to the minimum level of its siblings
		// TODO Don't forget the template moon
		UpgradeCost cost = UpgradeCost.ZERO;
		for (BuildingType building : BuildingType.values()) {
			int min = -1;
			for (RoiPlanet p : account.RoiPlanets()) {
				if (p == planet) {
					continue;
				}
				int level = p.getBuildingLevel(building);
				if (min < 0 || level < min) {
					min = level;
				}
			}
			cost = cost.plus(theRules.economy().getUpgradeCost(account, planet, building.getUpgrade(), 0, min));
			planet.upgrade(building.getUpgrade(), min);
		}
		for (ShipyardItemType ship : ShipyardItemType.values()) {
			int min = -1;
			for (RoiPlanet p : account.RoiPlanets()) {
				if (p == planet) {
					continue;
				}
				int level = p.getStationedShips(ship);
				if (min < 0 || level < min) {
					min = level;
				}
			}
			cost = cost.plus(theRules.economy().getUpgradeCost(account, planet, ship.getUpgrade(), 0, min));
			planet.upgrade(ship.getUpgrade(), min);
		}
		return cost;
	}

	long getLifetimeMetric(RoiAccount account, List<RoiSequenceElement> sequence) {
		for (RoiSequenceElement el : sequence) {
			if (el.planetIndex >= 0 && el.planetIndex >= account.RoiPlanets().size()) {
				// Astro must be going. Gotta wait for it to finish.
				long upgradeTime = getUpgradeCompletion(account, AccountUpgradeType.Astrophysics, null);
				if (upgradeTime <= 0) {
					throw new IllegalStateException();
				}
				account.advance(upgradeTime);
			}
			if (account.RoiPlanets().size() < getMaxPlanets(account)) {
				RoiPlanet newPlanet = (RoiPlanet) account.getPlanets().newValue();
				UpgradeCost newPlanetCost = upgradeToLevel(account, newPlanet);
				account.spend(Math.round(newPlanetCost.getMetalValue(account.getUniverse().getTradeRatios())));
			}
			el.setTime(account.getTime());
			RoiPlanet planet = el.planetIndex < 0 ? null : account.RoiPlanets().get(el.planetIndex);
			int targetLevel = getTargetLevel(account, planet, el.upgrade);
			el.setTargetLevel(targetLevel);
			upgrade(account, el.upgrade, planet, targetLevel);
		}
		return account.getTime();
	}

	private void upgrade(RoiAccount account, AccountUpgradeType upgrade, RoiPlanet planet, int targetLevel) {
		// Pre-requisites first
		int currentLevel = upgrade.getLevel(account, planet);
		if (targetLevel < currentLevel && upgrade.building == null) {
			return;
		}
		if (currentLevel == 0) {
			for (Requirement req : theRules.economy().getRequirements(upgrade)) {
				int currentReqLevel = req.type.getLevel(account, planet);
				if (currentReqLevel < req.level) {
					upgrade(account, req.type, planet, req.level - currentReqLevel);
				}
			}
		}

		// Wait for any current upgrade to finish
		long upgradeTime = getUpgradeCompletion(account, upgrade, planet);
		if (upgradeTime > 0) {
			account.advance(upgradeTime);
		}
		UpgradeCost cost = theRules.economy().getUpgradeCost(account, planet, upgrade, currentLevel, targetLevel);
		long costAmount = Math.round(cost.getMetalValue(theAccount.getUniverse().getTradeRatios()));
		account.spend(costAmount);
		account.start(upgrade, planet, targetLevel - currentLevel, cost.getUpgradeTime().getSeconds());
	}

	private static long getUpgradeCompletion(RoiAccount account, AccountUpgradeType type, RoiPlanet planet) {
		if (planet == null) {
			return account.getResearch().getUpgradeCompletion();
		} else if (type.building != null) {
			return planet.getUpgradeCompletion();
		} else {
			return planet.getStationaryStructures().getUpgradeCompletion();
		}
	}
}
