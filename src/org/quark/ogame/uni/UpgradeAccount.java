package org.quark.ogame.uni;

import java.util.LinkedList;
import java.util.List;

import org.observe.config.SyncValueSet;
import org.qommons.Nameable;

public class UpgradeAccount implements Account {
	private final Account theWrapped;
	private final UpgradeResearch theResearch;
	private List<AccountUpgrade> theUpgrades;

	public UpgradeAccount(Account wrap) {
		theWrapped = wrap;
		theUpgrades = new LinkedList<>();
		theResearch = new UpgradeResearch();
	}

	public UpgradeAccount withUpgrade(AccountUpgrade upgrade) {
		theUpgrades.add(upgrade);
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
	public Account getReferenceAccount() {
		return theWrapped.getReferenceAccount();
	}

	@Override
	public Account setReferenceAccount(Account referenceAccount) {
		return this;
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
	public SyncValueSet<Planet> getPlanets() {
		return theWrapped.getPlanets();
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

	public class UpgradeResearch implements CondensedResearch {
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
			for (AccountUpgrade upgrade : theUpgrades) {
				if (upgrade.getType().research == type) {
					level = Math.max(level, upgrade.getToLevel());
				}
			}
			return level;
		}

		@Override
		public void setResearchLevel(ResearchType type, int level) {}
	}

	public static class UpgradePlanet implements CondensedPlanet {
		private final Planet theWrapped;
		private final UpgradeStructures theStructures;
		private final List<AccountUpgrade> theUpgrades;

		public UpgradePlanet(Planet wrapped) {
			theWrapped = wrapped;
			theUpgrades = new LinkedList<>();
			theStructures = new UpgradeStructures();
		}

		public UpgradePlanet withUpgrade(AccountUpgrade upgrade) {
			theUpgrades.add(upgrade);
			return this;
		}

		@Override
		public Coordinate getCoordinates() {
			return theWrapped.getCoordinates();
		}

		@Override
		public int getBaseFields() {
			return theWrapped.getBaseFields();
		}

		@Override
		public Planet setBaseFields(int baseFields) {
			return this;
		}

		@Override
		public int getMinimumTemperature() {
			return theWrapped.getMinimumTemperature();
		}

		@Override
		public Planet setMinimumTemperature(int minTemp) {
			return this;
		}

		@Override
		public int getMaximumTemperature() {
			return theWrapped.getMaximumTemperature();
		}

		@Override
		public Planet setMaximumTemperature(int maxTemp) {
			return this;
		}

		@Override
		public Moon getMoon() {
			return theWrapped.getMoon();
		}

		@Override
		public int getMetalUtilization() {
			return theWrapped.getMetalUtilization();
		}

		@Override
		public Planet setMetalUtilization(int utilization) {
			return this;
		}

		@Override
		public int getCrystalUtilization() {
			return theWrapped.getCrystalUtilization();
		}

		@Override
		public Planet setCrystalUtilization(int utilization) {
			return this;
		}

		@Override
		public int getDeuteriumUtilization() {
			return theWrapped.getDeuteriumUtilization();
		}

		@Override
		public Planet setDeuteriumUtilization(int utilization) {
			return this;
		}

		@Override
		public int getSolarPlantUtilization() {
			return theWrapped.getSolarPlantUtilization();
		}

		@Override
		public Planet setSolarPlantUtilization(int utilization) {
			return this;
		}

		@Override
		public int getFusionReactorUtilization() {
			return theWrapped.getFusionReactorUtilization();
		}

		@Override
		public Planet setFusionReactorUtilization(int utilization) {
			return this;
		}

		@Override
		public int getSolarSatelliteUtilization() {
			return theWrapped.getSolarSatelliteUtilization();
		}

		@Override
		public Planet setSolarSatelliteUtilization(int utilization) {
			return this;
		}

		@Override
		public int getCrawlerUtilization() {
			return theWrapped.getCrawlerUtilization();
		}

		@Override
		public Planet setCrawlerUtilization(int utilization) {
			return this;
		}

		@Override
		public String getName() {
			return theWrapped.getName();
		}

		@Override
		public BuildingType getCurrentUpgrade() {
			return null;
		}

		@Override
		public UpgradePlanet setCurrentUpgrade(BuildingType building) {
			return this;
		}

		@Override
		public StationaryStructures getStationaryStructures() {
			return theStructures;
		}

		@Override
		public Fleet getStationedFleet() {
			return theWrapped.getStationedFleet();
		}

		@Override
		public CondensedPlanet setName(String name) {
			return this;
		}

		@Override
		public int getBuildingLevel(BuildingType type) {
			int level = theWrapped.getBuildingLevel(type);
			for (AccountUpgrade upgrade : theUpgrades) {
				if (upgrade.getType().building == type) {
					level = Math.max(level, upgrade.getToLevel());
				}
			}
			return level;
		}

		@Override
		public CondensedPlanet setBuildingLevel(BuildingType type, int buildingLevel) {
			return this;
		}

		@Override
		public int getBonus(ResourceType type) {
			return theWrapped.getBonus(type);
		}

		@Override
		public Planet setBonus(ResourceType type, int level) {
			return this;
		}

		public class UpgradeStructures implements CondensedStationaryStructures {
			@Override
			public int getItems(ShipyardItemType type) {
				int level = theWrapped.getStationaryStructures().getItems(type);
				for (AccountUpgrade upgrade : theUpgrades) {
					if (upgrade.getType().shipyardItem == type) {
						level = Math.max(level, upgrade.getToLevel());
					}
				}
				return level;
			}

			@Override
			public StationaryStructures setItems(ShipyardItemType type, int number) {
				return this;
			}
		}
	}
}
