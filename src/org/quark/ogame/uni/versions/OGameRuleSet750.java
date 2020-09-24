package org.quark.ogame.uni.versions;

import org.quark.ogame.uni.FleetRules;
import org.quark.ogame.uni.OGameEconomyRuleSet;
import org.quark.ogame.uni.OGameRuleSet;

public class OGameRuleSet750 implements OGameRuleSet {
	@Override
	public String getName() {
		return "7.5.0";
	}

	private final OGameEconomy750 theEconomy = new OGameEconomy750();
	private final OGameFleet710 theFleet = new OGameFleet710();

	@Override
	public OGameEconomyRuleSet economy() {
		return theEconomy;
	}

	@Override
	public FleetRules fleet() {
		return theFleet;
	}

}
