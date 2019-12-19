package org.quark.ogame.uni;

import org.observe.config.ObservableValueSet;
import org.qommons.Nameable;

public interface Account extends Nameable {
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
