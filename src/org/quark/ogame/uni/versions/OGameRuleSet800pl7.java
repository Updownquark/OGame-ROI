package org.quark.ogame.uni.versions;

import org.quark.ogame.uni.FleetRules;
import org.quark.ogame.uni.OGameEconomyRuleSet;
import org.quark.ogame.uni.OGameRuleSet;

public class OGameRuleSet800pl7 implements OGameRuleSet {
	@Override
	public String getName() {
		return "8.0.0-pl7";
	}

	private final OGameEconomy800pl7 theEconomy = new OGameEconomy800pl7();
	private final OGameFleet800pl7 theFleet = new OGameFleet800pl7();

	@Override
	public OGameEconomyRuleSet economy() {
		return theEconomy;
	}

	@Override
	public FleetRules fleet() {
		return theFleet;
	}
}
