package org.quark.ogame.roi;

import java.util.function.Function;
import java.util.function.ToIntFunction;

import org.observe.collect.ObservableCollection;
import org.observe.config.ConfiguredValueType;
import org.observe.config.SyncValueCreator;
import org.observe.config.SyncValueSet;
import org.observe.util.TypeTokens;
import org.qommons.BreakpointHere;
import org.qommons.Nameable;
import org.qommons.Transaction;
import org.qommons.collect.QuickSet.QuickMap;
import org.quark.ogame.OGameUtils;
import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.AccountClass;
import org.quark.ogame.uni.AccountUpgradeType;
import org.quark.ogame.uni.AllianceClass;
import org.quark.ogame.uni.BuildingType;
import org.quark.ogame.uni.CondensedFleet;
import org.quark.ogame.uni.CondensedMoon;
import org.quark.ogame.uni.CondensedPlanet;
import org.quark.ogame.uni.CondensedResearch;
import org.quark.ogame.uni.CondensedRockyBody;
import org.quark.ogame.uni.CondensedStationaryStructures;
import org.quark.ogame.uni.Coordinate;
import org.quark.ogame.uni.Holding;
import org.quark.ogame.uni.Moon;
import org.quark.ogame.uni.OGameEconomyRuleSet.FullProduction;
import org.quark.ogame.uni.Officers;
import org.quark.ogame.uni.Planet;
import org.quark.ogame.uni.PlannedFlight;
import org.quark.ogame.uni.PlannedUpgrade;
import org.quark.ogame.uni.Research;
import org.quark.ogame.uni.ResearchType;
import org.quark.ogame.uni.ResourceType;
import org.quark.ogame.uni.RockyBody;
import org.quark.ogame.uni.ShipyardItemType;
import org.quark.ogame.uni.Trade;
import org.quark.ogame.uni.Universe;

import com.google.common.reflect.TypeToken;

public class RoiAccount implements Account {
	static class AccountStatus {
		long theTime;
		long theHoldings;
		long theNextUpgradeCompletion;
		int planetCount;

		AccountStatus() {}

		AccountStatus(AccountStatus copy) {
			theTime = copy.theTime;
			theHoldings = copy.theHoldings;
			theNextUpgradeCompletion = copy.theNextUpgradeCompletion;
			planetCount = copy.planetCount;
		}
	}

	private final RoiSequenceGenerator theSequence;
	private final Account theTarget;
	private final RoiResearch theResearch;
	private final RoiValueSet<RoiPlanet> thePlanets;

	private BranchStack<AccountStatus> theStatus;
	private long theBranch;

	public RoiAccount(RoiSequenceGenerator sequence, Account target) {
		theSequence = sequence;
		theTarget = target;
		theResearch = new RoiResearch();
		thePlanets = new RoiValueSet<>(RoiPlanet.class, i -> new RoiPlanet(null, i + 1));
		for (Planet targetPlanet : target.getPlanets().getValues()) {
			thePlanets.getValues().add(new RoiPlanet(targetPlanet, 0));
		}
		theStatus = new BranchStack<AccountStatus>(new AccountStatus()) {
			@Override
			AccountStatus createValue() {
				return new AccountStatus();
			}

			@Override
			void copyValue(AccountStatus source, AccountStatus dest) {
				dest.theTime = source.theTime;
				dest.theHoldings = source.theHoldings;
				dest.theNextUpgradeCompletion = source.theNextUpgradeCompletion;
				dest.planetCount = source.planetCount;
			}
		};
		reset();
	}

	public Transaction branch() {
		theBranch++;
		return () -> {
			theBranch--;
			theStatus.pop(theBranch);
			while (thePlanets.getValues().size() > theStatus.get().planetCount) {
				thePlanets.getValues().removeLast();
			}
			theResearch.popBranch(theBranch);
			for (RoiPlanet planet : thePlanets.getValues()) {
				planet.popBranch(theBranch);
			}
		};
	}

	public long getTime() {
		return theStatus.get().theTime;
	}

	public long getHolding() {
		return theStatus.get().theHoldings;
	}

