package org.quark.ogame.uni;

import org.qommons.Named;

public interface OGameRuleSet extends Named {
	OGameEconomyRuleSet economy();

	FleetRules fleet();
}
