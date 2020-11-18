package org.quark.ogame.roi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.quark.ogame.roi.RoiAccount.RoiPlanet;
import org.quark.ogame.roi.RoiAccount.RoiRockyBody;
import org.quark.ogame.uni.AccountUpgradeType;
import org.quark.ogame.uni.UpgradeCost;

public class RoiSequenceElement {
	public final AccountUpgradeType upgrade;
	public final int planetIndex;
	public final boolean isMoon;
	private int theTargetLevel;
	private long theTime;
	private UpgradeCost theCost;
	private UpgradeCost theTotalCost;
	private List<RoiSequenceElement> thePreHelpers;
	private List<RoiSequenceElement> theDependencies;

	public RoiSequenceElement(AccountUpgradeType upgrade, int planetIndex, boolean moon) {
		this.upgrade = upgrade;
		this.planetIndex = planetIndex;
		isMoon = moon;
	}

	public RoiRockyBody getBody(RoiAccount account) {
		if (planetIndex < 0 || planetIndex >= account.roiPlanets().size()) {
			return null;
		}
		RoiPlanet planet = account.roiPlanets().get(planetIndex);
		return isMoon ? planet.getMoon() : planet;
	}

	public int getTargetLevel() {
		return theTargetLevel;
	}

	public UpgradeCost getCost() {
		return theCost;
	}

	public UpgradeCost getTotalCost() {
		if (theCost == null) {
			return null;
		}
		if (theTotalCost == null) {
			theTotalCost = calculateTotalCost();
		}
		return theTotalCost;
	}

	protected UpgradeCost calculateTotalCost() {
		UpgradeCost cost = theCost;
		for (RoiSequenceElement helper : getPreHelpers()) {
			cost = cost.plus(helper.getTotalCost());
		}
		for (RoiSequenceElement dep : getDependencies()) {
			cost = cost.plus(dep.getTotalCost());
		}
		return cost;
	}

	public List<RoiSequenceElement> getPreHelpers() {
		return thePreHelpers == null ? Collections.emptyList() : Collections.unmodifiableList(thePreHelpers);
	}

	public List<RoiSequenceElement> getDependencies() {
		return theDependencies == null ? Collections.emptyList() : Collections.unmodifiableList(theDependencies);
	}

	public RoiSequenceElement withPreHelper(RoiSequenceElement dep) {
		if (thePreHelpers == null) {
			thePreHelpers = new ArrayList<>();
		}
		thePreHelpers.add(dep);
		dirtyTotalCost();
		return this;
	}

	public RoiSequenceElement withPreHelper(int index, RoiSequenceElement helper) {
		if (thePreHelpers == null) {
			thePreHelpers = new ArrayList<>();
		}
		thePreHelpers.add(index, helper);
		dirtyTotalCost();
		return this;
	}

	public RoiSequenceElement removePreHelper(int index) {
		dirtyTotalCost();
		return thePreHelpers.remove(index);
	}

	public RoiSequenceElement clearPreHelpers() {
		if (thePreHelpers != null && !thePreHelpers.isEmpty()) {
			thePreHelpers.clear();
			dirtyTotalCost();
		}
		return this;
	}

	public RoiSequenceElement withDependency(RoiSequenceElement dep) {
		if (theDependencies == null) {
			theDependencies = new ArrayList<>();
		}
		theDependencies.add(dep);
		dirtyTotalCost();
		return this;
	}

	RoiSequenceElement setTargetLevel(int targetLevel, UpgradeCost cost) {
		theTargetLevel = targetLevel;
		theCost = cost;
		dirtyTotalCost();
		return this;
	}

	protected void dirtyTotalCost() {
		theTotalCost = null;
	}

	public int getLevel(RoiAccount roiAccount) {
		if (upgrade == null) {
			return 0;
		}
		RoiRockyBody body = getBody(roiAccount);
		if (body == null && upgrade.research == null) {
			return 0;
		}
		return upgrade.getLevel(roiAccount, body);
	}

	public long getTime() {
		return theTime;
	}

	void setTime(long time) {
		theTime = time;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder().append(upgrade);
		if (planetIndex >= 0) {
			str.append('@').append(planetIndex);
		}
		if (theTargetLevel > 0) {
			str.append(':').append(theTargetLevel);
		}
		return str.toString();
	}
}
