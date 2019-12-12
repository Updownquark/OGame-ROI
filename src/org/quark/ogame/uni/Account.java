package org.quark.ogame.uni;

import org.observe.collect.ObservableCollection;

public interface Account {
	Research getResearch();

	ObservableCollection<Planet> getPlanets();
}
