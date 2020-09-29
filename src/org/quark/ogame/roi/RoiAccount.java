package org.quark.ogame.roi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import org.observe.collect.ObservableCollection;
import org.observe.config.ConfiguredValueType;
import org.observe.config.SyncValueCreator;
import org.observe.config.SyncValueSet;
import org.observe.util.TypeTokens;
import org.qommons.BreakpointHere;
import org.qommons.Nameable;
import org.qommons.collect.QuickSet.QuickMap;
import org.quark.ogame.OGameUtils;
import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.AccountClass;
import org.quark.ogame.uni.AccountUpgradeType;
import org.quark.ogame.uni.BuildingType;
import org.quark.ogame.uni.CondensedFleet;
import org.quark.ogame.uni.CondensedMoon;
import org.quark.ogame.uni.CondensedPlanet;
import org.quark.ogame.uni.CondensedResearch;
import org.quark.ogame.uni.CondensedRockyBody;
import org.quark.ogame.uni.CondensedStationaryStructures;
import org.quark.ogame.uni.Coordinate;
import org.quark.ogame.uni.Fleet;
import org.quark.ogame.uni.Holding;
import org.quark.ogame.uni.Moon;
import org.quark.ogame.uni.Officers;
import org.quark.ogame.uni.Planet;
import org.quark.ogame.uni.PlannedFlight;
import org.quark.ogame.uni.PlannedUpgrade;
import org.quark.ogame.uni.Research;
import org.quark.ogame.uni.ResearchType;
import org.quark.ogame.uni.ResourceType;
import org.quark.ogame.uni.RockyBody;
import org.quark.ogame.uni.ShipyardItemType;
import org.quark.ogame.uni.StationaryStructures;
import org.quark.ogame.uni.Trade;
import org.quark.ogame.uni.Universe;

import com.google.common.reflect.TypeToken;

public class RoiAccount implements Account {
	private final RoiSequence theSequence;
	private final Account theTarget;
	private final RoiResearch theResearch;
	private final RoiValueSet<RoiPlanet> thePlanets;

	private long theTotalBaseProduction;
	private long theTotalUpgradedProduction;
	private long theTime;
	private long theHoldings;
	private long theNextUpgradeCompletion;

	public RoiAccount(RoiSequence sequence, Account target) {
		theSequence = sequence;
		theTarget = target;
		theResearch = new RoiResearch();
		thePlanets = new RoiValueSet<>(RoiPlanet.class, i -> new RoiPlanet(null, i + 1));
		for (Planet targetPlanet : target.getPlanets().getValues()) {
			thePlanets.getValues().add(new RoiPlanet(targetPlanet, 0));
		}
		reset();
	}

	public long getTime() {
		return theTime;
	}

	public long getHolding() {
		return theHoldings;
	}

