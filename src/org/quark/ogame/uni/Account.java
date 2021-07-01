package org.quark.ogame.uni;

import org.observe.config.SyncValueSet;
import org.observe.util.ObjectMethodOverride;
import org.observe.util.ObjectMethodOverride.ObjectMethod;
import org.qommons.Nameable;

public interface Account extends Nameable {
	@Override
	@ObjectMethodOverride(ObjectMethod.toString)
	String getName();

	int getId();

	Universe getUniverse();

	AccountClass getGameClass();
	Account setGameClass(AccountClass clazz);

	AllianceClass getAllianceClass();
	Account setAllianceClass(AllianceClass clazz);

	Officers getOfficers();

	Research getResearch();

	SyncValueSet<Planet> getPlanets();

	SyncValueSet<Holding> getHoldings();
	SyncValueSet<Trade> getTrades();
	SyncValueSet<PlannedUpgrade> getPlannedUpgrades();
	SyncValueSet<PlannedFlight> getPlannedFlights();
}
