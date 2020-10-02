package org.quark.ogame.roi;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.qommons.IntList;
import org.qommons.collect.BetterList;
import org.qommons.collect.CircularArrayList;
import org.qommons.collect.StampedLockingStrategy;
import org.qommons.threading.ElasticExecutor;
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
import org.quark.ogame.uni.ResearchType;
import org.quark.ogame.uni.ResourceType;
import org.quark.ogame.uni.ShipyardItemType;
import org.quark.ogame.uni.TradeRatios;
import org.quark.ogame.uni.UpgradeCost;

public class RoiSequenceGenerator {
	public static final int MIN_FUSION_PLANET = 5;
	public static final Set<AccountUpgradeType> CORE_UPGRADES;
	private static final boolean SUPER_OPTIMIZATION = false;

	static {
		BetterList<AccountUpgradeType> upgrades = new BetterTreeList<>(false);
		upgrades.with(//
			AccountUpgradeType.MetalMine, AccountUpgradeType.CrystalMine, AccountUpgradeType.DeuteriumSynthesizer, //
			AccountUpgradeType.Astrophysics, AccountUpgradeType.Plasma);
		CORE_UPGRADES = Collections.unmodifiableSet(new HashSet<>(upgrades));
	}

	private final OGameRuleSet theRules;
	private final Account theAccount;
	private final SettableValue<Integer> theNewPlanetSlot;
	private final SettableValue<Integer> theNewPlanetFields;
	private final SettableValue<Integer> theNewPlanetTemp;

	private final SettableValue<ProductionSource> theEnergyType;

	private final SettableValue<Moon> theTemplateMoon;
	private final SettableValue<Integer> theTargetPlanet;
	private final SettableValue<Long> theStorageContainment;
	private final SettableValue<Double> theDefense;
	private final SettableValue<Boolean> isWithHoldingCargoes;
	private final SettableValue<Boolean> isWithHarvestingCargoes;

	private final SettableValue<String> theStatus;
	private final SettableValue<Integer> theProgress;
	private final SettableValue<Duration> theLifetimeMetric;

	private final SettableValue<Boolean> isActive;

	public RoiSequenceGenerator(OGameRuleSet rules, Account account) {
		theRules = rules;
		theAccount = account;
		StampedLockingStrategy locker = new StampedLockingStrategy(this);
		isActive = SettableValue.build(boolean.class).withValue(false).withLock(locker).build();
		ObservableValue<String> disabled = isActive.map(active -> active ? "Sequence is already being calculated" : null);

		theNewPlanetSlot = SettableValue.build(int.class).withValue(8).withLock(locker).build().disableWith(disabled);
		theNewPlanetFields = SettableValue.build(int.class).withValue(200).withLock(locker).build().disableWith(disabled);
		theNewPlanetTemp = SettableValue.build(int.class).withValue(30).withLock(locker).build().disableWith(disabled);
		theTargetPlanet = SettableValue.build(int.class)//
			.withValue(Math.max(15, account.getPlanets().getValues().size() + 2))//
			.withLock(locker).build().disableWith(disabled);
		theEnergyType = SettableValue.build(ProductionSource.class).withValue(ProductionSource.Satellite).withLock(locker).build()
			.disableWith(disabled);
		theTemplateMoon = SettableValue.build(Moon.class).withLock(locker).build().disableWith(disabled);
		theStorageContainment = SettableValue.build(long.class).withValue(0L).withLock(locker).build().disableWith(disabled);
		theDefense = SettableValue.build(double.class).withValue(0.0).withLock(locker).build().disableWith(disabled);
		isWithHoldingCargoes = SettableValue.build(boolean.class).withValue(false).withLock(locker).build().disableWith(disabled);
		isWithHarvestingCargoes = SettableValue.build(boolean.class).withValue(false).withLock(locker).build().disableWith(disabled);

		theStatus = SettableValue.build(String.class).withLock(locker).build();
		theProgress = SettableValue.build(Integer.class).withLock(locker).build();
		theLifetimeMetric = SettableValue.build(Duration.class).withLock(locker).build();
	}

	public OGameRuleSet getRules() {
		return theRules;
	}

	public Account getAccount() {
		return theAccount;
	}

