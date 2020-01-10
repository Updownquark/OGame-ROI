package org.quark.ogame.roi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.quark.ogame.uni.ResourceType;

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

	public static OGameImprovementType getMineImprovement(ResourceType resourceType) {
		switch (resourceType) {
		case Metal:
			return Metal;
		case Crystal:
			return Crystal;
		case Deuterium:
			return Deut;
		default:
			throw new IllegalArgumentException("Unrecognized resource type: " + resourceType);
		}
	}

	public static OGameImprovementType getStorageImprovement(ResourceType resourceType) {
		switch (resourceType) {
		case Metal:
			return MetalStorage;
		case Crystal:
			return CrystalStorage;
		case Deuterium:
			return DeutStorage;
		default:
			throw new IllegalArgumentException("Unrecognized resource type: " + resourceType);
		}
	}

	public ResourceType isMine() {
		switch (this) {
		case Metal:
			return ResourceType.Metal;
		case Crystal:
			return ResourceType.Crystal;
		case Deut:
			return ResourceType.Deuterium;
		default:
			return null;
		}
	}

	public ResourceType isStorage() {
		switch (this) {
		case MetalStorage:
			return ResourceType.Metal;
		case CrystalStorage:
			return ResourceType.Crystal;
		case DeutStorage:
			return ResourceType.Deuterium;
		default:
			return null;
		}
	}
}