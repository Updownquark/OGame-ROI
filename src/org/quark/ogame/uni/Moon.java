package org.quark.ogame.uni;

import org.observe.config.ParentReference;
import org.observe.util.ObjectMethodOverride;
import org.observe.util.ObjectMethodOverride.ObjectMethod;

public interface Moon extends RockyBody {
	@ParentReference
	Planet getPlanet();
	@Override
	@ObjectMethodOverride(ObjectMethod.toString)
	String getName();

	// Facilities
	int getLunarBase();
	Moon setLunarBase(int lunarBase);

	int getSensorPhalanx();
	Moon setSensorPhalanx(int sensorPhalanx);

	int getJumpGate();
	Moon setJumpGate(int jumpGate);

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
	default Moon setBuildingLevel(BuildingType type, int buildingLevel) {
		switch (type) {
		case RoboticsFactory:
			setRoboticsFactory(buildingLevel);
			break;
		case Shipyard:
			setShipyard(buildingLevel);
			break;
		case LunarBase:
			setLunarBase(buildingLevel);
			break;
		case SensorPhalanx:
			setSensorPhalanx(buildingLevel);
			break;
		case JumpGate:
			setJumpGate(buildingLevel);
			break;
		default:
			if (buildingLevel != 0) {
				throw new IllegalArgumentException();
			}
		}
		return this;
	}

	int getFieldBonus();
	Moon setFieldBonus(int fieldBonus);

	@Override
	default int getUsedFields() {
		return RockyBody.super.getUsedFields()//
			+ getLunarBase() + getSensorPhalanx() + getJumpGate();
	}
}
