package org.quark.ogame.uni;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.observe.collect.ObservableCollection;
import org.observe.config.ConfiguredValueType;
import org.observe.config.SyncValueCreator;
import org.observe.config.SyncValueSet;
import org.observe.util.TypeTokens;
import org.qommons.Nameable;
import org.quark.ogame.OGameUtils;

import com.google.common.reflect.TypeToken;

public class UpgradeAccount implements Account {
	private final Account theWrapped;
	private final UpgradeResearch theResearch;
	private volatile UpgradePlanetSet thePlanets;

	public UpgradeAccount(Account wrap) {
		theWrapped = wrap;
		theResearch = new UpgradeResearch();
	}

	public Account getWrapped() {
		return theWrapped;
	}

	public UpgradeAccount withUpgrade(PlannedUpgrade upgrade) {
		if (upgrade.getType().research != null) {
			theResearch.withUpgrade(upgrade);
		} else {
			UpgradePlanet upgradePlanet = getPlanets()//
				.getPlanet(//
					upgrade.getPlanet());
			if (upgradePlanet != null) {
				upgradePlanet.withUpgrade(upgrade);
			}
		}
		return this;
	}

	public UpgradeAccount withoutUpgrade(PlannedUpgrade upgrade) {
		if (upgrade.getType().research != null) {
			theResearch.withoutUpgrade(upgrade);
		} else {
			UpgradePlanet planet = getPlanets().getPlanet(upgrade.getPlanet());
			if (planet != null) {
				planet.withoutUpgrade(upgrade);
			}
		}
		return this;
	}

	public UpgradeAccount clearUpgrades() {
		theResearch.clearUpgrades();
		thePlanets.clearUpgrades();
		return this;
	}

	@Override
	public Nameable setName(String name) {
		return this;
	}

	@Override
	public String getName() {
		return theWrapped.getName();
	}

	@Override
	public int getId() {
		return theWrapped.getId();
	}

	@Override
	public Universe getUniverse() {
		return theWrapped.getUniverse();
	}

	@Override
	public AccountClass getGameClass() {
		return theWrapped.getGameClass();
	}

	@Override
	public Account setGameClass(AccountClass clazz) {
		return this;
	}

	@Override
	public Officers getOfficers() {
		return theWrapped.getOfficers();
	}

	@Override
	public Research getResearch() {
		return theResearch;
	}

	@Override
	public UpgradePlanetSet getPlanets() {
		UpgradePlanetSet planets = thePlanets;
		if (planets == null) {
			synchronized (this) {
				planets = thePlanets;
				if (planets == null) {
					thePlanets = planets = new UpgradePlanetSet();
				}
			}
		}
		return thePlanets;
	}

	@Override
	public SyncValueSet<Holding> getHoldings() {
		return theWrapped.getHoldings();
	}

	@Override
	public SyncValueSet<Trade> getTrades() {
		return theWrapped.getTrades();
	}

	@Override
	public SyncValueSet<PlannedUpgrade> getPlannedUpgrades() {
		return theWrapped.getPlannedUpgrades();
	}

	@Override
	public SyncValueSet<PlannedFlight> getPlannedFlights() {
		return theWrapped.getPlannedFlights();
	}

	@Override
	public String toString() {
		return theWrapped.toString();
	}

	public class UpgradePlanetSet implements SyncValueSet<Planet> {
		private final Map<Long, UpgradePlanet> theUpgradePlanets;
		private final ObservableCollection<UpgradePlanet> thePlanetCollection;

		UpgradePlanetSet() {
			theUpgradePlanets = new ConcurrentHashMap<>();
			// thePlanetCollection = theWrapped.getPlanets().getValues().flow()//
			// .map(TypeTokens.get().of(UpgradePlanet.class), p -> {
			// return theUpgradePlanets.computeIfAbsent(p.getId(), __ -> new UpgradePlanet(p));
			// }, opts -> opts.cache(false)).collectPassive();
			thePlanetCollection = theWrapped.getPlanets().getValues().flow()//
				.transform(TypeTokens.get().of(UpgradePlanet.class),
					c -> c.cache(false).map(p -> {
						long planetId = p.getId();
						return theUpgradePlanets.computeIfAbsent(planetId, __ -> new UpgradePlanet(p));
					}).replaceSourceWith((p, cv) -> cv.getCurrentSource()))// Not even sure I need this since it'll just be updates
				.collectPassive();
		}

