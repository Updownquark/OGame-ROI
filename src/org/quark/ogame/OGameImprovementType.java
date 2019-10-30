package org.quark.ogame;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum OGameImprovementType {
	// Helper improvements
	Robotics(true, false), Nanite(true, false), ResearchLab(true, false), IRN(true, false), //
	// Buildings
	Metal(false, false, Robotics, Nanite), Crystal(false, false, Robotics, Nanite), Deut(false, false, Robotics, Nanite), Fusion(false, true, Robotics, Nanite), //
	// Research
	Energy(false, true, ResearchLab, IRN), Plasma(false, false, ResearchLab, IRN), Planet(false, false, ResearchLab, IRN),//
	//Storage
	MetalStorage(false, false), CrystalStorage(false, false), DeutStorage(false, false),//
	
	Crawler(false, false);

	public final boolean isHelper;
	public final boolean energyType;
	public final List<OGameImprovementType> helpers;

	private OGameImprovementType(boolean helper, boolean energyType, OGameImprovementType... helpers) {
		isHelper=helper;
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