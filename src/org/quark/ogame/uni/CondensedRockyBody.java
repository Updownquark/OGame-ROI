package org.quark.ogame.uni;

public interface CondensedRockyBody extends RockyBody {
	@Override
	default int getRoboticsFactory() {
		return getBuildingLevel(BuildingType.RoboticsFactory);
	}

	@Override
	default CondensedRockyBody setRoboticsFactory(int roboticsFactory) {
		setBuildingLevel(BuildingType.RoboticsFactory, roboticsFactory);
		return this;
	}

	@Override
	default int getShipyard() {
		return getBuildingLevel(BuildingType.Shipyard);
	}

	@Override
	default CondensedRockyBody setShipyard(int shipyard) {
		setBuildingLevel(BuildingType.Shipyard, shipyard);
		return this;
	}

	@Override
	abstract int getBuildingLevel(BuildingType type);

	@Override
	abstract CondensedRockyBody setBuildingLevel(BuildingType type, int buildingLevel);
}
