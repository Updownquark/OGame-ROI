package org.quark.ogame;

public enum OGameBuildingType {
	Metal, Crystal, Deuterium, Fusion, Robotics, Nanite, ResearchLab, MetalStorage, CrystalStorage, DeutStorage;

	public static OGameBuildingType getStorage(int resourceType) {
		switch (resourceType) {
		case 0:
			return MetalStorage;
		case 1:
			return CrystalStorage;
		case 2:
			return DeutStorage;
		default:
			throw new IllegalArgumentException("Unrecognized resource type: " + resourceType);
		}
	}
}
