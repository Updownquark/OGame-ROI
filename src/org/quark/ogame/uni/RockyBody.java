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
	int getStationedShips(ShipyardItemType type);
	void setStationedShips(ShipyardItemType type, int count);

	BuildingType getCurrentUpgrade();
	void setCurrentUpgrade(BuildingType building);

	default int getUsedFields() {
		return getRoboticsFactory() + getShipyard();
	}
}