		void clearUpgrades() {
			for (UpgradePlanet planet : thePlanetCollection) {
				planet.clearUpgrades();
			}
		}

		@Override
		public ConfiguredValueType<Planet> getType() {
			return theWrapped.getPlanets().getType();
		}

		@Override
		public ObservableCollection<Planet> getValues() {
			return (ObservableCollection<Planet>) (ObservableCollection<?>) thePlanetCollection;
		}

		public UpgradePlanet getUpgradePlanet(Planet wrappedPlanet) {
			if (wrappedPlanet.getAccount() == UpgradeAccount.this) {
				return (UpgradePlanet) wrappedPlanet;
			} else if (wrappedPlanet.getAccount() == theWrapped) {
				return theUpgradePlanets.computeIfAbsent(wrappedPlanet.getId(), __ -> new UpgradePlanet(wrappedPlanet));
			} else {
				// throw new IllegalArgumentException("Unrecognized planet: " + wrappedPlanet);
				return null;
			}
		}

		public UpgradePlanet getPlanet(long planetId) {
			for (Planet p : theWrapped.getPlanets().getValues()) {
				if (p.getId() == planetId) {
					return getUpgradePlanet(p);
				}
			}
			return null;
		}

		@Override
		public <E2 extends Planet> SyncValueCreator<Planet, E2> create(TypeToken<E2> subType) {
			throw new UnsupportedOperationException("Can't create planets here");
		}
	}

	class UpgradeResearch implements CondensedResearch {
		private Map<ResearchType, List<PlannedUpgrade>> thePlannedUpgrades;

		void withUpgrade(PlannedUpgrade upgrade) {
			if (thePlannedUpgrades == null) {
				thePlannedUpgrades = new HashMap<>();
			}
			List<PlannedUpgrade> upgrades = thePlannedUpgrades.computeIfAbsent(upgrade.getType().research, __ -> new LinkedList<>());
			if (!upgrades.contains(upgrade)) {
				upgrades.add(upgrade);
			}
		}

		void withoutUpgrade(PlannedUpgrade upgrade) {
			if (thePlannedUpgrades == null) {
				return;
			}
			thePlannedUpgrades.getOrDefault(upgrade.getType().research, Collections.emptyList()).remove(upgrade);
		}

		void clearUpgrades() {
			if (thePlannedUpgrades != null) {
				thePlannedUpgrades.clear();
			}
		}

		@Override
		public ResearchType getCurrentUpgrade() {
			return null;
		}

		@Override
		public Research setCurrentUpgrade(ResearchType activeResearch) {
			return null;
		}

		@Override
		public int getResearchLevel(ResearchType type) {
			int level = theWrapped.getResearch().getResearchLevel(type);
			if (thePlannedUpgrades != null) {
				for (PlannedUpgrade upgrade : thePlannedUpgrades.getOrDefault(type, Collections.emptyList())) {
					level += upgrade.getQuantity();
				}
			}
			return level;
		}

		@Override
		public void setResearchLevel(ResearchType type, int level) {}
	}

	public class UpgradePlanet extends UpgradeRockyBody implements CondensedPlanet {
		private UpgradeMoon theMoon;
		private int theCrawlerUtil;
		private int theFusionUtil;

		public UpgradePlanet(Planet wrapped) {
			super(wrapped);
			theFusionUtil = -1;
			theCrawlerUtil = -1;
		}

		public void optimizeEnergy(OGameEconomyRuleSet eco) {
			OGameUtils.optimizeEnergy(UpgradeAccount.this, this, eco);
		}

		@Override
		public Planet getWrapped() {
			return (Planet) super.getWrapped();
		}

		@Override
		void withUpgrade(PlannedUpgrade upgrade) {
			if (upgrade.isMoon()) {
				getMoon().withUpgrade(upgrade);
			} else {
				super.withUpgrade(upgrade);
			}
		}

		@Override
		void withoutUpgrade(PlannedUpgrade upgrade) {
			if (upgrade.isMoon()) {
				getMoon().withoutUpgrade(upgrade);
			} else {
				super.withoutUpgrade(upgrade);
			}
		}

