package org.quark.ogame.uni;

import org.observe.util.NamedEntity;

public interface Holding extends NamedEntity {
	AccountUpgradeType getType();
	Holding setType(AccountUpgradeType type);

	int getLevel();
	Holding setLevel(int level);

	long getMetal();
	Holding setMetal(long metal);

	long getCrystal();
	Holding setCrystal(long crystal);

	long getDeuterium();
	Holding setDeuterium(long deuterium);
}
