package org.quark.ogame;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum OGameImprovementType {
	// Helper improvements
	Nanite, ResearchLab, IRN, //
	// Buildings
	Metal(Nanite), Crystal(Nanite), Deut(Nanite), Fusion(Nanite), //
	// Research
	Energy(ResearchLab, IRN), Plasma(ResearchLab, IRN), Planet(ResearchLab, IRN);

	public final List<OGameImprovementType> helpers;

	private OGameImprovementType(OGameImprovementType... helpers) {
		this.helpers = Collections.unmodifiableList(Arrays.asList(helpers));
	}
}