		@Override
		void clearUpgrades() {
			super.clearUpgrades();
			if (theMoon != null) {
				theMoon.clearUpgrades();
			}
		}

		@Override
		public long getId() {
			return getWrapped().getId();
		}

		@Override
		public UpgradeAccount getAccount() {
			return UpgradeAccount.this;
		}

		@Override
		public Coordinate getCoordinates() {
			return getWrapped().getCoordinates();
		}

		@Override
		public int getBaseFields() {
			return getWrapped().getBaseFields();
		}

		@Override
		public Planet setBaseFields(int baseFields) {
			return this;
		}

		@Override
		public int getMinimumTemperature() {
			return getWrapped().getMinimumTemperature();
		}

		@Override
		public Planet setMinimumTemperature(int minTemp) {
			return this;
		}

		@Override
		public int getMaximumTemperature() {
			return getWrapped().getMaximumTemperature();
		}

		@Override
		public Planet setMaximumTemperature(int maxTemp) {
			return this;
		}

		@Override
		public UpgradeMoon getMoon() {
			if (theMoon == null) {
				theMoon = new UpgradeMoon(getWrapped().getMoon());
			}
			return theMoon;
		}

		@Override
		public int getMetalUtilization() {
			return getWrapped().getMetalUtilization();
		}

		@Override
		public Planet setMetalUtilization(int utilization) {
			return this;
		}

		@Override
		public int getCrystalUtilization() {
			return getWrapped().getCrystalUtilization();
		}

		@Override
		public Planet setCrystalUtilization(int utilization) {
			return this;
		}

		@Override
		public int getDeuteriumUtilization() {
			return getWrapped().getDeuteriumUtilization();
		}

		@Override
		public Planet setDeuteriumUtilization(int utilization) {
			return this;
		}

		@Override
		public int getSolarPlantUtilization() {
			return 100;
		}

		@Override
		public Planet setSolarPlantUtilization(int utilization) {
			return this;
		}

		@Override
		public int getFusionReactorUtilization() {
			if (theFusionUtil >= 0) {
				return theFusionUtil;
			}
			return getWrapped().getFusionReactorUtilization();
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
			return this;
		}

		@Override
		public int getCrawlerUtilization() {
			if (theCrawlerUtil >= 0) {
				return theCrawlerUtil;
			}
			return getWrapped().getCrawlerUtilization();
		}

		@Override
		public Planet setCrawlerUtilization(int utilization) {
			theCrawlerUtil = utilization;
			return this;
		}

		@Override
		public int getBonus(ResourceType type) {
			return getWrapped().getBonus(type);
		}

		@Override
		public Planet setBonus(ResourceType type, int level) {
			return this;
		}

		@Override
		public UpgradePlanet setName(String name) {
			return this;
		}

		@Override
		public UpgradePlanet setCurrentUpgrade(BuildingType building) {
			return this;
		}

		@Override
		public UpgradePlanet setBuildingLevel(BuildingType type, int buildingLevel) {
			return this;
		}

		@Override
		public String toString() {
			return theWrapped.toString() + "." + getWrapped().toString();
		}

		public class UpgradeMoon extends UpgradeRockyBody implements CondensedMoon {
			public UpgradeMoon(Moon wrapped) {
				super(wrapped);
			}

			@Override
			public Moon getWrapped() {
				return (Moon) super.getWrapped();
			}

			@Override
			public UpgradePlanet getPlanet() {
				return UpgradePlanet.this;
			}

			@Override
			public int getFieldBonus() {
				return getWrapped().getFieldBonus();
			}

			@Override
			public Moon setFieldBonus(int fieldBonus) {
				return this;
			}

			@Override
			public UpgradeMoon setName(String name) {
				return this;
			}

			@Override
			public UpgradeMoon setCurrentUpgrade(BuildingType building) {
				return this;
			}

			@Override
			public UpgradeMoon setBuildingLevel(BuildingType type, int buildingLevel) {
				return this;
			}
		}
	}

	public static abstract class UpgradeRockyBody implements CondensedRockyBody {
		private final RockyBody theWrappedBody;
		private Map<BuildingType, List<PlannedUpgrade>> thePlannedUpgrades;
		private UpgradeShips theStructures;

