package org.quark.ogame;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum OGameImprovementType {
	// Helper improvements
	Robotics(false), Nanite(false), ResearchLab(false), IRN(false), //
	// Buildings
	Metal(false, Robotics, Nanite), Crystal(false, Robotics, Nanite), Deut(false, Robotics, Nanite), Fusion(true, Robotics, Nanite), //
	// Research
	Energy(true, ResearchLab, IRN), Plasma(false, ResearchLab, IRN), Planet(false, ResearchLab, IRN),//
	//Storage
	MetalStorage(false), CrystalStorage(false), DeutStorage(false);

	public final boolean energyType;
	public final List<OGameImprovementType> helpers;

	private OGameImprovementType(boolean energyType, OGameImprovementType... helpers) {
		this.energyType=energyType;
		this.helpers = Collections.unmodifiableList(Arrays.asList(helpers));
	}

	public static OGameImprovementType getMineImprovement(int resourceType) {
		switch (resourceType) {
		case 0:
			return Metal;
		case 1:
			return Crystal;
		case 2:
			return Deut;
		default:
			throw new IllegalArgumentException("Unrecognized resource type: " + resourceType);
		}
	}

	public static OGameImprovementType getStorageImprovement(int resourceType) {
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

	public int isMine() {
		switch (this) {
		case Metal:
			return 0;
		case Crystal:
			return 1;
		case Deut:
			return 2;
		default:
			return -1;
		}
	}

	public int isStorage() {
		switch (this) {
		case MetalStorage:
			return 0;
		case CrystalStorage:
			return 1;
		case DeutStorage:
			return 2;
		default:
			return -1;
		}
	}
}