	public long getProduction() {
		long production = 0;
		for (RoiPlanet planet : roiPlanets()) {
			production += planet.getProductionValue();
		}
		return production;
	}

	/**
	 * Adds a temporary upgrade to this account
	 * 
	 * @param type
	 * @param body
	 * @param upgrade
	 */
	public void upgrade(AccountUpgradeType type, RoiRockyBody body, int upgrade) {
		if (type.research != null) {
			theResearch.upgrade(type.research, upgrade);
		} else {
			body.upgrade(type, upgrade);
		}
	}

	public RoiAccount completeUpgrades() {
		AccountStatus status = theStatus.getForUpdate(theBranch);
		long nextUpgradeTime = getNextUpgradeCompletion();
		while (nextUpgradeTime > theStatus.get().theTime) {
			advance(nextUpgradeTime);
			nextUpgradeTime = getNextUpgradeCompletion();
		}
		return this;
	}

	/**
	 * Applies all temporary upgrades in this account such that they are not removed with {@link #clear()}
	 * 
	 * @return This account
	 */
	public RoiAccount flush() {
		theStatus.flush(theBranch);
		theResearch.flush();
		for (RoiPlanet planet : thePlanets.getValues()) {
			planet.flush();
		}
		debugCheckUpgradeCompletion();
		return this;
	}

	/**
	 * Starts an account upgrade building on this account
	 * 
	 * @param type
	 * @param body
	 * @param amount
	 * @param duration
	 */
	public void start(AccountUpgradeType type, RoiRockyBody body, int amount, long duration) {
		if (duration < 0) {
			throw new IllegalArgumentException("" + duration);
		} else if (duration == 0) {
			upgrade(type, body, amount);
			return;
		}
		long finishTime = theStatus.get().theTime + duration;
		if (type.research != null) {
			theResearch.start(type.research, amount, finishTime);
		} else {
			body.start(type, amount, finishTime);
		}
		if (theStatus.get().theNextUpgradeCompletion == 0 || finishTime < theStatus.get().theNextUpgradeCompletion) {
			theStatus.getForUpdate(theBranch).theNextUpgradeCompletion = finishTime;
		}
		debugCheckUpgradeCompletion();
	}

	/** Resets all upgrades done to this account, including {@link #flush() flushed} ones */
	public void reset() {
		theStatus.reset();
		theResearch.reset();
		int maxPlanets = theSequence.getRules().economy().getMaxPlanets(this);
		theStatus.get().planetCount = maxPlanets;
		while (thePlanets.getValues().size() > theStatus.get().planetCount) {
			thePlanets.getValues().removeLast();
		}
		for (RoiPlanet planet : thePlanets.getValues()) {
			planet.reset();
		}
		while (thePlanets.getValues().size() < maxPlanets) {
			thePlanets.newValue();
		}
		debugCheckUpgradeCompletion();
	}

	public long getNextUpgradeCompletion() {
		return theStatus.get().theNextUpgradeCompletion;
	}

	public void advance(long time) {
		if (time < theStatus.get().theTime) {
			BreakpointHere.breakpoint();
		} else if (time == theStatus.get().theTime) {
			return;
		}
		AccountStatus status = theStatus.getForUpdate(theBranch);
		while (status.theNextUpgradeCompletion > 0 && time >= status.theNextUpgradeCompletion) {
			long next = status.theNextUpgradeCompletion;
			status.theHoldings += getProduction() * (next - status.theTime) / 3_600;
			status.theNextUpgradeCompletion = 0;
			theResearch.finishUpgradeTo(next);
			for (RoiPlanet planet : thePlanets.getValues()) {
				planet.finishUpgradeTo(next);
			}
			status.theTime = next;
		}
		if (time > status.theTime) {
			status.theHoldings += getProduction() * (time - status.theTime) / 3_600;
			status.theTime = time;
		}
		debugCheckUpgradeCompletion();
	}