	public SettableValue<Integer> getNewPlanetSlot() {
		return theNewPlanetSlot;
	}

	public SettableValue<Integer> getNewPlanetFields() {
		return theNewPlanetFields;
	}

	public SettableValue<Integer> getNewPlanetTemp() {
		return theNewPlanetTemp;
	}

	public SettableValue<ProductionSource> getEnergyType() {
		return theEnergyType;
	}

	public SettableValue<Moon> getTemplateMoon() {
		return theTemplateMoon;
	}

	public SettableValue<Integer> getTargetPlanet() {
		return theTargetPlanet;
	}

	public SettableValue<Long> getStorageContainment() {
		return theStorageContainment;
	}

	public SettableValue<Double> getDefense() {
		return theDefense;
	}

	public SettableValue<Boolean> isWithHoldingCargoes() {
		return isWithHoldingCargoes;
	}

	public SettableValue<Boolean> isWithHarvestingCargoes() {
		return isWithHarvestingCargoes;
	}

	public ObservableValue<String> isActive() {
		return isActive.map(active -> active ? "Sequence is already being calculated" : null);
	}

	public ObservableValue<String> getStatus() {
		return theStatus;
	}

	public ObservableValue<Integer> getProgress() {
		return theProgress;
	}

	public ObservableValue<Duration> getLifetimeMetric() {
		return theLifetimeMetric;
	}

