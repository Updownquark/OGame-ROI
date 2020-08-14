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

	Account getReferenceAccount();
	Account setReferenceAccount(Account referenceAccount);

	AccountClass getGameClass();
	Account setGameClass(AccountClass clazz);

	Officers getOfficers();

	Research getResearch();

	SyncValueSet<Planet> getPlanets();

	SyncValueSet<Holding> getHoldings();
	SyncValueSet<Trade> getTrades();
	SyncValueSet<PlannedUpgrade> getPlannedUpgrades();
	SyncValueSet<PlannedFlight> getPlannedFlights();
}
