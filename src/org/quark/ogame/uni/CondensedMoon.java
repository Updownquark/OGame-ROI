package org.quark.ogame.uni;

public interface CondensedMoon extends CondensedRockyBody, Moon {
	@Override
	default CondensedMoon setStationedShips(ShipyardItemType type, int count) {
		CondensedRockyBody.super.setStationedShips(type, count);
		return this;
	}

	@Override
	CondensedMoon setCurrentUpgrade(BuildingType building);

	@Override
	CondensedMoon setName(String name);

	@Override
	CondensedMoon setBuildingLevel(BuildingType type, int buildingLevel);

	@Override
	int getBuildingLevel(BuildingType type);

	@Override
	default int getLunarBase() {
		return getBuildingLevel(BuildingType.LunarBase);
	}

	@Override
	default Moon setLunarBase(int lunarBase) {
		setBuildingLevel(BuildingType.LunarBase, lunarBase);
		return this;
	}

	@Override
	default int getSensorPhalanx() {
		return getBuildingLevel(BuildingType.SensorPhalanx);
	}

	@Override
	default Moon setSensorPhalanx(int sensorPhalanx) {
		setBuildingLevel(BuildingType.SensorPhalanx, sensorPhalanx);
		return this;
	}

	@Override
	default int getJumpGate() {
		return getBuildingLevel(BuildingType.JumpGate);
	}

	@Override
	default Moon setJumpGate(int jumpGate) {
		setBuildingLevel(BuildingType.JumpGate, jumpGate);
		return this;
	}
}