	public long getProduction() {
		for (RoiPlanet planet : RoiPlanets()) {
			planet.getProduction(); // Flush production
		}
		return theTotalUpgradedProduction;
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

	/** Removes all temporary upgrades from this account */
	public void clear() {
		theTotalUpgradedProduction = theTotalBaseProduction;
		theResearch.clear();
		while (!thePlanets.getValues().isEmpty() && !thePlanets.getValues().getLast().isFlushed) {
			thePlanets.getValues().removeLast();
		}
		for (RoiPlanet planet : thePlanets.getValues()) {
			planet.clear();
		}
	}

	/**
	 * Applies all temporary upgrades in this account such that they are not removed with {@link #clear()}
	 * 
	 * @param sequence
	 */
	public void flush() {
		getProduction(); // Flush production
		theTotalBaseProduction = theTotalUpgradedProduction;
		theResearch.flush();
		for (RoiPlanet planet : thePlanets.getValues()) {
			planet.flush();
		}
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
		long finishTime = theTime + duration;
		if (type.research != null) {
			theResearch.start(type.research, amount, finishTime);
		} else {
			body.start(type, amount, finishTime);
		}
		if (theNextUpgradeCompletion < 0 || finishTime < theNextUpgradeCompletion) {
			theNextUpgradeCompletion = finishTime;
		}
	}

	/** Resets all upgrades done to this account, including {@link #flush(Consumer) flushed} ones */
	public void reset() {
		theResearch.reset();
		while (thePlanets.getValues().size() > 0 && thePlanets.getValues().getLast().getTargetBody() == null) {
			thePlanets.getValues().removeLast();
		}
		for (RoiPlanet planet : thePlanets.getValues()) {
			planet.reset();
		}
		theHoldings = 0;
		theTime = 0;
		theNextUpgradeCompletion = -1;
		theTotalBaseProduction = 0;
		for (RoiPlanet p : RoiPlanets()) {
			theTotalBaseProduction += p.getProduction();
		}
		theTotalUpgradedProduction = theTotalBaseProduction;
	}

	public long getNextUpgradeCompletion() {
		return theNextUpgradeCompletion;
	}

	public void advance(long time) {
		if (time < theTime) {
			BreakpointHere.breakpoint();
		}
		while (theNextUpgradeCompletion >= 0 && time >= theNextUpgradeCompletion) {
			long next = theNextUpgradeCompletion;
			theHoldings += theTotalUpgradedProduction * (next - theTime) / 3_600_000;
			finishNextUpgrades();
			theTime = next;
		}
		theHoldings += theTotalUpgradedProduction * (time - theTime) / 3_600_000;
		theTime = time;
	}

	public void spend(long amount) {
		if (theHoldings > amount) {
			theHoldings -= amount;
			return;
		}
		long waitTime = theTime + Math.round(amount * 3_600.0 / theTotalUpgradedProduction);
		while (theNextUpgradeCompletion >= 0 && waitTime >= theNextUpgradeCompletion) {
			long next = theNextUpgradeCompletion;
			theHoldings += theTotalUpgradedProduction * (next - theTime) / 3_600_000;
			finishNextUpgrades();
			theTime = next;
			waitTime = theTime + Math.round(amount * 3_600.0 / theTotalUpgradedProduction);
		}
		theHoldings = 0; // Spend the amount
		if (waitTime < theTime) {
			BreakpointHere.breakpoint();
		}
		theTime = waitTime;
	}

	private void finishNextUpgrades() {
		long nextUpgrade = theNextUpgradeCompletion;
		if (nextUpgrade < 0) {
			return;
		}
		theNextUpgradeCompletion = -1;
		theResearch.finishUpgradeTo(nextUpgrade);
		for (RoiPlanet planet : thePlanets.getValues()) {
			planet.finishUpgradesTo(nextUpgrade);
		}
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

	public ObservableCollection<RoiPlanet> RoiPlanets() {
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

	public class RoiResearch implements CondensedResearch {
		private final int[] theResearch;
		private final Map<ResearchType, Integer> theUpgrades;
		private ResearchType theCurrentUpgrade;
		private int theUpgradeAmount;
		private long theUpgradeCompletion;

		RoiResearch() {
			theResearch = new int[ResearchType.values().length];
			theUpgrades = new LinkedHashMap<>();
			reset();
		}

		void reset() {
			for (ResearchType r : ResearchType.values()) {
				theResearch[r.ordinal()] = theTarget.getResearch().getResearchLevel(r);
			}
			theCurrentUpgrade = theTarget.getResearch().getCurrentUpgrade();
			clear();
		}

		void clear() {
			theUpgrades.clear();
		}

		void flush() {
			for (Map.Entry<ResearchType, Integer> upgrade : theUpgrades.entrySet()) {
				theResearch[upgrade.getKey().ordinal()] += upgrade.getValue();
			}
			theUpgrades.clear();
		}

		void start(ResearchType type, int amount, long completionTime) {
			theCurrentUpgrade = type;
			theUpgradeAmount = amount;
			theUpgradeCompletion = completionTime;
		}

		void finishUpgradeTo(long time) {
			if (time <= theUpgradeCompletion) {
				upgrade(theCurrentUpgrade, theUpgradeAmount);
				theCurrentUpgrade = null;
				theUpgradeCompletion = 0;
			}
		}

		public void upgrade(ResearchType type, int upgrade) {
			theUpgrades.compute(type, (__, current) -> current == null ? upgrade : current + upgrade);
			switch (type) {
			case Plasma:
				for (RoiPlanet planet : RoiPlanets()) {
					planet.isProductionDirty = true;
				}
				break;
			default:
			}
		}

		@Override
		public ResearchType getCurrentUpgrade() {
			return theCurrentUpgrade;
		}

		public long getUpgradeCompletion() {
			return theUpgradeCompletion;
		}

		@Override
		public Research setCurrentUpgrade(ResearchType activeResearch) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getResearchLevel(ResearchType type) {
			return theResearch[type.ordinal()] + theUpgrades.getOrDefault(type, 0);
		}

		@Override
		public void setResearchLevel(ResearchType type, int level) {
			theResearch[type.ordinal()] = level;
		}
	}

	public abstract class RoiRockyBody implements CondensedRockyBody {
		private final RockyBody theTargetBody;
		private final int[] theBuildings;
		private final Map<BuildingType, Integer> theUpgrades;
		private final RoiShipSet theShips;
		private BuildingType theCurrentBuildingUpgrade;
		private int theUpgradeAmount;
		private long theUpgradeCompletion;

		RoiRockyBody(RockyBody target) {
			theTargetBody = target;
			theBuildings = new int[BuildingType.values().length];
			theUpgrades = new LinkedHashMap<>();
			if (target != null) {
				for (BuildingType building : BuildingType.values()) {
					theBuildings[building.ordinal()] = target.getBuildingLevel(building);
				}
				theCurrentBuildingUpgrade = target.getCurrentUpgrade();
			}
			theShips = new RoiShipSet(this);
			reset();
		}

		public RockyBody getTargetBody() {
			return theTargetBody;
		}

		void reset() {
			if (theTargetBody != null) {
				for (BuildingType building : BuildingType.values()) {
					theBuildings[building.ordinal()] = theTargetBody.getBuildingLevel(building);
				}
				theCurrentBuildingUpgrade = theTargetBody.getCurrentUpgrade();
			}
			theShips.reset(theTargetBody == null ? null : theTargetBody.getStationaryStructures(),
				theTargetBody == null ? null : theTargetBody.getStationedFleet());
			clear();
		}

		void flush() {
			for (Map.Entry<BuildingType, Integer> upgrade : theUpgrades.entrySet()) {
				theBuildings[upgrade.getKey().ordinal()] += upgrade.getValue();
			}
			theUpgrades.clear();
			theShips.flush();
		}

		void clear() {
			theUpgrades.clear();
			theShips.clear();
		}

		public void upgrade(AccountUpgradeType type, int upgrade) {
			if (type.building != null) {
				upgrade(type.building, upgrade);
			} else {
				theShips.upgrade(type.shipyardItem, upgrade);
			}
		}

		void upgrade(BuildingType type, int upgrade) {
			theUpgrades.compute(type, (__, current) -> current == null ? upgrade : current + upgrade);
		}

		void start(AccountUpgradeType type, int amount, long completionTime) {
			if (type.building != null) {
				theCurrentBuildingUpgrade = type.building;
				theUpgradeAmount = amount;
				theUpgradeCompletion = completionTime;
			} else {
				theShips.start(type.shipyardItem, amount, completionTime);
			}
		}

		void finishUpgradesTo(long time) {
			if (time <= theUpgradeCompletion) {
				upgrade(theCurrentBuildingUpgrade, theUpgradeAmount);
				theCurrentBuildingUpgrade = null;
				theUpgradeCompletion = 0;
			}
			theShips.finishUpgradeTo(time);
		}

		@Override
		public BuildingType getCurrentUpgrade() {
			return theCurrentBuildingUpgrade;
		}

		@Override
		public RockyBody setCurrentUpgrade(BuildingType building) {
			theCurrentBuildingUpgrade = building;
			return this;
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
		public int getBuildingLevel(BuildingType type) {
			return theBuildings[type.ordinal()] + theUpgrades.getOrDefault(type, 0);
		}

		public long getUpgradeCompletion() {
			return theUpgradeCompletion;
		}

		@Override
		public CondensedRockyBody setBuildingLevel(BuildingType type, int buildingLevel) {
			theBuildings[type.ordinal()] = buildingLevel;
			return this;
		}
	}

	public class RoiPlanet extends RoiRockyBody implements CondensedPlanet {
		private final int thePlanetIndex;
		private final RoiMoon theMoon;

		private long theBaseProduction;
		private long theUpgradedProduction;
		private boolean isProductionDirty;
		private int theFusionUtil;
		private int theCrawlerUtil;
		private boolean isFlushed;

		RoiPlanet(Planet target, int planetIndex) {
			super(target);
			thePlanetIndex = planetIndex;
			theMoon = new RoiMoon(target == null ? null : target.getMoon(), this);
			isProductionDirty = true;
			isFlushed = target != null;
		}

		@Override
		public Planet getTargetBody() {
			return (Planet) super.getTargetBody();
		}

		@Override
		void clear() {
			theUpgradedProduction = theBaseProduction;
			super.clear();
			if (theMoon != null) {
				theMoon.clear();
			}
			resetProduction();
		}

		@Override
		void upgrade(BuildingType type, int upgrade) {
			super.upgrade(type, upgrade);
			if (type == null) {
				BreakpointHere.breakpoint();
			}
			switch (type) {
			case MetalMine:
			case CrystalMine:
			case DeuteriumSynthesizer:
			case SolarPlant:
			case FusionReactor:
				isProductionDirty = true;
				break;
			default:
			}
		}

		@Override
		void reset() {
			super.reset();
			theFusionUtil = getTargetBody() != null ? getTargetBody().getFusionReactorUtilization() : 100;
			if (theMoon != null) {
				theMoon.reset();
			}
			resetProduction();
		}

		@Override
		void flush() {
			theBaseProduction = theUpgradedProduction;
			isFlushed = true;
			super.flush();
			theMoon.flush();
		}

		private void optimizeEnergy() {
			long oldProduction = theUpgradedProduction;
			theUpgradedProduction = Math.round(OGameUtils.optimizeEnergy(RoiAccount.this, this, theSequence.getRules().economy()));
			theTotalUpgradedProduction += theUpgradedProduction - oldProduction;
		}

		void resetProduction() {
			theBaseProduction = theUpgradedProduction = Math.round(theSequence.getRules().economy()
				.getFullProduction(RoiAccount.this, this).asCost().getMetalValue(theTarget.getUniverse().getTradeRatios()));
		}

		public long getProduction() {
			if (isProductionDirty) {
				optimizeEnergy();
				isProductionDirty = false;
			}
			return theUpgradedProduction;
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
					return theSequence.getNewPlanetSlot();
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
				return theSequence.getNewPlanetFields();
			}
		}

		@Override
		public Planet setBaseFields(int baseFields) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getMinimumTemperature() {
			return getTargetBody() != null ? getTargetBody().getMinimumTemperature() : theSequence.getNewPlanetTemp() - 20;
		}

		@Override
		public Planet setMinimumTemperature(int minTemp) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getMaximumTemperature() {
			return getTargetBody() != null ? getTargetBody().getMaximumTemperature() : theSequence.getNewPlanetTemp() + 20;
		}

		@Override
		public Planet setMaximumTemperature(int maxTemp) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Moon getMoon() {
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
			return theFusionUtil;
		}

		@Override
		public Planet setFusionReactorUtilization(int utilization) {
			theFusionUtil = utilization;
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
			return theCrawlerUtil;
		}

		@Override
		public Planet setCrawlerUtilization(int utilization) {
			theCrawlerUtil = utilization;
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

	class RoiShipSet implements CondensedStationaryStructures, CondensedFleet {
		private final RoiRockyBody theOwner;
		private final int[] theShips;
		private final Map<ShipyardItemType, Integer> theUpgrades;
		private ShipyardItemType theCurrentUpgrade;
		private int theUpgradeAmount;
		private long theUpgradeCompletion;

		RoiShipSet(RoiRockyBody owner) {
			theOwner = owner;
			theShips = new int[ShipyardItemType.values().length];
			theUpgrades = new LinkedHashMap<>();
		}

		void reset(StationaryStructures structures, Fleet fleet) {
			if (structures != null || fleet != null) {
				for (ShipyardItemType ship : ShipyardItemType.values()) {
					if (ship.mobile && fleet != null) {
						theShips[ship.ordinal()] = fleet.getItems(ship);
					}
					if (!ship.mobile && structures != null) {
						theShips[ship.ordinal()] = structures.getItems(ship);
					}
				}
			}
			clear();
		}

		void clear() {
			theUpgrades.clear();
		}

		void flush() {
			for (Map.Entry<ShipyardItemType, Integer> upgrade : theUpgrades.entrySet()) {
				theShips[upgrade.getKey().ordinal()] += upgrade.getValue();
			}
			theUpgrades.clear();
		}

		void upgrade(ShipyardItemType type, int upgrade) {
			theUpgrades.compute(type, (__, current) -> current == null ? upgrade : current + upgrade);
			if (theOwner instanceof RoiPlanet) {
				switch (type) {
				case SolarSatellite:
				case Crawler:
					((RoiPlanet) theOwner).isProductionDirty = true;
					break;
				default:
				}
			}
		}

		public ShipyardItemType getCurrentUpgrade() {
			return theCurrentUpgrade;
		}

		public long getUpgradeCompletion() {
			return theUpgradeCompletion;
		}

		void start(ShipyardItemType type, int amount, long completionTime) {
			theCurrentUpgrade = type;
			theUpgradeAmount = amount;
			theUpgradeCompletion = completionTime;
		}

		void finishUpgradeTo(long time) {
			if (time <= theUpgradeCompletion) {
				theUpgradeCompletion = 0;
				upgrade(theCurrentUpgrade, theUpgradeAmount);
				theCurrentUpgrade = null;
			}
		}

		@Override
		public int getItems(ShipyardItemType type) {
			return theShips[type.ordinal()] + theUpgrades.getOrDefault(type, 0);
		}

		@Override
		public RoiShipSet setItems(ShipyardItemType type, int number) {
			theShips[type.ordinal()] = number;
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
