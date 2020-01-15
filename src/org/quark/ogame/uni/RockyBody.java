package org.quark.ogame.uni;

import org.qommons.Nameable;

public interface RockyBody extends Nameable {
	// Facilities
	int getRoboticsFactory();
	void setRoboticsFactory(int roboticsFactory);

	int getShipyard();
	void setShipyard(int shipyard);

	int getBuildingLevel(BuildingType type);
	void setBuildingLevel(BuildingType type, int buildingLevel);

	default int getStationedShips(ShipyardItemType type) {
		if (type.mobile) {
			return getStationedFleet().getItems(type);
		} else {
			return getStationaryStructures().getItems(type);
		}
	}

	default RockyBody setStationedShips(ShipyardItemType type, int count) {
		if (type.mobile) {
			getStationedFleet().setItems(type, count);
		} else {
			getStationaryStructures().setItems(type, count);
		}
		return this;
	}

	BuildingType getCurrentUpgrade();
	void setCurrentUpgrade(BuildingType building);

	default int getUsedFields() {
		return getRoboticsFactory() + getShipyard();
	}

	StationaryStructures getStationaryStructures();
	Fleet getStationedFleet();
}