	private void debugCheckUpgradeCompletion() {
		AccountStatus status = theStatus.get();
		if (status.theNextUpgradeCompletion > 0 && status.theTime >= status.theNextUpgradeCompletion) {
			BreakpointHere.breakpoint(); // DEBUG
		}
		if (status.theNextUpgradeCompletion < 0) {
			if (theResearch.getUpgradeCompletion() > 0) {
				BreakpointHere.breakpoint();
			}
			for (RoiPlanet planet : roiPlanets()) {
				if (planet.getUpgradeCompletion() > 0) {
					BreakpointHere.breakpoint();
				} else if (planet.getStationaryStructures().getUpgradeCompletion() > 0) {
					BreakpointHere.breakpoint();
				} else if (planet.getMoon().getUpgradeCompletion() > 0) {
					BreakpointHere.breakpoint();
				} else if (planet.getMoon().getStationaryStructures().getUpgradeCompletion() > 0) {
					BreakpointHere.breakpoint();
				}
			}
		} else {
			if (theResearch.getUpgradeCompletion() > 0 && theResearch.getUpgradeCompletion() < status.theNextUpgradeCompletion) {
				BreakpointHere.breakpoint();
			}
			for (RoiPlanet planet : roiPlanets()) {
				if (planet.getUpgradeCompletion() > 0 && planet.getUpgradeCompletion() < status.theNextUpgradeCompletion) {
					BreakpointHere.breakpoint();
				} else if (planet.getStationaryStructures().getUpgradeCompletion() > 0
					&& planet.getStationaryStructures().getUpgradeCompletion() < status.theNextUpgradeCompletion) {
					BreakpointHere.breakpoint();
				} else if (planet.getMoon().getUpgradeCompletion() > 0
					&& planet.getMoon().getUpgradeCompletion() < status.theNextUpgradeCompletion) {
					BreakpointHere.breakpoint();
				} else if (planet.getMoon().getStationaryStructures().getUpgradeCompletion() > 0
					&& planet.getMoon().getStationaryStructures().getUpgradeCompletion() < status.theNextUpgradeCompletion) {
					BreakpointHere.breakpoint();
				}
			}
		}
	}

	public void spend(long amount) {
		if (amount == 0) {
			return;
		}
		AccountStatus status = theStatus.getForUpdate(theBranch);
		if (amount <= status.theHoldings) {
			status.theHoldings -= amount;
			return;
		}
		long waitTime = Math.round((amount - status.theHoldings) * 3_600.0 / getProduction());
		while (status.theNextUpgradeCompletion > 0 && status.theTime + waitTime > status.theNextUpgradeCompletion) {
			advance(status.theNextUpgradeCompletion);
			waitTime = Math.round((amount - status.theHoldings) * 3_600.0 / getProduction());
		}
		advance(status.theTime + waitTime);
		status.theHoldings = 0;
	}

	@Override
	public Nameable setName(String name) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getName() {
		return theTarget.getName();
	}

	@Override
	public int getId() {
		return theTarget.getId();
	}

	@Override
	public Universe getUniverse() {
		return theTarget.getUniverse();
	}

	@Override
	public AccountClass getGameClass() {
		return theTarget.getGameClass();
	}

	@Override
	public Account setGameClass(AccountClass clazz) {
		throw new UnsupportedOperationException();
	}

	@Override
	public AllianceClass getAllianceClass() {
		return theTarget.getAllianceClass();
	}

	@Override
	public Account setAllianceClass(AllianceClass clazz) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Officers getOfficers() {
		return theTarget.getOfficers();
	}

	@Override
	public RoiResearch getResearch() {
		return theResearch;
	}

	@Override
	public RoiValueSet<Planet> getPlanets() {
		return (RoiValueSet<Planet>) (RoiValueSet<?>) thePlanets;
	}

	public ObservableCollection<RoiPlanet> roiPlanets() {
		return thePlanets.getValues();
	}

	@Override
	public SyncValueSet<Holding> getHoldings() {
		return SyncValueSet.empty(TypeTokens.get().of(Holding.class));
	}

	@Override
	public SyncValueSet<Trade> getTrades() {
		return SyncValueSet.empty(TypeTokens.get().of(Trade.class));
	}

	@Override
	public SyncValueSet<PlannedUpgrade> getPlannedUpgrades() {
		return SyncValueSet.empty(TypeTokens.get().of(PlannedUpgrade.class));
	}

