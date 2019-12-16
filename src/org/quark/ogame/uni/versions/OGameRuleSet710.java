package org.quark.ogame.uni.versions;

import org.quark.ogame.uni.FleetRules;
import org.quark.ogame.uni.OGameEconomyRuleSet;
import org.quark.ogame.uni.OGameRuleSet;

public class OGameRuleSet710 implements OGameRuleSet {
	@Override
	public String getName() {
		return "7.1.0";
	}

	private final OGameEconomy710 theEconomy = new OGameEconomy710();
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
