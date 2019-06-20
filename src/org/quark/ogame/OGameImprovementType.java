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
	Energy(true, ResearchLab, IRN), Plasma(false, ResearchLab, IRN), Planet(false, ResearchLab, IRN);

	public final boolean energyType;
	public final List<OGameImprovementType> helpers;

	private OGameImprovementType(boolean energyType, OGameImprovementType... helpers) {
		this.energyType=energyType;
		this.helpers = Collections.unmodifiableList(Arrays.asList(helpers));
	}

	public boolean isMine() {
		switch (this) {
		case Metal:
		case Crystal:
		case Deut:
			return true;
		default:
			return false;
		}
	}
}