	public void produceSequence(BetterList<RoiSequenceElement> sequence) {
		isActive.set(true, null);
		try {
			theStatus.set("Initializing Core Sequence", null);
			theProgress.set(0, null);
			theLifetimeMetric.set(null, null);
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

			RoiAccount copy = new RoiAccount(this, theAccount);
			{
				int maxPlanets = theRules.economy().getMaxPlanets(copy);
				// First, if the new account has more planet slots than planets, catch it up
				while (copy.roiPlanets().size() < maxPlanets) {
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
			TradeRatios tr = theAccount.getUniverse().getTradeRatios();
			while (copy.roiPlanets().size() < theTargetPlanet.get()) {
				AccountUpgradeType bestUpgrade = null;
				RoiPlanet bestPlanet = null;
				long bestRoi = 0;
				long preProduction = copy.getProduction();
				for (AccountUpgradeType upgrade : CORE_UPGRADES) {
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
						// Find the best planet to upgrade
						cost = null;
						for (RoiPlanet planet : copy.roiPlanets()) {
							UpgradeCost planetCost = coreUpgrade(copy, planet, upgrade);
							long postProduction = copy.getProduction();
							long planetRoi = Math.round(planetCost.getMetalValue(tr) / (postProduction - preProduction));
							if (bestUpgrade == null || planetRoi < bestRoi) {
								bestUpgrade = upgrade;
								bestPlanet = planet;
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
				int planetIdx = bestPlanet == null ? -1 : copy.roiPlanets().indexOf(bestPlanet);
				// AccountUpgradeType
				coreUpgrade(copy, bestPlanet, bestUpgrade);
				copy.flush();
				sequence.add(new RoiSequenceElement(bestUpgrade, planetIdx, bestRoi).setTargetLevel(targetLevel));
			}

			copy.reset();
			RoiSequenceElement[] seqArray = sequence.toArray(new RoiSequenceElement[sequence.size()]);
			long lifetimeMetric = addHelperUpgrades(copy, sequence, true);
			theLifetimeMetric.set(Duration.ofSeconds(lifetimeMetric), null);
			if (!SUPER_OPTIMIZATION) {
				return;
			}

			// /* Now, genetically modify the core sequence, adjusting individual items to optimize the final lifetime metric
			// * Take into account:
			// * * Storage building costs
			// * * Defense building costs
			// * * Cargo building costs
			// * * Regular fleet saving costs
			// * * Ancillary spending habits
			// */
			Mutation[] mutations = new Mutation[1000];
			Mutation original = new Mutation(seqArray, sequence, lifetimeMetric);
			mutations[0] = original;
			int mutationCount = 100;
			for (int i = 1; i < mutationCount; i++) {
				mutations[i] = new Mutation(seqArray, 15);
			}
			ElasticExecutor<Mutation> mutationExecutor = new ElasticExecutor<>("Mutation Optimizer",
				() -> new ElasticExecutor.TaskExecutor<Mutation>() {
					private final RoiAccount account = new RoiAccount(RoiSequenceGenerator.this, theAccount);

					@Override
					public void execute(Mutation task) {
						if (task.lifetimeMetric >= 0) {
							return;
						}
						task.lifetimeMetric = addHelperUpgrades(account, task.sequence, false);
						account.reset();
					}
				});
			long bestMutatedLifetime = lifetimeMetric;
			for (int round = 0; round < 10; round++) {
				theStatus.set("Optimizing Upgrade Order (Round " + (round + 1) + " of 10)", null);
				for (int i = 0; i < mutationCount; i++) {
					mutationExecutor.execute(mutations[i]);
				}
				mutationExecutor.waitWhileActive(0);
				Arrays.sort(mutations, 0, mutationCount, Mutation::compareTo);
				if (mutationCount > 100) {
					mutationCount = 100;
				}
				long bestRoundMetric = -1;
				for (int i = 0, j = mutationCount; i < mutationCount && j < mutations.length; i++) {
					if (bestRoundMetric < 0 || mutations[i].lifetimeMetric < bestRoundMetric) {
						bestRoundMetric = mutations[i].lifetimeMetric;
					}
					int reproduction;
					if (mutations[i].lifetimeMetric < bestMutatedLifetime) {
						reproduction = 15;
					} else if (i < 10) {
						reproduction = 8;
					} else if (i < 20) {
						reproduction = 6;
					} else if (i < 30) {
						reproduction = 4;
					} else if (i < 40) {
						reproduction = 3;
					} else {
						reproduction = 2;
					}
					for (int k = 0; k < reproduction && j < mutations.length; k++, j++) {
						mutations[j] = mutations[i].copy(10 - round);
					}
					mutationCount = j;
				}
			}
			theStatus.set("ROI Sequence Complete", null);
			theProgress.set(sequence.size(), null);
			theLifetimeMetric.set(Duration.ofSeconds(lifetimeMetric), null);
		} finally {
			isActive.set(false, null);
		}
	}

	public static List<RoiCompoundSequenceElement> condense(List<RoiSequenceElement> sequence) {
		List<RoiSequenceElement> seqCopy = new ArrayList<>(sequence.size());
		seqCopy.addAll(sequence);
		List<RoiCompoundSequenceElement> condensed = new ArrayList<>(sequence.size());
		AccountUpgradeType upgrade = null;
		int level = 0;
		int low = 0;
		int lastUpgrade = -1;
		for (int i = 0; i < seqCopy.size(); i++) {
			RoiSequenceElement el = seqCopy.get(i);
			if (!CORE_UPGRADES.contains(el.upgrade)) {
				continue;
			}
			if (upgrade == null) {
				upgrade = el.upgrade;
				level = el.getTargetLevel();
				lastUpgrade = i;
			} else if (el.upgrade != upgrade || el.getTargetLevel() != level) {
				condense(seqCopy, low, lastUpgrade + 1, condensed);
				low = lastUpgrade = i;
				upgrade = el.upgrade;
				level = el.getTargetLevel();
			} else {
				lastUpgrade = i;
			}
		}
		return condensed;
	}

	private static void condense(List<RoiSequenceElement> seqCopy, int from, int to, List<RoiCompoundSequenceElement> condensed) {
		IntList planets = new IntList();
		BitSet used = new BitSet(to - from);
		for (int i = from; i < to; i = from + used.nextClearBit(i - from + 1)) {
			RoiSequenceElement el = seqCopy.get(i);
			AccountUpgradeType upgrade = el.upgrade;
			if (upgrade == AccountUpgradeType.SolarSatellite) {
				used.set(i);
				continue;
			} else if (CORE_UPGRADES.contains(upgrade)) {
				continue; // Do the core upgrade last
			}
			used.set(i);
			int level = el.getTargetLevel();
			if (el.planetIndex < 0) {
				condensed.add(new RoiCompoundSequenceElement(upgrade, level, null, el.getTime()));
				continue;
			}
			planets.clear();
			planets.add(el.planetIndex);
			for (int j = from + used.nextClearBit(i - from + 1); j < to; j = from + used.nextClearBit(j - from + 1)) {
				RoiSequenceElement elj = seqCopy.get(j);
				if (elj.upgrade == upgrade && elj.getTargetLevel() == level) {
					used.set(j);
					planets.add(elj.planetIndex);
				}
			}
			condensed.add(new RoiCompoundSequenceElement(upgrade, level, planets.toArray(), el.getTime()));
		}
		// Now do the core upgrade
		planets.clear();
		int i = from + used.nextClearBit(0);
		RoiSequenceElement el = seqCopy.get(i);
		if (el.planetIndex >= 0) {
			planets.add(el.planetIndex);
		}
		for (i = from + used.nextClearBit(i - from + 1); i < to; i = from + used.nextClearBit(i - from + 1)) {
			el = seqCopy.get(i);
			if (el.planetIndex >= 0) {
				planets.add(el.planetIndex);
			}
		}
		condensed.add(
			new RoiCompoundSequenceElement(el.upgrade, el.getTargetLevel(), planets.isEmpty() ? null : planets.toArray(), el.getTime()));
	}

	static class Mutation implements Comparable<Mutation> {
		final RoiSequenceElement[] coreSequence;
		final CircularArrayList<RoiSequenceElement> sequence;
		long lifetimeMetric;

		Mutation(RoiSequenceElement[] seq, int mutationDegree) {
			coreSequence = seq.clone();
			sequence = CircularArrayList.build().withInitCapacity(seq.length * 4).build();
			mutate(mutationDegree);
			lifetimeMetric = -1;
		}

		Mutation(RoiSequenceElement[] coreSeq, List<RoiSequenceElement> sequence, long lifetimeMetric) {
			coreSequence = coreSeq.clone();
			this.sequence = CircularArrayList.build().withInitCapacity(sequence.size()).<RoiSequenceElement> build().withAll(sequence);
			this.lifetimeMetric = lifetimeMetric;
		}

		@Override
		public int compareTo(Mutation other) {
			return Long.compare(lifetimeMetric, other.lifetimeMetric);
		}

		void mutate(int degree) {
			sequence.clear();
			Random r = new Random();
			int randomBits = r.nextInt();
			int bitsLeft = 32;
			int mask = 0x80000000;
			for (int i = 0; i < coreSequence.length - degree; i++) {
				if (bitsLeft == 0) {
					randomBits = r.nextInt();
					bitsLeft = 32;
				}
				if ((randomBits & mask) != 0) {
					int index = i + degree;
					RoiSequenceElement el = coreSequence[i];
					coreSequence[i] = coreSequence[index];
					coreSequence[index] = el;
				}
			}
			sequence.with(coreSequence);
		}

		Mutation copy(int mutationDegree) {
			return new Mutation(coreSequence, mutationDegree);
		}
	}

	private static final Map<AccountUpgradeType, List<AccountUpgradeType>> HELPERS;
	static {
		Map<AccountUpgradeType, List<AccountUpgradeType>> helpers = new HashMap<>();
		for (AccountUpgradeType upgrade : AccountUpgradeType.values()) {
			switch (upgrade.type) {
			case Building:
				if (upgrade.building.isMine() != null) {
					helpers.put(upgrade,
						Arrays.asList(AccountUpgradeType.RoboticsFactory, AccountUpgradeType.NaniteFactory, AccountUpgradeType.Crawler));
				} else {
					helpers.put(upgrade, Arrays.asList(AccountUpgradeType.RoboticsFactory, AccountUpgradeType.NaniteFactory));
				}
				break;
			case Research:
				helpers.put(upgrade, Arrays.asList(AccountUpgradeType.ResearchLab, AccountUpgradeType.IntergalacticResearchNetwork));
				break;
			case ShipyardItem:
				helpers.put(upgrade, Arrays.asList(AccountUpgradeType.Shipyard, AccountUpgradeType.NaniteFactory));
				break;
			}
		}
		HELPERS = Collections.unmodifiableMap(helpers);
	}

	private static List<AccountUpgradeType> getHelperTypes(AccountUpgradeType upgrade) {
		return HELPERS.get(upgrade);
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
			if (account.getResearch().getCurrentUpgrade() == upgrade.research) {
				currentLevel += account.getResearch().getUpgradeAmount();
			}
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
			if (body.getCurrentUpgrade() == upgrade.building) {
				currentLevel++;
			}
			targetLevel = currentLevel + 1;
			break;
		case ShipyardItem:
			if (body.getStationaryStructures().getCurrentUpgrade() == upgrade.shipyardItem) {
				currentLevel += body.getStationaryStructures().getUpgradeAmount();
			}
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
		if (upgrade == AccountUpgradeType.Astrophysics && account.roiPlanets().size() < theRules.economy().getMaxPlanets(account)) {
			RoiPlanet newPlanet = (RoiPlanet) account.getPlanets().newValue();
			cost = cost.plus(upgradeToLevel(account, newPlanet));
		}
		return cost;
	}

	private UpgradeCost upgradeToLevel(RoiAccount account, RoiPlanet planet) {
		// Build up the fresh new planet to the minimum level of its siblings
		// TODO Don't forget the template moon
		UpgradeCost cost = UpgradeCost.ZERO;
		for (BuildingType building : BuildingType.values()) {
			int min = -1;
			for (RoiPlanet p : account.roiPlanets()) {
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
			for (RoiPlanet p : account.roiPlanets()) {
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

	long addHelperUpgrades(RoiAccount account, List<RoiSequenceElement> sequence, boolean updates) {
		account.reset();
		// Now, insert actual energy production needed to supply full energy, plus crawlers
		ListIterator<RoiSequenceElement> sequenceIter = sequence.listIterator();
		TradeRatios tr = account.getUniverse().getTradeRatios();
		while (sequenceIter.hasNext()) {
			if (updates) {
				theProgress.set(sequenceIter.nextIndex(), null);
			}
			RoiSequenceElement el = sequenceIter.next();
			if (el.planetIndex < 0) {
				coreUpgrade(account, null, el.upgrade);
				account.flush();
				continue;
			}
			RoiPlanet planet = account.roiPlanets().get(el.planetIndex);
			coreUpgrade(account, planet, el.upgrade);
			account.flush();
			if (el.upgrade.building == null || el.upgrade.building.isMine() == null) {
				continue;
			}
			Production energy = theRules.economy().getProduction(account, planet, ResourceType.Energy, 0);
			// sequenceIter.add(new RoiSequenceElement(AccountUpgradeType.Crawler, el.planetIndex, 0));
			// upgrade(account, planet, AccountUpgradeType.Crawler);
			account.flush();

			if (theEnergyType.get() == ProductionSource.Fusion && account.roiPlanets().size() >= MIN_FUSION_PLANET) {
				while (energy.totalNet >= 0) {
					if (planet.getFusionReactor() == 0) {
						planet.upgrade(AccountUpgradeType.SolarSatellite, -planet.getSolarSatellites());
						upgrade(account, planet, AccountUpgradeType.FusionReactor, planet.getFusionReactor() + 1);
						account.flush();
						continue;
					}
					// Can't really control well for deut usage here, so just go by best energy value
					// We'll account for absolutely everything in the final step
					int preDeutUsage = theRules.economy().getProduction(account, planet, ResourceType.Deuterium,
						energy.totalProduction / energy.totalConsumption).byType.getOrDefault(ProductionSource.Fusion, 0);
					UpgradeCost fusionCost = upgrade(account, planet, AccountUpgradeType.FusionReactor);
					account.getProduction(); // Update the energy and production value
					Production fusionEnergy = theRules.economy().getProduction(account, planet, ResourceType.Energy, 0);
					int fusionDeutUsage = theRules.economy().getProduction(account, planet, ResourceType.Deuterium,
						fusionEnergy.totalProduction / fusionEnergy.totalConsumption).byType.getOrDefault(ProductionSource.Fusion, 0);
					fusionCost = fusionCost
						.plus(UpgradeCost.of('b', 0, 0, Math.round((fusionDeutUsage - preDeutUsage) * el.roi), 0, Duration.ZERO, 1, 0, 0));
					account.clear();
					UpgradeCost energyCost = upgrade(account, planet, AccountUpgradeType.Energy, account.getResearch().getEnergy() + 1);
					int fusionEnergyBump = fusionEnergy.byType.get(ProductionSource.Fusion) - energy.byType.get(ProductionSource.Fusion);
					int researchEnergyBump = theRules.economy().getProduction(account, planet, ResourceType.Energy, 0).byType
						.get(ProductionSource.Fusion) - energy.byType.get(ProductionSource.Fusion);
					researchEnergyBump *= account.roiPlanets().size();
					double fusionEfficiency = fusionEnergyBump / fusionCost.getMetalValue(tr);
					double researchEfficiency = researchEnergyBump / energyCost.getMetalValue(tr);
					if (researchEfficiency > fusionEfficiency) {
						sequenceIter.add(new RoiSequenceElement(AccountUpgradeType.Energy, el.planetIndex, 0));
						account.flush();
					} else {
						sequenceIter.add(new RoiSequenceElement(AccountUpgradeType.FusionReactor, el.planetIndex, 0));
						account.clear();
						upgrade(account, planet, AccountUpgradeType.FusionReactor);
						account.flush();
					}
				}
			} else if (theEnergyType.get() == ProductionSource.Solar) {
				while (energy.totalNet >= 0) {
					sequenceIter.add(new RoiSequenceElement(AccountUpgradeType.SolarPlant, el.planetIndex, 0));
					upgrade(account, planet, AccountUpgradeType.SolarPlant, planet.getSolarPlant() + 1);
					account.flush();
					energy = theRules.economy().getProduction(account, planet, ResourceType.Energy, 0);
				}
			} else {
				sequenceIter.add(new RoiSequenceElement(AccountUpgradeType.SolarSatellite, el.planetIndex, 0));
				upgrade(account, planet, AccountUpgradeType.SolarSatellite);
				account.flush();
			}
		}

		account.reset();
		long lifetimeMetric = getLifetimeMetric(account, sequence);
		sequenceIter = sequence.listIterator();
		while (sequenceIter.hasNext()) {
			RoiSequenceElement el = sequenceIter.next();
			sequenceIter.previous();
			List<AccountUpgradeType> helperTypes = getHelperTypes(el.upgrade);
			RoiSequenceElement bestHelper;
			do {
				if (updates) {
					theLifetimeMetric.set(Duration.ofSeconds(lifetimeMetric), null);
					theProgress.set(sequenceIter.nextIndex(), null);
				}
				bestHelper = null;
				long bestLifetime = -1;
				boolean first = true;
				for (AccountUpgradeType helper : helperTypes) {
					if (el.planetIndex >= 0 || helper.research != null) {
						RoiSequenceElement helperEl = new RoiSequenceElement(helper, el.planetIndex, 0);
						if (first) {
							sequenceIter.add(helperEl);
							first = false;
						} else {
							sequenceIter.set(helperEl);
						}
						long helpedLifetime = getLifetimeMetric(account, sequence);
						if (lifetimeMetric <= helpedLifetime) {
							continue;
						} else if (bestLifetime < 0 || helpedLifetime < bestLifetime) {
							bestHelper = helperEl;
							bestLifetime = helpedLifetime;
						}
					} else {
						for (int p = 0; p < account.roiPlanets().size(); p++) {
							RoiSequenceElement helperEl = new RoiSequenceElement(helper, p, 0);
							if (first) {
								sequenceIter.add(helperEl);
								first = false;
							} else {
								sequenceIter.set(helperEl);
							}
							long helpedLifetime = getLifetimeMetric(account, sequence);
							if (lifetimeMetric <= helpedLifetime) {
								continue;
							} else if (bestLifetime < 0 || helpedLifetime < bestLifetime) {
								bestHelper = helperEl;
								bestLifetime = helpedLifetime;
							}
						}
					}
				}
				if (bestLifetime >= 0) {
					sequenceIter.set(bestHelper);
					lifetimeMetric = bestLifetime;
				} else if (!first) {
					sequenceIter.remove();
				}
			} while (bestHelper != null);
			sequenceIter.next();
		}
		return lifetimeMetric;
	}

	long getLifetimeMetric(RoiAccount account, Iterable<RoiSequenceElement> sequence) {
		for (RoiSequenceElement el : sequence) {
			if (el.planetIndex >= 0 && el.planetIndex >= account.roiPlanets().size()) {
				// Astro must be going. Gotta wait for it to finish.
				long upgradeTime = getUpgradeCompletion(account, AccountUpgradeType.Astrophysics, null);
				if (upgradeTime > 0) {
					account.advance(upgradeTime);
				}
				if (account.roiPlanets().size() == theRules.economy().getMaxPlanets(account)) {
					// This can happen as things get moved around, e.g. astro getting moved ahead of an upgrade on the new planet
					return Long.MAX_VALUE;
				}
			}
			if (account.roiPlanets().size() < theRules.economy().getMaxPlanets(account)) {
				RoiPlanet newPlanet = (RoiPlanet) account.getPlanets().newValue();
				UpgradeCost newPlanetCost = upgradeToLevel(account, newPlanet);
				account.spend(Math.round(newPlanetCost.getMetalValue(account.getUniverse().getTradeRatios())));
			}
			el.setTime(account.getTime());
			if (el.planetIndex >= 0) {
				RoiPlanet planet = account.roiPlanets().get(el.planetIndex);
				int targetLevel = getTargetLevel(account, planet, el.upgrade);
				el.setTargetLevel(targetLevel);
				upgrade(account, el.upgrade, planet, targetLevel);
			} else {
				RoiPlanet bestPlanet = null;
				long bestTime = -1;
				long bestUpgradeTime = -1;
				for (RoiPlanet planet : account.roiPlanets()) {
					int targetLevel = getTargetLevel(account, planet, el.upgrade);
					upgrade(account, el.upgrade, planet, targetLevel);

					long time = account.getTime();
					long upgradeTime = getUpgradeCompletion(account, el.upgrade, planet);
					boolean best = false;
					if (bestTime < 0) {
						best = true;
					} else if (account.getTime() < bestTime) {
						best = true;
					} else if (account.getTime() == bestTime && upgradeTime < bestUpgradeTime) {
						best = true;
					}
					if (best) {
						bestPlanet = planet;
						bestTime = time;
						bestUpgradeTime = upgradeTime;
					}
					account.clear();
				}
				int targetLevel = getTargetLevel(account, bestPlanet, el.upgrade);
				el.setTargetLevel(targetLevel);
				upgrade(account, el.upgrade, bestPlanet, targetLevel);
			}

			account.flush();
		}
		// Include the completion of the last astro and the leveling of the first planet in the lifetime metric
		if (account.getResearch().getCurrentUpgrade() == ResearchType.Astrophysics) {
			// Astro must be going. Gotta wait for it to finish.
			long upgradeTime = getUpgradeCompletion(account, AccountUpgradeType.Astrophysics, null);
			if (upgradeTime > 0) {
				account.advance(upgradeTime);
			}
			if (account.roiPlanets().size() == theRules.economy().getMaxPlanets(account)) {
				throw new IllegalStateException();
			}
		}
		if (account.roiPlanets().size() < theRules.economy().getMaxPlanets(account)) {
			RoiPlanet newPlanet = (RoiPlanet) account.getPlanets().newValue();
			UpgradeCost newPlanetCost = upgradeToLevel(account, newPlanet);
			account.spend(Math.round(newPlanetCost.getMetalValue(account.getUniverse().getTradeRatios())));
		}
		long time = account.getTime();
		account.reset();
		return time;
	}

	private void upgrade(RoiAccount account, AccountUpgradeType upgrade, RoiPlanet planet, int targetLevel) {
		// Pre-requisites first
		int currentLevel = upgrade.getLevel(account, planet);
		if (upgrade.research != null && account.getResearch().getCurrentUpgrade() == upgrade.research) {
			currentLevel += account.getResearch().getUpgradeAmount();
		} else if (upgrade.building != null && planet.getCurrentUpgrade() == upgrade.building) {
			currentLevel++;
		} else if (upgrade.shipyardItem != null && planet.getStationaryStructures().getCurrentUpgrade() == upgrade.shipyardItem) {
			currentLevel += planet.getStationaryStructures().getUpgradeAmount();
		}
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
