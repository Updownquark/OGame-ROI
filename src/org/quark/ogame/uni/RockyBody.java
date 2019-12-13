package org.quark.ogame.uni;

import org.qommons.Nameable;

public interface RockyBody extends Nameable {
	// Facilities
	int getRoboticsFactory();
	void setRoboticsFactory(int roboticsFactory);

	int getShipyard();
	void setShipyard(int shipyard);

	int getBuildingLevel(BuildingType type);
	int getStationedShips(ShipyardItemType type);

	int getFieldBonus();
	void setFieldBonus(int fieldBonus);

	default int getUsedFields() {
		return getRoboticsFactory() + getShipyard();
	}
}