	@Override
	public SyncValueSet<PlannedFlight> getPlannedFlights() {
		return SyncValueSet.empty(TypeTokens.get().of(PlannedFlight.class));
	}

	static <T> int getOrDefault(T key, QuickMap<T, Integer> map, ToIntFunction<T> def) {
		Integer value = map.getIfPresent(key);
		return value != null ? value.intValue() : def.applyAsInt(key);
	}

	public static class RoiValueSet<E> implements SyncValueSet<E> {
		private final ConfiguredValueType<E> theType;
		private final ObservableCollection<E> theValues;
		private final Function<Integer, E> theCreator;

		public RoiValueSet(Class<E> type, Function<Integer, E> creator) {
			theType = ConfiguredValueType.empty(TypeTokens.get().of(type));
			theValues = ObservableCollection.build(theType.getType()).safe(false).build();
			theCreator = creator;
		}

		@Override
		public ConfiguredValueType<E> getType() {
			return theType;
		}

		@Override
		public ObservableCollection<E> getValues() {
			return theValues;
		}

		@Override
		public <E2 extends E> SyncValueCreator<E, E2> create(TypeToken<E2> subType) {
			throw new UnsupportedOperationException();
		}

		public E newValue() {
			E newValue = theCreator.apply(theValues.size());
			theValues.add(newValue);
			return newValue;
		}
	}

	static abstract class BranchStack<T> {
		static class BranchStackElement<T> {
			BranchStackElement<T> parent;
			final long branch;
			T value;

			BranchStackElement(BranchStackElement<T> parent, long branch, T value) {
				this.parent = parent;
				this.branch = branch;
				this.value = value;
			}
		}

		BranchStackElement<T> theTop;

		BranchStack(T initValue) {
			theTop = new BranchStackElement(null, 0, initValue);
		}

		abstract T createValue();

		abstract void copyValue(T source, T dest);

		T get() {
			return theTop.value;
		}

		T getForUpdate(long branch) {
			return push(branch).value;
		}

		void set(long branch, T value) {
			push(branch).value = value;
		}

		private BranchStackElement<T> push(long branch) {
			if (theTop.branch != branch) {
				T newValue = createValue();
				copyValue(theTop.value, newValue);
				theTop = new BranchStackElement<>(theTop, branch, newValue);
			}
			return theTop;
		}

		void pop(long branch) {
			if (theTop.branch > branch) {
				theTop = theTop.parent;
			}
		}

		void flush(long branch) {
			if (branch > 0 && theTop.branch == branch) {
				copyValue(theTop.value, theTop.parent.value);
			}
		}

		void reset() {
			while (theTop.parent != null) {
				theTop = theTop.parent;
			}
		}
	}

	static class UpgradeItemStatus<T extends Enum<T>> {
		private final int[] theLevels;
		T upgrade;
		int amount;
		long completion;

		UpgradeItemStatus(int count) {
			theLevels = new int[count];
		}
	}

	public abstract class UpgradableAccountItem<T extends Enum<T>> {
		private final Class<T> theType;
		protected final BranchStack<UpgradeItemStatus<T>> theItemStatus;

		UpgradableAccountItem(Class<T> type) {
			theType = type;
			theItemStatus = new BranchStack<UpgradeItemStatus<T>>(createItemStatus()) {
				@Override
				UpgradeItemStatus<T> createValue() {
					return createItemStatus();
				}

				@Override
				void copyValue(UpgradeItemStatus<T> source, UpgradeItemStatus<T> dest) {
					copyItemStatus(source, dest);
				}
			};
		}

		protected Class<T> getType() {
			return theType;
		}

		protected UpgradeItemStatus<T> getItemStatus(boolean forUpgrade) {
			return forUpgrade ? theItemStatus.getForUpdate(theBranch) : theItemStatus.get();
		}

		protected UpgradeItemStatus<T> createItemStatus() {
			return new UpgradeItemStatus<T>(getType().getEnumConstants().length);
		}

		protected void copyItemStatus(UpgradeItemStatus<T> source, UpgradeItemStatus<T> dest) {
			System.arraycopy(source.theLevels, 0, dest.theLevels, 0, source.theLevels.length);
			dest.upgrade = source.upgrade;
			dest.amount = source.amount;
			dest.completion = source.completion;
		}

