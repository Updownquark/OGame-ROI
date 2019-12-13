package org.quark.ogame.uni;

import org.observe.config.ObservableValueSet;
import org.qommons.Nameable;

public interface Account extends Nameable {
	int getId();
	void setId(int id);

	Universe getUniverse();

	int getReferenceAccount();
	void setReferenceAccount(int referenceAccount);

	AccountClass getGameClass();
	void setGameClass(AccountClass clazz);

	Officers getOfficers();

	Research getResearch();

	ObservableValueSet<Planet> getPlanets();
}
