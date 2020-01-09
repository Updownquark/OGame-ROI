package org.quark.ogame.uni;

import org.observe.util.NamedEntity;

public interface Trade extends NamedEntity {
	ResourceType getType();

	TradeRatios getRate();

	long getResource1();
	Trade setResource1(long res1);

	long getResource2();
	Trade setResource2(long res2);

	default long getRequiredResource() {
		double amt = 0;
		switch (getType()) {
		case Metal:
			amt += getResource1() / getRate().getCrystal();
			amt += getResource2() / getRate().getDeuterium();
			amt *= getRate().getMetal();
			break;
		case Crystal:
			amt += getResource1() / getRate().getMetal();
			amt += getResource2() / getRate().getDeuterium();
			amt *= getRate().getCrystal();
			break;
		case Deuterium:
			amt += getResource1() / getRate().getMetal();
			amt += getResource2() / getRate().getCrystal();
			amt *= getRate().getDeuterium();
			break;
		case Energy:
			throw new IllegalStateException("Energy cannot be traded");
		}
		return Math.round(amt);
	}

	default long getMetal() {
		switch (getType()) {
		case Metal:
			return getRequiredResource();
		default:
			return getResource1();
		}
	}

	default void setMetal(long metal) {
		switch (getType()) {
		case Metal:
			throw new UnsupportedOperationException("This is a metal trade");
		default:
			setResource1(metal);
			break;
		}
	}

	default long getCrystal() {
		switch (getType()) {
		case Metal:
			return getResource1();
		case Crystal:
			return getRequiredResource();
		default:
			return getResource2();
		}
	}

	default void setCrystal(long crystal) {
		switch (getType()) {
		case Metal:
			setResource1(crystal);
			break;
		case Crystal:
			throw new UnsupportedOperationException("This is a crystal trade");
		default:
			setResource2(crystal);
			return;
		}
	}

	default long getDeuterium() {
		switch (getType()) {
		case Deuterium:
			return getRequiredResource();
		default:
			return getResource2();
		}
	}

	default void setDeuterium(long deuterium) {
		switch (getType()) {
		case Deuterium:
			throw new UnsupportedOperationException("This is a deuterium trade");
		default:
			setResource2(deuterium);
			break;
		}
	}
}