		void popBranch(long branch) {
			theItemStatus.pop(branch);
		}

		void reset() {
			theItemStatus.reset();
			theItemStatus.get().upgrade = null;
			theItemStatus.get().amount = 0;
			theItemStatus.get().completion = 0;
			for (int i = 0; i < theItemStatus.get().theLevels.length; i++) {
				theItemStatus.get().theLevels[i]=getResetValue(theType.getEnumConstants()[i]);
			}
		}

		protected abstract int getResetValue(T type);

		void flush() {
			theItemStatus.flush(theBranch);
		}

		void start(T type, int amount, long completionTime) {
			UpgradeItemStatus<T> status = theItemStatus.getForUpdate(theBranch);
			status.upgrade = type;
			status.amount = amount;
			status.completion = completionTime;
		}

		void finishUpgradeTo(long time) {
			AccountStatus acctStatus = theStatus.getForUpdate(theBranch);
			UpgradeItemStatus<T> status = theItemStatus.getForUpdate(theBranch);
			if (status.upgrade != null && time >= status.completion) {
				upgrade(status.upgrade, status.amount);
				status.upgrade = null;
				status.completion = 0;
			} else if (status.completion > 0
				&& (acctStatus.theNextUpgradeCompletion == 0 || status.completion < acctStatus.theNextUpgradeCompletion)) {
				acctStatus.theNextUpgradeCompletion = status.completion;
			}
		}

		public void upgrade(T type, int upgrade) {
			if (upgrade == 0) {
				return;
			}
			if (type == null) {
				BreakpointHere.breakpoint();
			}
			UpgradeItemStatus<T> status = theItemStatus.getForUpdate(theBranch);
			status.theLevels[type.ordinal()] += upgrade;
		}

		public T getCurrentUpgrade() {
			return theItemStatus.get().upgrade;
		}

		public long getUpgradeCompletion() {
			return theItemStatus.get().completion;
		}

		public int getUpgradeAmount() {
			return theItemStatus.get().amount;
		}

		protected int getLevel(T type) {
			return theItemStatus.get().theLevels[type.ordinal()];
		}

		protected UpgradableAccountItem<T> setLevel(T type, int level) {
			upgrade(type, level - getLevel(type));
			return this;
		}
	}

	public class RoiResearch extends UpgradableAccountItem<ResearchType> implements CondensedResearch {
		RoiResearch() {
			super(ResearchType.class);
			reset();
		}

		@Override
		protected int getResetValue(ResearchType type) {
			return theTarget.getResearch().getResearchLevel(type);
		}

		@Override
		public void upgrade(ResearchType type, int upgrade) {
			super.upgrade(type, upgrade);
			switch (type) {
			case Plasma:
				for (RoiPlanet planet : roiPlanets()) {
					planet.getItemStatus(true).productionDirty = true;
				}
				break;
			case Astrophysics:
				int maxPlanets = theSequence.getRules().economy().getMaxPlanets(RoiAccount.this);
				if (thePlanets.getValues().size() < maxPlanets) {
					theStatus.getForUpdate(theBranch).planetCount = maxPlanets;
					getPlanets().newValue();
				}
				break;
			default:
			}
		}

		@Override
		public Research setCurrentUpgrade(ResearchType activeResearch) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getResearchLevel(ResearchType type) {
			return getLevel(type);
		}

		@Override
		public void setResearchLevel(ResearchType type, int level) {
			setLevel(type, level);
		}
	}

	public abstract class RoiRockyBody extends UpgradableAccountItem<BuildingType> implements CondensedRockyBody {
		private final RockyBody theTargetBody;
		private final RoiShipSet theShips;

		RoiRockyBody(RockyBody target) {
			super(BuildingType.class);
			theTargetBody = target;
			theShips = new RoiShipSet(this);
		}

		public RockyBody getTargetBody() {
			return theTargetBody;
		}

		@Override
		protected int getResetValue(BuildingType type) {
			RockyBody body = getTargetBody();
			if (body == null) {
				return 0;
			}
			return body.getBuildingLevel(type);
		}

