package org.quark.ogame.roi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.quark.ogame.uni.AccountUpgradeType;
import org.quark.ogame.uni.ResourceType;

public enum OGameImprovementType {
	// Helper improvements
	Robotics(AccountUpgradeType.RoboticsFactory, true, false),
	Nanite(AccountUpgradeType.NaniteFactory, true, false),
	ResearchLab(AccountUpgradeType.ResearchLab, true, false),
	IRN(AccountUpgradeType.IntergalacticResearchNetwork, true, false), //
	// Buildings
	Metal(AccountUpgradeType.MetalMine, false, false, Robotics, Nanite),
	Crystal(AccountUpgradeType.CrystalMine, false, false, Robotics, Nanite),
	Deut(AccountUpgradeType.DeuteriumSynthesizer, false, false, Robotics, Nanite),
	Fusion(AccountUpgradeType.FusionReactor, false, true, Robotics, Nanite), //
	// Research
	Energy(AccountUpgradeType.Energy, false, true, ResearchLab, IRN),
	Plasma(AccountUpgradeType.Plasma, false, false, ResearchLab, IRN),
	Planet(AccountUpgradeType.Astrophysics, false, false, ResearchLab, IRN), //
	//Storage
	MetalStorage(AccountUpgradeType.MetalStorage, false, false),
	CrystalStorage(AccountUpgradeType.CrystalStorage, false, false),
	DeutStorage(AccountUpgradeType.DeuteriumStorage, false, false), //
	
	Crawler(AccountUpgradeType.Crawler, false, false);

	public final AccountUpgradeType upgradeType;
	public final boolean isHelper;
	public final boolean energyType;
	public final List<OGameImprovementType> helpers;

	private OGameImprovementType(AccountUpgradeType type, boolean helper, boolean energyType, OGameImprovementType... helpers) {
		upgradeType = type;
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