package org.quark.ogame.uni.versions;

import org.quark.ogame.uni.FleetRules;
import org.quark.ogame.uni.OGameEconomyRuleSet;
import org.quark.ogame.uni.OGameRuleSet;

public class OGameRuleSet740 implements OGameRuleSet {
	@Override
	public String getName() {
		return "7.4.0";
	}

	private final OGameEconomy740 theEconomy = new OGameEconomy740();
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
