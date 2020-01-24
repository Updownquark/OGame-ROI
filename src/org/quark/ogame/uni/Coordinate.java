package org.quark.ogame.uni;

import org.observe.util.ObjectMethodOverride;
import org.observe.util.ObjectMethodOverride.ObjectMethod;

public interface Coordinate extends Comparable<Coordinate> {
	int getGalaxy();

	Coordinate setGalaxy(int galaxy);

	int getSystem();

	Coordinate setSystem(int system);

	int getSlot();

	Coordinate setSlot(int slot);

	default Coordinate set(int galaxy, int system, int slot) {
		return setGalaxy(galaxy).setSystem(system).setSlot(slot);
	}

	@Override
	default int compareTo(Coordinate other) {
		int comp = Integer.compare(getGalaxy(), other.getGalaxy());
		if (comp == 0) {
			comp = Integer.compare(getSystem(), other.getSystem());
		}
		if (comp == 0) {
			comp = Integer.compare(getSlot(), other.getSlot());
		}
		return comp;
	}

	@ObjectMethodOverride(ObjectMethod.equals)
	default boolean isEqual(Object other) {
		if (this == other) {
			return true;
		} else if (!(other instanceof Coordinate)) {
			return false;
		} else {
			return compareTo((Coordinate) other) == 0;
		}
	}

	@ObjectMethodOverride(ObjectMethod.hashCode)
	default int hash() {
		return (getGalaxy() * 13) + (getSystem() * 7) + getSlot();
	}

	@ObjectMethodOverride(ObjectMethod.toString)
	default String print() {
		return new StringBuilder("[").append(getGalaxy()).append(':').append(getSystem()).append(':').append(getSlot()).append(']')
			.toString();
	}
}
