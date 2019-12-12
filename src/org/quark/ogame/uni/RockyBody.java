package org.quark.ogame.uni;

public interface RockyBody {
	// Facilities
	int getRoboticsFactory();
	void setRoboticsFactory(int roboticsFactory);

	int getShipyard();
	void setShipyard(int shipyard);

	int getTotalFields();

	default int getUsedFields() {
		return getRoboticsFactory() + getShipyard();
	}
}
