package org.quark.ogame.uni.versions;

import org.quark.ogame.uni.FleetRules;
import org.quark.ogame.uni.OGameEconomyRuleSet;
import org.quark.ogame.uni.OGameRuleSet;

public class OGameRuleSet711 implements OGameRuleSet {
	@Override
	public String getName() {
		return "7.1.1";
	}

	private final OGameEconomy711 theEconomy = new OGameEconomy711();
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
