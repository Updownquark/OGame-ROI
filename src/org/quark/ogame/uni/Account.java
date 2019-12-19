package org.quark.ogame.uni;

import org.observe.config.ObservableValueSet;
import org.observe.util.ObjectMethodOverride;
import org.observe.util.ObjectMethodOverride.ObjectMethod;
import org.qommons.Nameable;

public interface Account extends Nameable {
	@Override
	@ObjectMethodOverride(ObjectMethod.toString)
	String getName();

	int getId();
	void setId(int id);

	Universe getUniverse();

	Account getReferenceAccount();
	void setReferenceAccount(Account referenceAccount);

	AccountClass getGameClass();
	void setGameClass(AccountClass clazz);

	Officers getOfficers();

	Research getResearch();

	ObservableValueSet<Planet> getPlanets();
}