		@Override
		void reset() {
			super.reset();
			theShips.reset();
		}

		@Override
		void flush() {
			super.flush();
			theShips.flush();
		}

		@Override
		void popBranch(long branch) {
			super.popBranch(branch);
			theShips.popBranch(branch);
		}

		public void upgrade(AccountUpgradeType type, int upgrade) {
			if (type.building != null) {
				upgrade(type.building, upgrade);
			} else {
				theShips.upgrade(type.shipyardItem, upgrade);
			}
		}

		void start(AccountUpgradeType type, int amount, long completion) {
			if (type.shipyardItem != null) {
				theShips.start(type.shipyardItem, amount, completion);
			} else {
				start(type.building, amount, completion);
			}
		}

		@Override
		void finishUpgradeTo(long time) {
			super.finishUpgradeTo(time);
			theShips.finishUpgradeTo(time);
		}

		@Override
		public RoiShipSet getStationaryStructures() {
			return theShips;
		}

		@Override
		public RoiShipSet getStationedFleet() {
			return theShips;
		}

		@Override
		public Nameable setName(String name) {
			throw new UnsupportedOperationException();
		}

		@Override
		public RockyBody setCurrentUpgrade(BuildingType building) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getBuildingLevel(BuildingType type) {
			return getLevel(type);
		}

		@Override
		public CondensedRockyBody setBuildingLevel(BuildingType type, int buildingLevel) {
			setLevel(type, buildingLevel);
			return this;
		}
	}

	static class PlanetStatus extends UpgradeItemStatus<BuildingType> {
		FullProduction production;
		long productionValue;
		int fusionUtil;
		int crawlerUtil;
		boolean productionDirty;

		PlanetStatus() {
			super(BuildingType.values().length);
			productionDirty = true;
		}
	}

	public class RoiPlanet extends RoiRockyBody implements CondensedPlanet {
		private final int thePlanetIndex;
		private final RoiMoon theMoon;

		RoiPlanet(Planet target, int planetIndex) {
			super(target);
			thePlanetIndex = planetIndex;
			theMoon = new RoiMoon(target == null ? null : target.getMoon(), this);
			reset();
		}

		@Override
		public Planet getTargetBody() {
			return (Planet) super.getTargetBody();
		}

		@Override
		protected PlanetStatus getItemStatus(boolean forUpgrade) {
			return (PlanetStatus) super.getItemStatus(forUpgrade);
		}

		@Override
		protected PlanetStatus createItemStatus() {
			return new PlanetStatus();
		}

		@Override
		protected void copyItemStatus(UpgradeItemStatus<BuildingType> source, UpgradeItemStatus<BuildingType> dest) {
			super.copyItemStatus(source, dest);
			PlanetStatus pSrc = (PlanetStatus) source;
			PlanetStatus pDst = (PlanetStatus) dest;
			pDst.production = pSrc.production;
			pDst.productionValue = pSrc.productionValue;
			pDst.fusionUtil = pSrc.fusionUtil;
			pDst.crawlerUtil = pSrc.crawlerUtil;
			pDst.productionDirty = pSrc.productionDirty;
		}

		@Override
		public void upgrade(BuildingType type, int upgrade) {
			super.upgrade(type, upgrade);
			switch (type) {
			case MetalMine:
			case CrystalMine:
			case DeuteriumSynthesizer:
			case SolarPlant:
			case FusionReactor:
				getItemStatus(true).productionDirty = true;
				break;
			default:
			}
		}

		@Override
		void reset() {
			super.reset();
			theMoon.reset();
		}

		@Override
		void flush() {
			super.flush();
			theMoon.flush();
		}

		@Override
		void popBranch(long branch) {
			super.popBranch(branch);
			theMoon.popBranch(branch);
		}

		@Override
		void finishUpgradeTo(long time) {
			super.finishUpgradeTo(time);
			theMoon.finishUpgradeTo(time);
		}