		UpgradeRockyBody(RockyBody wrappedBody) {
			theWrappedBody = wrappedBody;
		}

		public RockyBody getWrapped() {
			return theWrappedBody;
		}

		void withUpgrade(PlannedUpgrade upgrade) {
			if (upgrade.getType().shipyardItem != null) {
				getStationaryStructures().withUpgrade(upgrade);
				return;
			}
			if (thePlannedUpgrades == null) {
				thePlannedUpgrades = new HashMap<>();
			}
			List<PlannedUpgrade> upgrades = thePlannedUpgrades.computeIfAbsent(upgrade.getType().building, __ -> new LinkedList<>());
			if (!upgrades.contains(upgrade)) {
				upgrades.add(upgrade);
			}
		}

		void withoutUpgrade(PlannedUpgrade upgrade) {
			if (upgrade.getType().shipyardItem != null) {
				if (theStructures != null) {
					theStructures.withoutUpgrade(upgrade);
				}
			} else if (thePlannedUpgrades != null) {
				thePlannedUpgrades.getOrDefault(upgrade.getType().building, Collections.emptyList()).remove(upgrade);
			}
		}

		void clearUpgrades() {
			if (thePlannedUpgrades != null) {
				thePlannedUpgrades.clear();
			}
			if (theStructures != null) {
				theStructures.clearUpgrades();
			}
		}

		@Override
		public String getName() {
			return theWrappedBody.getName();
		}

		@Override
		public BuildingType getCurrentUpgrade() {
			return theWrappedBody.getCurrentUpgrade();
		}

		@Override
		public RockyBody setCurrentUpgrade(BuildingType building) {
			return this;
		}

		@Override
		public UpgradeShips getStationaryStructures() {
			if (theStructures == null) {
				theStructures = new UpgradeShips();
			}
			return theStructures;
		}

		@Override
		public UpgradeShips getStationedFleet() {
			if (theStructures == null) {
				theStructures = new UpgradeShips();
			}
			return theStructures;
		}

		@Override
		public Nameable setName(String name) {
			return this;
		}

		@Override
		public int getBuildingLevel(BuildingType type) {
			int level = theWrappedBody.getBuildingLevel(type);
			if (thePlannedUpgrades != null) {
				for (PlannedUpgrade upgrade : thePlannedUpgrades.getOrDefault(type, Collections.emptyList())) {
					level += upgrade.getQuantity();
				}
			}
			return level;
		}

		@Override
		public CondensedRockyBody setBuildingLevel(BuildingType type, int buildingLevel) {
			return this;
		}

		public class UpgradeShips implements CondensedStationaryStructures, CondensedFleet {
			private Map<ShipyardItemType, List<PlannedUpgrade>> thePlannedUpgrades;

			UpgradeShips() {}

			void withUpgrade(PlannedUpgrade upgrade) {
				if (thePlannedUpgrades == null) {
					thePlannedUpgrades = new HashMap<>();
				}
				List<PlannedUpgrade> upgrades = thePlannedUpgrades.computeIfAbsent(upgrade.getType().shipyardItem,
					__ -> new LinkedList<>());
				if (!upgrades.contains(upgrade)) {
					upgrades.add(upgrade);
				}
			}

			void withoutUpgrade(PlannedUpgrade upgrade) {
				if (thePlannedUpgrades == null) {
					return;
				}
				thePlannedUpgrades.getOrDefault(upgrade.getType().shipyardItem, Collections.emptyList()).remove(upgrade);
			}

			void clearUpgrades() {
				if (thePlannedUpgrades != null) {
					thePlannedUpgrades.clear();
				}
			}

			@Override
			public int getItems(ShipyardItemType type) {
				int level = theWrappedBody.getStationedShips(type);
				if (thePlannedUpgrades != null) {
					for (PlannedUpgrade upgrade : thePlannedUpgrades.getOrDefault(type, Collections.emptyList())) {
						level+=upgrade.getQuantity();
					}
				}
				return level;
			}

			@Override
			public UpgradeShips setItems(ShipyardItemType type, int number) {
				return this;
			}
		}
	}
}
