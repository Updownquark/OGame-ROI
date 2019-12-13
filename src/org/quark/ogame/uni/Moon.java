package org.quark.ogame.uni;

public interface Moon extends RockyBody {
	// Facilities
	int getLunarBase();
	void setLunarBase(int lunarBase);

	int getSensorPhalanx();
	void setSensorPhalanx(int sensorPhalanx);

	int getJumpGate();
	void setJumpGate(int jumpGate);

	@Override
	default int getBuildingLevel(BuildingType type) {
		switch (type) {
		case RoboticsFactory:
			return getRoboticsFactory();
		case Shipyard:
			return getShipyard();
		case LunarBase:
			return getLunarBase();
		case SensorPhalanx:
			return getSensorPhalanx();
		case JumpGate:
			return getJumpGate();
		default:
			return 0;
		}
	}

	@Override
	default int getStationedShips(ShipyardItemType type) {
		return 0;
	}

	@Override
	default int getUsedFields() {
		return RockyBody.super.getUsedFields()//
			+ getLunarBase() + getSensorPhalanx() + getJumpGate();
	}
}