		private void optimizeEnergy() {
			PlanetStatus status = getItemStatus(true);
			status.production = OGameUtils.optimizeEnergy(RoiAccount.this, this, //
				theSequence.getRules().economy());
			status.productionValue = Math.round(status.production.asCost().getMetalValue(getUniverse().getTradeRatios()));
			status.productionDirty = false;
		}

		public FullProduction getProduction() {
			PlanetStatus status = getItemStatus(false);
			if (status.productionDirty) {
				optimizeEnergy();
				status = getItemStatus(false);
			}
			return status.production;
		}

		public long getProductionValue() {
			PlanetStatus status = getItemStatus(false);
			if (status.productionDirty) {
				optimizeEnergy();
				status = getItemStatus(false);
			}
			return status.productionValue;
		}

		void dirtyProduction() {
			getItemStatus(true).productionDirty = true;
		}

		@Override
		public Account getAccount() {
			return RoiAccount.this;
		}

		@Override
		public Coordinate getCoordinates() {
			return getTargetBody() != null ? getTargetBody().getCoordinates() : new Coordinate() {
				@Override
				public int getGalaxy() {
					return 1;
				}

				@Override
				public Coordinate setGalaxy(int galaxy) {
					throw new UnsupportedOperationException();
				}

				@Override
				public int getSystem() {
					return 1;
				}

				@Override
				public Coordinate setSystem(int system) {
					throw new UnsupportedOperationException();
				}

				@Override
				public int getSlot() {
					return theSequence.getNewPlanetSlot().get();
				}

				@Override
				public Coordinate setSlot(int slot) {
					throw new UnsupportedOperationException();
				}
			};
		}

		@Override
		public int getBaseFields() {
			if (getTargetBody() != null) {
				return getTargetBody().getBaseFields();
			} else {
				return theSequence.getNewPlanetFields().get();
			}
		}

		@Override
		public Planet setBaseFields(int baseFields) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getMinimumTemperature() {
			return getTargetBody() != null ? getTargetBody().getMinimumTemperature() : theSequence.getNewPlanetTemp().get() - 20;
		}

		@Override
		public Planet setMinimumTemperature(int minTemp) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getMaximumTemperature() {
			return getTargetBody() != null ? getTargetBody().getMaximumTemperature() : theSequence.getNewPlanetTemp().get() + 20;
		}

		@Override
		public Planet setMaximumTemperature(int maxTemp) {
			throw new UnsupportedOperationException();
		}

		@Override
		public RoiMoon getMoon() {
			return theMoon;
		}

		@Override
		public int getMetalUtilization() {
			return 100;
		}

		@Override
		public Planet setMetalUtilization(int utilization) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getCrystalUtilization() {
			return 100;
		}

		@Override
		public Planet setCrystalUtilization(int utilization) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getDeuteriumUtilization() {
			return 100;
		}

		@Override
		public Planet setDeuteriumUtilization(int utilization) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getSolarPlantUtilization() {
			return 100;
		}

		@Override
		public Planet setSolarPlantUtilization(int utilization) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getFusionReactorUtilization() {
			return getItemStatus(false).fusionUtil;
		}

		@Override
		public Planet setFusionReactorUtilization(int utilization) {
			PlanetStatus status = getItemStatus(true);
			status.fusionUtil = utilization;
			status.productionDirty = true;
			return this;
		}

		@Override
		public int getSolarSatelliteUtilization() {
			return 100;
		}

		@Override
		public Planet setSolarSatelliteUtilization(int utilization) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getCrawlerUtilization() {
			return getItemStatus(false).crawlerUtil;
		}

		@Override
		public Planet setCrawlerUtilization(int utilization) {
			PlanetStatus status = getItemStatus(true);
			status.crawlerUtil = utilization;
			status.productionDirty = true;
			return this;
		}

		@Override
		public String getName() {
			return getTargetBody() != null ? getTargetBody().getName() : "Planet " + thePlanetIndex;
		}

		@Override
		public long getId() {
			return getTargetBody() != null ? getTargetBody().getId() : 1_000_000 + thePlanetIndex;
		}

		@Override
		public int getBonus(ResourceType type) {
			return 0;
		}

		@Override
		public Planet setBonus(ResourceType type, int level) {
			throw new UnsupportedOperationException();
		}

