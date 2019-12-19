package org.quark.ogame.uni;

import org.observe.util.ObjectMethodOverride;
import org.observe.util.ObjectMethodOverride.ObjectMethod;

public interface Moon extends RockyBody {
	@Override
	@ObjectMethodOverride(ObjectMethod.toString)
	String getName();

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
	default void setBuildingLevel(BuildingType type, int buildingLevel) {
		switch (type) {
		case RoboticsFactory:
			setRoboticsFactory(buildingLevel);
		case Shipyard:
			setShipyard(buildingLevel);
		case LunarBase:
			setLunarBase(buildingLevel);
		case SensorPhalanx:
			setSensorPhalanx(buildingLevel);
		case JumpGate:
			setJumpGate(buildingLevel);
		default:
			if (buildingLevel != 0) {
				throw new IllegalArgumentException();
			}
		}
	}

	int getFieldBonus();

	void setFieldBonus(int fieldBonus);

	@Override
	default int getStationedShips(ShipyardItemType type) {
		return 0;
	}

	@Override
	default void setStationedShips(ShipyardItemType type, int count) {
	}

	@Override
	default int getUsedFields() {
		return RockyBody.super.getUsedFields()//
			+ getLunarBase() + getSensorPhalanx() + getJumpGate();
	}
}
