package org.quark.ogame.uni;

import org.observe.util.ObjectMethodOverride;
import org.observe.util.ObjectMethodOverride.ObjectMethod;
import org.qommons.Nameable;

public interface RockyBody extends Nameable {
	@Override
	@ObjectMethodOverride(ObjectMethod.toString)
	String getName();

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
