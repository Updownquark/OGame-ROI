package org.quark.ogame.roi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.quark.ogame.uni.UpgradeCost;

public class RoiSequenceCoreElement extends RoiSequenceElement {
	private List<RoiSequenceElement> thePostHelpers;
	private List<RoiSequenceElement> theAccessories;
	private double theRoi;

	public RoiSequenceCoreElement(RoiSequenceElement el) {
		super(el.upgrade, el.planetIndex, false);
		setTargetLevel(el.getTargetLevel(), el.getCost());
		for (RoiSequenceElement dep : el.getDependencies()) {
			withDependency(dep);
		}
	}

	@Override
	RoiSequenceCoreElement setTargetLevel(int targetLevel, UpgradeCost cost) {
		super.setTargetLevel(targetLevel, cost);
		return this;
	}

	public double getRoi() {
		return theRoi;
	}

	public RoiSequenceCoreElement setRoi(double roi) {
		theRoi = roi;
		return this;
	}

	public RoiSequenceCoreElement withPostHelper(RoiSequenceElement helper) {
		if (thePostHelpers == null) {
			thePostHelpers = new ArrayList<>();
		}
		thePostHelpers.add(helper);
		dirtyTotalCost();
		return this;
	}

	public RoiSequenceCoreElement withPostHelper(int index, RoiSequenceElement helper) {
		if (thePostHelpers == null) {
			thePostHelpers = new ArrayList<>();
		}
		thePostHelpers.add(index, helper);
		dirtyTotalCost();
		return this;
	}

	public RoiSequenceCoreElement withAccessory(RoiSequenceElement helper) {
		if (theAccessories == null) {
			theAccessories = new ArrayList<>();
		}
		theAccessories.add(helper);
		dirtyTotalCost();
		return this;
	}

	public RoiSequenceElement removePostHelper(int index) {
		dirtyTotalCost();
		return thePostHelpers.remove(index);
	}

	public RoiSequenceCoreElement clearPostHelpers() {
		if (thePostHelpers != null && !thePostHelpers.isEmpty()) {
			thePostHelpers.clear();
			dirtyTotalCost();
		}
		return this;
	}

	public RoiSequenceCoreElement clearAccessories() {
		if (theAccessories != null && !theAccessories.isEmpty()) {
			theAccessories.clear();
			dirtyTotalCost();
		}
		return this;
	}

	public List<RoiSequenceElement> getPostHelpers() {
		return thePostHelpers == null ? Collections.emptyList() : Collections.unmodifiableList(thePostHelpers);
	}

	public List<RoiSequenceElement> getAccessories() {
		return theAccessories == null ? Collections.emptyList() : Collections.unmodifiableList(theAccessories);
	}

	@Override
	protected UpgradeCost calculateTotalCost() {
		UpgradeCost cost = super.calculateTotalCost();
		if (thePostHelpers != null) {
			for (RoiSequenceElement helper : thePostHelpers) {
				cost = cost.plus(helper.getTotalCost());
			}
		}
		if (theAccessories != null) {
			for (RoiSequenceElement helper : theAccessories) {
				cost = cost.plus(helper.getTotalCost());
			}
		}
		return cost;
	}
}
