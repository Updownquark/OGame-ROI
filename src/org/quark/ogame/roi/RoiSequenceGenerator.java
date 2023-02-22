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
import org.qommons.ArrayUtils;
import org.qommons.IntList;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.ListenerList;
import org.qommons.collect.StampedLockingStrategy;
import org.qommons.threading.ElasticExecutor;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeSet;
import org.quark.ogame.OGameUtils;
import org.quark.ogame.roi.RoiAccount.RoiPlanet;
import org.quark.ogame.roi.RoiAccount.RoiRockyBody;
import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.AccountUpgradeType;
import org.quark.ogame.uni.BuildingType;
import org.quark.ogame.uni.Moon;
import org.quark.ogame.uni.OGameEconomyRuleSet.FullProduction;
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
import org.quark.ogame.uni.Utilizable;

public class RoiSequenceGenerator {
	public static final int MIN_FUSION_PLANET = 5;
	public static final Set<AccountUpgradeType> CORE_UPGRADES;
	private static final boolean SUPER_OPTIMIZATION = false;

	static {
		BetterList<AccountUpgradeType> upgrades = BetterTreeList.<AccountUpgradeType> build().build();
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
	private final SettableValue<Duration> theStorageContainment;
	private final SettableValue<Duration> theDefense;
	private final DefenseRatios theDefenseRatio;
	private final SettableValue<Boolean> isWithHoldingCargoes;
	private final SettableValue<Boolean> isWithHarvestingCargoes;

	private final SettableValue<String> theStatus;
	private final SettableValue<Integer> theProgress;
	private final SettableValue<Duration> theLifetimeMetric;

	private final SettableValue<Boolean> isActive;
	private volatile boolean isCanceled;

	public RoiSequenceGenerator(OGameRuleSet rules, Account account) {
		theRules = rules;
		theAccount = account;
		StampedLockingStrategy locker = new StampedLockingStrategy(this, ThreadConstraint.ANY);
		isActive = SettableValue.build(boolean.class).withValue(false).withLocking(locker).build();
		ObservableValue<String> disabled = isActive.map(active -> active ? "Sequence is already being calculated" : null);

		theNewPlanetSlot = SettableValue.build(int.class).withValue(8).withLocking(locker).build().disableWith(disabled);
		theNewPlanetFields = SettableValue.build(int.class).withValue(200).withLocking(locker).build().disableWith(disabled);
		theNewPlanetTemp = SettableValue.build(int.class).withValue(30).withLocking(locker).build().disableWith(disabled);
		theTargetPlanet = SettableValue.build(int.class)//
			.withValue(Math.max(15, account.getPlanets().getValues().size() + 2))//
			.withLocking(locker).build().disableWith(disabled);
		theEnergyType = SettableValue.build(ProductionSource.class).withValue(ProductionSource.Satellite).withLocking(locker).build()
			.disableWith(disabled);
		theTemplateMoon = SettableValue.build(Moon.class).withLocking(locker).build().disableWith(disabled);
		theStorageContainment = SettableValue.build(Duration.class).withValue(Duration.ZERO).withLocking(locker).build()
			.disableWith(disabled);
		theDefense = SettableValue.build(Duration.class).withValue(Duration.ZERO).withLocking(locker).build().disableWith(disabled);
		theDefenseRatio = new DefenseRatios();
		isWithHoldingCargoes = SettableValue.build(boolean.class).withValue(false).withLocking(locker).build().disableWith(disabled);
		isWithHarvestingCargoes = SettableValue.build(boolean.class).withValue(false).withLocking(locker).build().disableWith(disabled);

		theStatus = SettableValue.build(String.class).withLocking(locker).build();
		theProgress = SettableValue.build(Integer.class).withLocking(locker).build();
		theLifetimeMetric = SettableValue.build(Duration.class).withLocking(locker).build();
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

	public SettableValue<Duration> getStorageContainment() {
		return theStorageContainment;
	}

	public SettableValue<Duration> getDefense() {
		return theDefense;
	}

	public DefenseRatios getDefenseRatio() {
		return theDefenseRatio;
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

	public void cancel() {
		isCanceled = true;
	}

	public void init(RoiAccount account) {
		for (RoiPlanet planet : account.roiPlanets()) {
			OGameUtils.optimizeEnergy(account, planet, theRules.economy());
		}
	}

	public void produceSequence(BetterList<RoiSequenceCoreElement> sequence) {
		isCanceled = false;
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
			for (int i = theAccount.getPlanets().getValues().size(); i < copy.roiPlanets().size(); i++) {
				RoiSequenceCoreElement el = new RoiSequenceCoreElement(new RoiSequenceElement(null, i, false)).setTargetLevel(0,
					UpgradeCost.ZERO);
				upgradeToLevel(copy, i, el);
				satisfyEnergy(copy, el);
				sequence.add(el);
			}
			copy.flush();
			for (int i = 0; i < theAccount.getPlanets().getValues().size(); i++) {
				RoiSequenceCoreElement el = new RoiSequenceCoreElement(new RoiSequenceElement(null, i, false)).setTargetLevel(0,
					UpgradeCost.ZERO);
				satisfyEnergy(copy, el);
				if (el.getPostHelpers().isEmpty()) {
					continue;
				}
				sequence.add(el);
				copy.flush();
			}
			init(copy);

			/*The basic way this works is:
			 * * Lay out a scaffolding of core upgrades
			 * * Fill it in with supporting upgrades
			 * * Poke it around to see if slight reordering is warranted 
			 */

			// First, lay out "core" production upgrades by ROI, assuming full energy supply and max crawlers
			TradeRatios tr = theAccount.getUniverse().getTradeRatios();
			try (Transaction branch = copy.branch()) {
				while (copy.roiPlanets().size() < theTargetPlanet.get()) {
					if (isCanceled) {
						return;
					}
					RoiSequenceCoreElement bestUpgradeEl = null;
					long preProduction = copy.getProduction();
					for (AccountUpgradeType upgrade : CORE_UPGRADES) {
						if (isCanceled) {
							return;
						}
						if (upgrade.research != null) {
							try (Transaction branch2 = copy.branch()) {
								RoiSequenceCoreElement upgradeEl = coreUpgrade(copy, -1, upgrade);
								long postProduction = copy.getProduction();
								if (postProduction > preProduction) {
									double roi = upgradeEl.getTotalCost().getMetalValue(tr) / (postProduction - preProduction);
									if (bestUpgradeEl == null || compareRoi(roi, bestUpgradeEl.getRoi()) > 0) {
										bestUpgradeEl = upgradeEl.setRoi(roi);
									}
								}
							}
						} else {
							// Find the best planet to upgrade
							for (int i = 0; i < copy.roiPlanets().size(); i++) {
								if (isCanceled) {
									return;
								}
								try (Transaction branch2 = copy.branch()) {
									RoiSequenceCoreElement upgradeEl = coreUpgrade(copy, i, upgrade);
									double planetRoi = upgradeEl.getRoi();
									if (bestUpgradeEl == null || compareRoi(planetRoi, bestUpgradeEl.getRoi()) > 0) {
										bestUpgradeEl = upgradeEl;
									}
								}
							}
						}
					}

					upgrade(copy, bestUpgradeEl, false);
					copy.flush();
					sequence.add(bestUpgradeEl);
				}
			}

			RoiSequenceCoreElement[] seqArray = sequence.toArray(new RoiSequenceCoreElement[sequence.size()]);
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

			ListenerList<Mutation> evaluatedMutations = ListenerList.build().build();
			ElasticExecutor<Mutation> mutationExecutor = new ElasticExecutor<>("Mutation Optimizer",
				() -> new ElasticExecutor.TaskExecutor<Mutation>() {
					private final RoiAccount account = new RoiAccount(RoiSequenceGenerator.this, theAccount);

					@Override
					public void execute(Mutation task) {
						if (isCanceled) {
							return;
						}
						task.evaluate(account);
						evaluatedMutations.add(task, false);
						account.reset();
					}
				});
			Mutation original = new Mutation(seqArray, lifetimeMetric);
			BetterTreeSet<Mutation> sortedMutations = BetterTreeSet
				.<Mutation> buildTreeSet((m1, m2) -> Long.compare(m1.lifetimeMetric, m2.lifetimeMetric)).build();
			sortedMutations.add(original);
			int mutationCount = 100;
			for (int i = 1; i < mutationCount; i++) {
				mutationExecutor.execute(new Mutation(seqArray));
			}
			Mutation bestMutation = original;
			long bestMutatedLifetime = lifetimeMetric;
			System.out.println("Optimizing from " + QommonsUtils.printTimeLength(bestMutatedLifetime));
			for (int round = 0; round < 10; round++) {
				theStatus.set("Optimizing Upgrade Order (Round " + (round + 1) + " of 10)", null);
				mutationExecutor.waitWhileActive(0);
				if (isCanceled) {
					return;
				}
				evaluatedMutations.dumpInto(sortedMutations);
				evaluatedMutations.clear();
				while (sortedMutations.size() > mutationCount) {
					sortedMutations.removeLast();
				}
				if (sortedMutations.first() != bestMutation) {
					bestMutation = sortedMutations.first();
					System.out.println("Improved to " + QommonsUtils.printTimeLength(bestMutation.lifetimeMetric));
					CollectionUtils.synchronize(sequence, Arrays.asList(bestMutation.coreSequence), (m1, m2) -> m1 == m2)//
						.simple(m -> m).rightOrder().adjust();
				}
				int i = 0;
				int j = sortedMutations.size();
				for (Mutation m : sortedMutations) {
					int reproduction;
					if (m.lifetimeMetric < bestMutatedLifetime) {
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
					for (int k = 0; k < reproduction && j < mutationCount * 10; k++, j++) {
						mutationExecutor.execute(m.copy());
					}
					i++;
				}
				bestMutatedLifetime = bestMutation.lifetimeMetric;
			}
			theStatus.set("ROI Sequence Complete", null);
			theProgress.set(sequence.size(), null);
			theLifetimeMetric.set(Duration.ofSeconds(lifetimeMetric), null);
		} finally {
			if (isCanceled) {
				theStatus.set("Canceled", null);
				isCanceled = false;
			}
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

	class Mutation implements Comparable<Mutation> {
		final RoiSequenceCoreElement[] coreSequence;
		long lifetimeMetric;
		int mutatedIndex;

		Mutation(RoiSequenceCoreElement[] seq) {
			coreSequence = seq.clone();
			lifetimeMetric = -1;
			mutatedIndex = -1;
			mutate();
		}

		Mutation(RoiSequenceCoreElement[] coreSeq, long lifetimeMetric) {
			coreSequence = coreSeq.clone();
			this.lifetimeMetric = lifetimeMetric;
		}

		@Override
		public int compareTo(Mutation other) {
			return Long.compare(lifetimeMetric, other.lifetimeMetric);
		}

		void mutate() {
			Random r = new Random();
			mutatedIndex = r.nextInt(coreSequence.length - 1);
			RoiSequenceCoreElement el = coreSequence[mutatedIndex];
			coreSequence[mutatedIndex] = coreSequence[mutatedIndex + 1];
			coreSequence[mutatedIndex + 1] = el;
		}

		void evaluate(RoiAccount account) {
			// Clear out and all the accessories that might be different now and re-evaluate them after
			for (int i = mutatedIndex; i < coreSequence.length; i++) {
				coreSequence[i].clearAccessories();
			}
			List<RoiSequenceCoreElement> seq = Arrays.asList(coreSequence);
			lifetimeMetric = getLifetimeMetric(account, seq);
			// It might be the case that the helpers on the sequence element that has been advanced
			// would better do their work on the previous element for which they help at all
			for (int h = 0; h < seq.get(mutatedIndex + 1).getPreHelpers().size(); h++) {
				for (int j = mutatedIndex - 1; j >= 0; j--) {
					if (isCanceled) {
						return;
					}
					if (getHelperTypes(seq.get(j).upgrade).contains(seq.get(mutatedIndex + 1).getPreHelpers().get(h))) {
						seq.get(j).withPreHelper(seq.get(mutatedIndex + 1).removePreHelper(h));
						long newLifetime = getLifetimeMetric(account, seq);
						if (newLifetime < lifetimeMetric) {
							lifetimeMetric = newLifetime;
							h--;
						} else {
							seq.get(mutatedIndex + 1).withPreHelper(h, seq.get(j).removePreHelper(seq.get(j).getPreHelpers().size() - 1));
						}
						break;
					}
				}
			}
			for (int h = 0; h < coreSequence[mutatedIndex + 1].getPostHelpers().size(); h++) {
				for (int j = mutatedIndex - 1; j >= 0; j--) {
					if (isCanceled) {
						return;
					}
					if (getHelperTypes(seq.get(j).upgrade).contains(seq.get(mutatedIndex + 1).getPostHelpers().get(h))) {
						seq.get(j).withPostHelper(seq.get(mutatedIndex + 1).removePostHelper(h));
						long newLifetime = getLifetimeMetric(account, seq);
						if (newLifetime < lifetimeMetric) {
							lifetimeMetric = newLifetime;
							h--;
						} else {
							seq.get(mutatedIndex + 1).withPostHelper(h,
								seq.get(j).removePostHelper(seq.get(j).getPostHelpers().size() - 1));
						}
						break;
					}
				}
			}
			for (int i = mutatedIndex; i < coreSequence.length; i++) {
				addAccessories(account, coreSequence[i]);
			}
		}

		Mutation copy() {
			return new Mutation(coreSequence);
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

	public RoiSequenceCoreElement coreUpgrade(RoiAccount account, int planetIndex, AccountUpgradeType upgrade) {
		long preProduction;
		RoiPlanet planet = planetIndex < 0 ? null : account.roiPlanets().get(planetIndex);
		RoiSequenceCoreElement el;
		switch (upgrade) {
		case MetalMine:
		case CrystalMine:
		case DeuteriumSynthesizer:
			preProduction = planet.getProductionValue();
			int currentLevel = planet.getBuildingLevel(upgrade.building);
			el = new RoiSequenceCoreElement(upgrade(account, planetIndex, false, upgrade, currentLevel + 1));
			// int currentCrawlers = planet.getStationaryStructures().getCrawlers();
			// int maxCrawlers = theRules.economy().getMaxCrawlers(account, planet);
			// if (maxCrawlers > currentCrawlers) {
			// el.withHelper(false, upgrade(account, planetIndex, false, AccountUpgradeType.Crawler, maxCrawlers));
			// }
			// planet.setCrawlerUtilization(theRules.economy().getMaxUtilization(Utilizable.Crawler, account, planet));
			// int sats = OGameUtils.getRequiredSatellites(account, planet, theRules.economy());
			// if (sats > planet.getSolarSatellites()) {
			// el.withHelper(false, upgrade(account, planetIndex, false, AccountUpgradeType.SolarSatellite, sats));
			// }
			break;
		case Astrophysics:
			preProduction = account.getProduction();
			currentLevel = account.getResearch().getAstrophysics();
			int targetLevel = currentLevel + ((currentLevel % 2 == 0) ? 1 : 2);
			el = new RoiSequenceCoreElement(upgrade(account, -1, false, upgrade, targetLevel));
			break;
		case Plasma:
			preProduction = account.getProduction();
			currentLevel = account.getResearch().getPlasma();
			el = new RoiSequenceCoreElement(upgrade(account, -1, false, upgrade, currentLevel + 1));
			break;
		default:
			throw new IllegalStateException("Unrecognized core upgrade: " + upgrade);
		}
		optimizePostHelpers(account, el, preProduction);
		long newProduction = planet == null ? account.getProduction() : planet.getProductionValue();
		el.setRoi(el.getTotalCost().getMetalValue(account.getUniverse().getTradeRatios()) / (newProduction - preProduction));
		return el;
	}

	private void optimizePostHelpers(RoiAccount account, RoiSequenceCoreElement upgrade, long preProduction) {
		if (isCanceled) {
			return;
		}
		if (upgrade.upgrade.research == ResearchType.Astrophysics) {
			if (account.getPlanets().getValues().get(0).getStationedFleet().getColonyShips() == 0) {
				upgrade.withPostHelper(upgrade(account, 0, false, AccountUpgradeType.ColonyShip, 1));//
				account.roiPlanets().get(0).upgrade(AccountUpgradeType.ColonyShip, -1);
			}
			if (account.roiPlanets().size() < theRules.economy().getMaxPlanets(account)) {
				account.getPlanets().newValue();
			}
			upgradeToLevel(account, account.roiPlanets().size() - 1, upgrade);
			return;
		} else if (upgrade.upgrade.research != null) {
			return;
		}
		Transaction branch = account.branch();
		try {
			RoiPlanet planet = upgrade.planetIndex < 0 ? null : account.roiPlanets().get(upgrade.planetIndex);
			int currentCrawlers = planet.getStationaryStructures().getCrawlers();
			int maxCrawlers = theRules.economy().getMaxCrawlers(account, planet);
			// planet.setCrawlerUtilization(theRules.economy().getMaxUtilization(Utilizable.Crawler, account, planet));
			satisfyEnergy(account, upgrade);
			if (currentCrawlers >= maxCrawlers) {
				return;
			}

			// Check the ROI for no crawler upgrades
			double baseCost = upgrade.getTotalCost().getMetalValue(account.getUniverse().getTradeRatios());
			long production = planet.getProductionValue();
			double baseRoi = baseCost / (production - preProduction);
			upgrade.setRoi(baseRoi); // Need an ROI here to optimize fusion

			// Check the ROI for a single crawler upgrade
			upgrade.withPostHelper(upgrade(account, upgrade.planetIndex, false, AccountUpgradeType.Crawler, currentCrawlers + 1));
			satisfyEnergy(account, upgrade);
			long newProduction = planet.getProductionValue();
			double newCost = upgrade.getTotalCost().getMetalValue(account.getUniverse().getTradeRatios());
			double singleCRoi = newCost / (newProduction - preProduction);

			double maxCRoi;
			if (maxCrawlers > currentCrawlers + 1) {
				branch.close();
				branch = account.branch();
				upgrade.clearPostHelpers();
				upgrade(account, upgrade, false);
				upgrade.withPostHelper(upgrade(account, upgrade.planetIndex, false, AccountUpgradeType.Crawler, maxCrawlers));
				satisfyEnergy(account, upgrade);
				newProduction = planet.getProductionValue();
				newCost = upgrade.getTotalCost().getMetalValue(account.getUniverse().getTradeRatios());
				maxCRoi = newCost / (newProduction - preProduction);
			} else {
				maxCRoi = singleCRoi;
			}
			if (compareRoi(maxCRoi, baseRoi) > 0) { // Done
			} else if (compareRoi(singleCRoi, baseRoi) > 0) {
				// binary search
				int bestCrawlerCount = ArrayUtils.optimize(currentCrawlers, maxCrawlers, crawlers -> {
					if (isCanceled) {
						return 0;
					}
					try (Transaction branch2 = account.branch()) {
						upgrade.clearPostHelpers();
						upgrade(account, upgrade, false);
						upgrade.withPostHelper(upgrade(account, upgrade.planetIndex, false, AccountUpgradeType.Crawler, crawlers));
						satisfyEnergy(account, upgrade);
						long prod2 = planet.getProductionValue();
						double cost2 = upgrade.getTotalCost().getMetalValue(account.getUniverse().getTradeRatios());
						return cost2 / (prod2 - preProduction);
					}
				}, RoiSequenceGenerator::compareRoi);
				if (isCanceled) {
					return;
				}
				branch.close();
				branch = account.branch();
				upgrade.clearPostHelpers();
				upgrade.withPostHelper(upgrade(account, upgrade.planetIndex, false, AccountUpgradeType.Crawler, bestCrawlerCount));
				satisfyEnergy(account, upgrade);
				long prod2 = planet.getProductionValue();
				double cost2 = upgrade.getTotalCost().getMetalValue(account.getUniverse().getTradeRatios());
				upgrade.setRoi(cost2 / (prod2 - preProduction));
			} else {
				upgrade.clearPostHelpers();
				upgrade(account, upgrade, false);
				satisfyEnergy(account, upgrade);
			}
		} finally {
			account.flush();
			branch.close();
		}
	}

	private static int compareRoi(double roi1, double roi2) {
		if (roi1 < 0) {
			if (roi2 < 0) {
				return Double.compare(roi1, roi2);
			} else {
				return -1;
			}
		} else if (roi2 < 0) {
			return 1;
		} else {
			return -Double.compare(roi1, roi2);
		}
	}

	private void satisfyEnergy(RoiAccount account, RoiSequenceCoreElement upgrade) {
		RoiPlanet planet = account.roiPlanets().get(upgrade.planetIndex);
		planet.setCrawlerUtilization(theRules.economy().getMaxUtilization(Utilizable.Crawler, account, planet));
		planet.setFusionReactorUtilization(theRules.economy().getMaxUtilization(Utilizable.FusionReactor, account, planet));

		ProductionSource type = theEnergyType.get();
		if (type == ProductionSource.Fusion && account.roiPlanets().size() < MIN_FUSION_PLANET) {
			// It's not practical to raise energy tech enough to start fusion until the account has been developed enough.
			// We'll capture this with a minimum planet count.
			if (theRules.economy().getSatelliteEnergy(account, planet) < 5) {
				type = ProductionSource.Solar;
			} else {
				type = ProductionSource.Satellite;
			}
		}
		switch (type) {
		case Fusion:
			if (planet.getFusionReactor() == 0) {
				upgrade.withPostHelper(upgrade(account, upgrade.planetIndex, false, AccountUpgradeType.SolarSatellite, 0));
				upgrade.withPostHelper(
					upgrade(account, upgrade.planetIndex, false, AccountUpgradeType.FusionReactor, planet.getFusionReactor() + 1));
			}
			Production energy = theRules.economy().getProduction(account, planet, ResourceType.Energy, 0);
			while (energy.totalNet < 0) {
				if (isCanceled) {
					return;
				}
				// Can't really control well for deut usage here, so just go by best energy value
				// We'll account for absolutely everything in the final step
				UpgradeCost fusionCost;
				int fusionEnergyBump;
				try (Transaction branch = account.branch()) {
					int preDeutUsage = theRules.economy().getProduction(account, planet, ResourceType.Deuterium,
						energy.totalProduction / energy.totalConsumption).byType.getOrDefault(ProductionSource.Fusion, 0);
					fusionCost = upgrade(account, upgrade.planetIndex, false, AccountUpgradeType.FusionReactor).getTotalCost();
					account.getProduction(); // Update the energy and production value
					Production fusionEnergy = theRules.economy().getProduction(account, planet, ResourceType.Energy, 0);
					int fusionDeutUsage = theRules.economy().getProduction(account, planet, ResourceType.Deuterium,
						fusionEnergy.totalProduction / fusionEnergy.totalConsumption).byType.getOrDefault(ProductionSource.Fusion, 0);
					fusionCost = fusionCost.plus(UpgradeCost.of('b', 0, 0, Math.round((fusionDeutUsage - preDeutUsage) * upgrade.getRoi()),
						0, Duration.ZERO, 1, 0, 0));
					fusionEnergyBump = fusionEnergy.byType.get(ProductionSource.Fusion) - energy.byType.get(ProductionSource.Fusion);
				}
				UpgradeCost energyCost;
				int researchEnergyBump;
				try (Transaction branch = account.branch()) {
					energyCost = upgrade(account, upgrade.planetIndex, false, AccountUpgradeType.Energy,
						account.getResearch().getEnergy() + 1).getTotalCost();
					researchEnergyBump = theRules.economy().getProduction(account, planet, ResourceType.Energy, 0).byType
						.get(ProductionSource.Fusion) - energy.byType.get(ProductionSource.Fusion);
					researchEnergyBump *= account.roiPlanets().size();
				}
				double fusionEfficiency = fusionEnergyBump / fusionCost.getMetalValue(account.getUniverse().getTradeRatios());
				double researchEfficiency = researchEnergyBump / energyCost.getMetalValue(account.getUniverse().getTradeRatios());
				if (researchEfficiency > fusionEfficiency) {
					upgrade.withPostHelper(
						upgrade(account, upgrade.planetIndex, false, AccountUpgradeType.Energy, account.getResearch().getEnergy() + 1));
					upgrade(account, upgrade, false);
				} else {
					upgrade.withPostHelper(upgrade(account, upgrade.planetIndex, false, AccountUpgradeType.FusionReactor));
					upgrade(account, upgrade, false);
				}
				energy = theRules.economy().getProduction(account, planet, ResourceType.Energy, 0);
			}
			break;
		case Solar:
			energy = theRules.economy().getProduction(account, planet, ResourceType.Energy, 0);
			while (energy.totalNet < 0) {
				if (isCanceled) {
					return;
				}
				upgrade.withPostHelper(
					upgrade(account, upgrade.planetIndex, false, AccountUpgradeType.SolarPlant, planet.getSolarPlant() + 1));
				energy = theRules.economy().getProduction(account, planet, ResourceType.Energy, 0);
			}
			break;
		case Satellite:
			int sats = OGameUtils.getRequiredSatellites(account, planet, theRules.economy());
			if (planet.getSolarSatellites() < sats) {
				upgrade.withPostHelper(upgrade(account, upgrade.planetIndex, false, AccountUpgradeType.SolarSatellite, sats));
			}
			break;
		default:
			throw new IllegalStateException("Unrecognized energy source: " + type);
		}
		OGameUtils.optimizeEnergy(account, planet, theRules.economy());
		addAccessories(account, upgrade);
	}

	private void addAccessories(RoiAccount account, RoiSequenceCoreElement el) {
		// Storage
		Duration storage = theStorageContainment.get();
		if (!storage.isZero()) {
			if (el.planetIndex >= 0) {
				checkStorage(account, el, el.planetIndex);
			} else {
				for (int i = 0; i < account.roiPlanets().size(); i++) {
					checkStorage(account, el, i);
				}
			}
		}
		Duration def = theDefense.get();
		UpgradeCost defCost = theDefenseRatio.getTotalCost(account, account.roiPlanets().getFirst(), theRules.economy());
		double defUnitCost = defCost.getMetalValue(account.getUniverse().getTradeRatios());
		if (!def.isZero() && defUnitCost > 0) {
			if (el.planetIndex >= 0) {
				checkDefense(account, el, el.planetIndex, defUnitCost);
			} else {
				for (int i = 0; i < account.roiPlanets().size(); i++) {
					checkDefense(account, el, i, defUnitCost);
				}
			}
		}
		int todo; // TODO cargoes
	}

	private void checkStorage(RoiAccount account, RoiSequenceCoreElement el, int planetIndex) {
		if (isCanceled) {
			return;
		}
		Duration storage = theStorageContainment.get();
		RoiPlanet planet = account.roiPlanets().get(el.planetIndex);
		FullProduction production = planet.getProduction();

		long storageReq = production.metal * storage.getSeconds() / 3600;
		long storageCap = theRules.economy().getStorage(planet, ResourceType.Metal);
		while (storageCap < storageReq) {
			el.withAccessory(upgrade(account, planetIndex, false, AccountUpgradeType.MetalStorage));
			storageCap = theRules.economy().getStorage(planet, ResourceType.Metal);
		}

		storageReq = production.crystal * storage.getSeconds() / 3600;
		storageCap = theRules.economy().getStorage(planet, ResourceType.Crystal);
		while (storageCap < storageReq) {
			el.withAccessory(upgrade(account, planetIndex, false, AccountUpgradeType.CrystalStorage));
			storageCap = theRules.economy().getStorage(planet, ResourceType.Crystal);
		}

		storageReq = production.deuterium * storage.getSeconds() / 3600;
		storageCap = theRules.economy().getStorage(planet, ResourceType.Crystal);
		while (storageCap < storageReq) {
			el.withAccessory(upgrade(account, planetIndex, false, AccountUpgradeType.DeuteriumStorage));
			storageCap = theRules.economy().getStorage(planet, ResourceType.Crystal);
		}
	}

	private void checkDefense(RoiAccount account, RoiSequenceCoreElement el, int planetIndex, double defUnitCost) {
		if (isCanceled) {
			return;
		}
		RoiPlanet planet = account.roiPlanets().get(planetIndex);
		int defenseUnitCount = (int) Math.round(planet.getProductionValue() * 1.0 * theDefense.get().getSeconds() / 3600 / defUnitCost);
		int amount = defenseUnitCount * theDefenseRatio.getRocketLaunchers().get();
		if (planet.getStationaryStructures().getRocketLaunchers() < amount) {
			el.withAccessory(upgrade(account, planetIndex, false, AccountUpgradeType.RocketLauncher, amount));
		}
		amount = defenseUnitCount * theDefenseRatio.getLightLasers().get();
		if (planet.getStationaryStructures().getLightLasers() < amount) {
			el.withAccessory(upgrade(account, planetIndex, false, AccountUpgradeType.LightLaser, amount));
		}
		amount = defenseUnitCount * theDefenseRatio.getLightLasers().get();
		if (planet.getStationaryStructures().getHeavyLasers() < amount) {
			el.withAccessory(upgrade(account, planetIndex, false, AccountUpgradeType.HeavyLaser, amount));
		}
		amount = defenseUnitCount * theDefenseRatio.getLightLasers().get();
		if (planet.getStationaryStructures().getGaussCannons() < amount) {
			el.withAccessory(upgrade(account, planetIndex, false, AccountUpgradeType.GaussCannon, amount));
		}
		amount = defenseUnitCount * theDefenseRatio.getLightLasers().get();
		if (planet.getStationaryStructures().getIonCannons() < amount) {
			el.withAccessory(upgrade(account, planetIndex, false, AccountUpgradeType.IonCannon, amount));
		}
		amount = defenseUnitCount * theDefenseRatio.getLightLasers().get();
		if (planet.getStationaryStructures().getPlasmaTurrets() < amount) {
			el.withAccessory(upgrade(account, planetIndex, false, AccountUpgradeType.PlasmaTurret, amount));
		}
	}

	private RoiSequenceElement upgrade(RoiAccount account, int planetIndex, boolean moon, AccountUpgradeType upgrade) {
		return upgrade(account, planetIndex, moon, upgrade, getTargetLevel(account, planetIndex, moon, upgrade));
	}

	private int getTargetLevel(RoiAccount account, int planetIndex, boolean moon, AccountUpgradeType upgrade) {
		if (upgrade == null) {
			return 0;
		}
		RoiRockyBody body;
		if (planetIndex < 0) {
			body = null;
		} else if (moon) {
			body=account.roiPlanets().get(planetIndex).getMoon();
		} else if (planetIndex < account.roiPlanets().size()) {
			body=account.roiPlanets().get(planetIndex);
		} else {
			if (upgrade.research != null) {
				body=null;
			} else {
				return 1;
			}
		}
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

	private RoiSequenceElement upgrade(RoiAccount account, int planetIndex, boolean moon, AccountUpgradeType upgrade, int targetLevel) {
		RoiSequenceElement el = new RoiSequenceElement(upgrade, planetIndex, moon);
		RoiRockyBody body = el.getBody(account);
		int currentLevel = upgrade.getLevel(account, body);
		if (targetLevel < 0) {
			targetLevel = currentLevel + 1;
		}
		RoiRockyBody rBody = body != null ? body : account.roiPlanets().getFirst();
		int baseLevel;
		if (rBody.getTargetBody() != null) {
			baseLevel = upgrade.getLevel(account, rBody.getTargetBody());
		} else {
			baseLevel = 0;
		}
		if (currentLevel == baseLevel) {
			// May need an actual planet for requirements
			int pi=planetIndex>=0 ? planetIndex : 0;
			// For all upgrades, calculate ROI based on the cost of not only the building itself,
			// but of all required buildings and researches needed to be able to build it
			for (Requirement req : theRules.economy().getRequirements(upgrade)) {
				int currentReqLevel = req.type.getLevel(account, rBody);
				if (currentReqLevel < req.level) {
					el.withDependency(upgrade(account, pi, moon, req.type, req.level));
				}
			}
		}

		el.setTargetLevel(targetLevel, theRules.economy().getUpgradeCost(account, body, upgrade, currentLevel, targetLevel));
		account.upgrade(upgrade, body, targetLevel - currentLevel);
		int used = body == null ? 0 : body.getUsedFields();
		int total;
		if (body == null) {
			total = 1;
		} else if (body instanceof Planet) {
			total=theRules.economy().getFields((Planet) body);
		} else {
			total= theRules.economy().getFields((Moon) body);
		}
		while (body instanceof Planet && used >= total && upgrade != AccountUpgradeType.Terraformer && !isCanceled) {
			// See if we can tear down solar first
			boolean usingSolar = ((Planet) body).getSolarPlant() > 0;
			if (usingSolar) {
				switch (theEnergyType.get()) {
				case Solar:
					usingSolar = true;
					break;
				case Fusion:
					usingSolar = account.roiPlanets().size() < MIN_FUSION_PLANET
						&& theRules.economy().getSatelliteEnergy(account, (Planet) body) < 5;
					break;
				default:
					usingSolar = false;
					break;
				}
			}
			if (!usingSolar && ((Planet) body).getSolarPlant() > 0) {
				el.withDependency(upgrade(account, planetIndex, false, AccountUpgradeType.SolarPlant, ((Planet) body).getSolarPlant() - 1));
			} else {
				el.withDependency(
					upgrade(account, planetIndex, false, AccountUpgradeType.Terraformer, ((Planet) body).getTerraformer() + 1));
			}
			used = body.getUsedFields();
			total = theRules.economy().getFields((Planet) body);
		}
		while (body instanceof Moon && used >= total && upgrade != AccountUpgradeType.LunarBase && !isCanceled) {
			el.withDependency(upgrade(account, planetIndex, true, AccountUpgradeType.LunarBase, ((Moon) body).getLunarBase() + 1));
			used = body.getUsedFields();
			total = theRules.economy().getFields((Moon) body);
		}
		return el;
	}

	private void upgradeToLevel(RoiAccount account, int planetIndex, RoiSequenceCoreElement upgrade) {
		RoiPlanet planet = account.roiPlanets().get(planetIndex);
		// Build up the fresh new planet to the minimum level of its siblings
		for (BuildingType building : BuildingType.values()) {
			int min = -1;
			for (int p = 0; p < account.roiPlanets().size(); p++) {
				if (p == planetIndex) {
					break;
				}
				int level = account.roiPlanets().get(p).getBuildingLevel(building);
				if (min < 0 || level < min) {
					min = level;
				}
			}
			if (planet.getBuildingLevel(building) < min) {
				upgrade.withPostHelper(upgrade(account, planetIndex, false, building.getUpgrade(), min));
			}
		}
		for (ShipyardItemType ship : ShipyardItemType.values()) {
			int min = -1;
			for (int p = 0; p < account.roiPlanets().size(); p++) {
				if (p == planetIndex) {
					break;
				}
				int level = account.roiPlanets().get(p).getStationedShips(ship);
				if (min < 0 || level < min) {
					min = level;
				}
			}
			if (min > 0) {
				upgrade.withPostHelper(upgrade(account, planetIndex, false, ship.getUpgrade(), min));
			}
		}
		Moon templateMoon = theTemplateMoon.get();
		if (templateMoon != null) {
			for (BuildingType building : BuildingType.values()) {
				int level = templateMoon.getBuildingLevel(building);
				if (planet.getMoon().getBuildingLevel(building) < level) {
					upgrade.withPostHelper(upgrade(account, planetIndex, true, building.getUpgrade(), level));
				}
			}
			for (ShipyardItemType ship : ShipyardItemType.values()) {
				int level = templateMoon.getStationedShips(ship);
				if (level > 0) {
					upgrade.withPostHelper(upgrade(account, planetIndex, true, ship.getUpgrade(), level));
				}
			}
		}
	}

	long addHelperUpgrades(RoiAccount account, List<RoiSequenceCoreElement> sequence, boolean updates) {
		account.reset();
		long lifetimeMetric = getLifetimeMetric(account, sequence);
		account.reset();
		int seqIndex = -1;
		ListIterator<RoiSequenceCoreElement> sequenceIter = sequence.listIterator();
		while (sequenceIter.hasNext()) {
			if (isCanceled) {
				return -1;
			}
			seqIndex++;
			if (updates) {
				theLifetimeMetric.set(Duration.ofSeconds(lifetimeMetric), null);
				theProgress.set(seqIndex, null);
			}
			RoiSequenceCoreElement el = sequenceIter.next();
			List<AccountUpgradeType> helperTypes = getHelperTypes(el.upgrade);
			if (helperTypes == null) {
				continue;
			}
			RoiSequenceElement bestHelper;
			do {
				if (updates) {
					theLifetimeMetric.set(Duration.ofSeconds(lifetimeMetric), null);
					theProgress.set(seqIndex, null); // Update the progress
				}
				bestHelper = null;
				// TODO Find helpers for existing pre-helpers, dependencies, post helpers, accessories
				long bestLifetime = lifetimeMetric;
				for (AccountUpgradeType helper : helperTypes) {
					if (el.planetIndex >= 0 || helper.research != null) {
						RoiSequenceElement helperEl;
						try (Transaction branch = account.branch()) {
							helperEl = upgrade(account, el.planetIndex, false, helper);
							el.withPreHelper(helperEl);
						}
						long helpedLifetime;
						try (Transaction branch = account.branch()) {
							helpedLifetime = getLifetimeMetric(account, sequence.subList(seqIndex, sequence.size()));
						}
						el.removePreHelper(el.getPreHelpers().size() - 1);
						if (compareLifetimes(helpedLifetime, bestLifetime) > 0) {
							bestHelper = helperEl;
							bestLifetime = helpedLifetime;
						}
					} else {
						for (int p = 0; p < account.roiPlanets().size(); p++) {
							RoiSequenceElement helperEl;
							try (Transaction branch = account.branch()) {
								helperEl = upgrade(account, p, false, helper);
								el.withPreHelper(helperEl);
							}
							long helpedLifetime;
							try (Transaction branch = account.branch()) {
								helpedLifetime = getLifetimeMetric(account, sequence.subList(seqIndex, sequence.size()));
							}
							el.removePreHelper(el.getPreHelpers().size() - 1);
							if (compareLifetimes(helpedLifetime, bestLifetime) > 0) {
								bestHelper = helperEl;
								bestLifetime = helpedLifetime;
							}
						}
					}
				}
				if (bestHelper != null) {
					el.withPreHelper(bestHelper);
					lifetimeMetric = bestLifetime;
					if (updates) {
						sequenceIter.set(el);
					}
				}
			} while (bestHelper != null);
			upgrade(account, el, true);
			account.flush();
		}
		if (updates) {
			theLifetimeMetric.set(Duration.ofSeconds(lifetimeMetric), null);
			theProgress.set(sequence.size(), null);
		}
		return lifetimeMetric;
	}

	private static int compareLifetimes(long lt1, long lt2) {
		if (lt1 < 0) {
			if (lt2 < 0) {
				return Long.compare(-lt1, -lt2);
			} else {
				return -1;
			}
		} else if (lt2 < 0) {
			return 1;
		} else {
			return -Long.compare(lt1, lt2);
		}
	}

	long getLifetimeMetric(RoiAccount account, Iterable<RoiSequenceCoreElement> sequence) {
		int index = -1;
		for (RoiSequenceCoreElement el : sequence) {
			index++;
			if (isCanceled) {
				return -1;
			}
			if (!upgrade(account, el, true)) {
				return -index;
			}
		}
		// Include the completion of the last astro and the leveling of the first planet in the lifetime metric
		if (account.getResearch().getCurrentUpgrade() == ResearchType.Astrophysics) {
			// Astro must be going. Gotta wait for it to finish.
			long upgradeTime = getUpgradeCompletion(account, AccountUpgradeType.Astrophysics, null);
			if (upgradeTime > 0) {
				account.advance(upgradeTime);
			}
		}
		// if (account.roiPlanets().size() < theRules.economy().getMaxPlanets(account)) {
		// RoiPlanet newPlanet = (RoiPlanet) account.getPlanets().newValue();
		// UpgradeCost newPlanetCost = upgradeToLevel(account, newPlanet);
		// account.spend(Math.round(newPlanetCost.getMetalValue(account.getUniverse().getTradeRatios())));
		// }
		return account.getTime();
	}

	public boolean upgrade(RoiAccount account, RoiSequenceElement el, boolean simulate) {
		for (RoiSequenceElement helper : el.getPreHelpers()) {
			if (!doUpgrade(account, helper, simulate)) {
				return false;
			}
		}
		if (!doUpgrade(account, el, simulate)) {
			return false;
		}
		if (el instanceof RoiSequenceCoreElement) {
			for (RoiSequenceElement helper : ((RoiSequenceCoreElement) el).getPostHelpers()) {
				if (!doUpgrade(account, helper, simulate)) {
					return false;
				}
			}
			for (RoiSequenceElement helper : ((RoiSequenceCoreElement) el).getAccessories()) {
				if (!doUpgrade(account, helper, simulate)) {
					return false;
				}
			}
		}
		return true;
	}

	private boolean doUpgrade(RoiAccount account, RoiSequenceElement upgrade, boolean simulate) {
		for (RoiSequenceElement dep : upgrade.getDependencies()) {
			if (!doUpgrade(account, dep, simulate)) {
				return false;
			}
		}
		if (upgrade.planetIndex >= 0 && upgrade.planetIndex >= account.roiPlanets().size()) {
			// Astro must be going. Gotta wait for it to finish.
			long upgradeTime = getUpgradeCompletion(account, AccountUpgradeType.Astrophysics, null);
			if (upgradeTime > 0) {
				account.advance(upgradeTime);
			}
			if (upgrade.planetIndex >= account.roiPlanets().size()
				&& account.roiPlanets().size() == theRules.economy().getMaxPlanets(account)) {
				// This can happen as things get moved around, e.g. astro getting moved ahead of an upgrade on the new planet
				return false;
			}
		}
		// if (account.roiPlanets().size() < theRules.economy().getMaxPlanets(account)) {
		// RoiPlanet newPlanet = (RoiPlanet) account.getPlanets().newValue();
		// UpgradeCost newPlanetCost = upgradeToLevel(account, newPlanet);
		// account.spend(Math.round(newPlanetCost.getMetalValue(account.getUniverse().getTradeRatios())));
		// }
		if (simulate) {
			upgrade.setTime(account.getTime());
		}
		if (upgrade.upgrade == null) {
			return true;
		}
		if (upgrade.upgrade.research == null) {
			RoiPlanet planet = account.roiPlanets().get(upgrade.planetIndex);
			int targetLevel;
			if (upgrade.getTargetLevel() >= 0) {
				targetLevel = upgrade.getTargetLevel();
			} else {
				targetLevel= getTargetLevel(account, upgrade.planetIndex, upgrade.isMoon, upgrade.upgrade);
			}
			return doUpgrade(account, upgrade.upgrade, planet, targetLevel, simulate);
		} else {
			long upgradeTime = getUpgradeCompletion(account, upgrade.upgrade, null);
			if (upgradeTime > 0) {
				account.advance(upgradeTime);
			}
			int bestPlanet = -1;
			int currentLevel = upgrade.getLevel(account);
			int targetLevel;
			if (upgrade.getTargetLevel() >= 0) {
				targetLevel = upgrade.getTargetLevel();
			} else {
				targetLevel = getTargetLevel(account, -1, false, upgrade.upgrade);
			}
			if (simulate) {
				Duration bestTime = null;
				for (int p = 0; p < account.roiPlanets().size(); p++) {
					RoiPlanet planet = account.roiPlanets().get(p);
					Duration planetTime = theRules.economy().getUpgradeCost(account, planet, upgrade.upgrade, currentLevel, targetLevel)
						.getUpgradeTime();
					if (bestTime == null || planetTime.compareTo(bestTime) < 0) {
						bestPlanet = p;
						bestTime = planetTime;
					}
				}
			} else {
				bestPlanet = 0;
			}
			return doUpgrade(account, upgrade.upgrade, account.roiPlanets().get(bestPlanet), targetLevel, simulate);
		}
	}

	private boolean doUpgrade(RoiAccount account, AccountUpgradeType upgrade, RoiPlanet planet, int targetLevel, boolean simulate) {
		if (upgrade == null) {
			return true;
		}
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
			return true;
		}
		if (currentLevel == 0) {
			for (Requirement req : theRules.economy().getRequirements(upgrade)) {
				int currentReqLevel = req.type.getLevel(account, planet);
				if (currentReqLevel < req.level) {
					if (!doUpgrade(account, req.type, planet, req.level, simulate)) {
						return false;
					}
				}
			}
		}

		if (simulate) {
			// Wait for any current upgrade to finish
			long upgradeTime = getUpgradeCompletion(account, upgrade, planet);
			if (upgradeTime > 0) {
				account.advance(upgradeTime);
			}
			UpgradeCost cost = theRules.economy().getUpgradeCost(account, planet, upgrade, currentLevel, targetLevel);
			if (cost.getUpgradeTime().getSeconds() == Long.MAX_VALUE) {
				return false;
			}
			long costAmount = Math.round(cost.getMetalValue(theAccount.getUniverse().getTradeRatios()));
			account.spend(costAmount);
			account.start(upgrade, planet, targetLevel - currentLevel, cost.getUpgradeTime().getSeconds());
		} else {
			account.upgrade(upgrade, planet, targetLevel-currentLevel);
		}
		return true;
	}

	private static long getUpgradeCompletion(RoiAccount account, AccountUpgradeType type, RoiPlanet planet) {
		long completion;
		if (type.research != null) {
			completion = account.getResearch().getUpgradeCompletion();
			for (RoiPlanet p : account.roiPlanets()) {
				if (p.getCurrentUpgrade() == BuildingType.ResearchLab) {
					completion = Math.max(completion, p.getUpgradeCompletion());
				}
			}
		} else if (type.building != null) {
			completion = planet.getUpgradeCompletion();
			switch (type.building) {
			case ResearchLab:
				completion = Math.max(completion, account.getResearch().getUpgradeCompletion());
				break;
			case Shipyard:
			case NaniteFactory:
				completion = Math.max(completion, planet.getStationaryStructures().getUpgradeCompletion());
				break;
			default:
				break;
			}
		} else {
			completion = planet.getStationaryStructures().getUpgradeCompletion();
			if (planet.getCurrentUpgrade() == BuildingType.Shipyard || planet.getCurrentUpgrade() == BuildingType.NaniteFactory) {
				completion=Math.max(completion, planet.getUpgradeCompletion());
			}
		}
		return completion;
	}
}