		@Override
		public RoiPlanet setName(String name) {
			super.setName(name);
			return this;
		}

		@Override
		public RoiPlanet setCurrentUpgrade(BuildingType building) {
			super.setCurrentUpgrade(building);
			return this;
		}

		@Override
		public RoiPlanet setBuildingLevel(BuildingType type, int buildingLevel) {
			super.setBuildingLevel(type, buildingLevel);
			return this;
		}

		@Override
		public String toString() {
			return getName();
		}
	}

	class RoiMoon extends RoiRockyBody implements CondensedMoon {
		private final RoiPlanet thePlanet;
		private final Moon theTargetMoon;

		RoiMoon(Moon target, RoiPlanet planet) {
			super(target);
			thePlanet = planet;
			theTargetMoon = target;
			reset();
		}

		@Override
		public Moon getTargetBody() {
			return (Moon) super.getTargetBody();
		}

		@Override
		public RoiPlanet getPlanet() {
			return thePlanet;
		}

		@Override
		public int getFieldBonus() {
			return 0;
		}

		@Override
		public Moon setFieldBonus(int fieldBonus) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getName() {
			return theTargetMoon != null ? theTargetMoon.getName() : thePlanet.getName() + " Moon";
		}

		@Override
		public RoiMoon setName(String name) {
			super.setName(name);
			return this;
		}

		@Override
		public RoiMoon setCurrentUpgrade(BuildingType building) {
			super.setCurrentUpgrade(building);
			return this;
		}

		@Override
		public RoiMoon setBuildingLevel(BuildingType type, int buildingLevel) {
			super.setBuildingLevel(type, buildingLevel);
			return this;
		}
	}

	class RoiShipSet extends UpgradableAccountItem<ShipyardItemType> implements CondensedStationaryStructures, CondensedFleet {
		private final RoiRockyBody theOwner;

		RoiShipSet(RoiRockyBody owner) {
			super(ShipyardItemType.class);
			theOwner = owner;
			reset();
		}

		@Override
		protected int getResetValue(ShipyardItemType type) {
			RockyBody body = theOwner.getTargetBody();
			if (body == null) {
				return 0;
			}
			return body.getStationedShips(type);
		}

		@Override
		public void upgrade(ShipyardItemType type, int upgrade) {
			super.upgrade(type, upgrade);
			if (theOwner instanceof RoiPlanet) {
				switch (type) {
				case SolarSatellite:
				case Crawler:
					((RoiPlanet) theOwner).dirtyProduction();
					break;
				default:
				}
			}
		}

		@Override
		public int getItems(ShipyardItemType type) {
			return getLevel(type);
		}

		@Override
		public RoiShipSet setItems(ShipyardItemType type, int number) {
			setLevel(type, number);
			return this;
		}
	}

	class RoiHolding implements Holding {
		private long theMetal;
		private long theCrystal;
		private long theDeuterium;

		RoiHolding() {
			reset();
		}

		void reset() {
			for (Holding holding : theTarget.getHoldings().getValues()) {
				theMetal += holding.getMetal();
				theCrystal += holding.getCrystal();
				theDeuterium += holding.getDeuterium();
			}
		}

		@Override
		public String getName() {
			return "__";
		}

		@Override
		public Nameable setName(String name) {
			throw new UnsupportedOperationException();
		}

		@Override
		public AccountUpgradeType getType() {
			return null;
		}

		@Override
		public Holding setType(AccountUpgradeType type) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getLevel() {
			return 0;
		}

		@Override
		public Holding setLevel(int level) {
			throw new UnsupportedOperationException();
		}

		@Override
		public long getMetal() {
			return theMetal;
		}

		@Override
		public Holding setMetal(long metal) {
			theMetal = metal;
			return this;
		}

		@Override
		public long getCrystal() {
			return theCrystal;
		}

		@Override
		public Holding setCrystal(long crystal) {
			theCrystal = crystal;
			return this;
		}

		@Override
		public long getDeuterium() {
			return theDeuterium;
		}

		@Override
		public Holding setDeuterium(long deuterium) {
			theDeuterium = deuterium;
			return this;
		}
	}
}
