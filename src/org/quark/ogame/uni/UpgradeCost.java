package org.quark.ogame.uni;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.qommons.QommonsUtils;
import org.quark.ogame.OGameUtils;

public interface UpgradeCost {
	long getMetal();
	long getCrystal();
	long getDeuterium();

	int getEnergy();
	Duration getUpgradeTime();

	UpgradeCost ofType(UpgradeType type);
	long getPoints(PointType type);

	boolean isZero();

	UpgradeCost negate();
	UpgradeCost plus(UpgradeCost cost);
	UpgradeCost times(int mult);

	UpgradeCost divide(double div);

	default long getTotal() {
		return getMetal() + getCrystal() + getDeuterium();
	}

	default double getMetalValue(TradeRatios tradeRate) {
		long value = getMetal();
		value += Math.round(getCrystal() * tradeRate.getMetal() / tradeRate.getCrystal());
		value += Math.round(getDeuterium() * tradeRate.getMetal() / tradeRate.getDeuterium());
		return value;
	}

	public static final UpgradeCost ZERO = new SimpleUpgradeCost(UpgradeType.Building, PointType.Economy, 0, 0, 0, 0, Duration.ZERO);

	public static UpgradeCost of(UpgradeType type, PointType pointType, long metal, long crystal, long deut, int energy, Duration time) {
		if (metal == 0 && crystal == 0 && deut == 0 && energy == 0 && time.isZero()) {
			return ZERO;
		}
		return new SimpleUpgradeCost(type, pointType, metal, crystal, deut, energy, time);
	}

	public static UpgradeCost of(char type, char pointType, long metal, long crystal, long deut, int energy, Duration time) {
		UpgradeType t;
		PointType p;
		switch (type) {
		case 'b': // Building
		case 'B':
			t = UpgradeType.Building;
			break;
		case 'r':
		case 'R':
			t = UpgradeType.Research;
			break;
		case 'f': // Fleet
		case 'F':
		case 's': // Ship
		case 'S':
			t = UpgradeType.ShipyardItem;
			break;
		default:
			throw new IllegalArgumentException("Unrecognized upgrade type character: " + type + ". Use 'b', 'r', 'f', or 's'");
		}
		switch (pointType) {
		case 'e': // Economy
		case 'E':
			p = PointType.Economy;
			break;
		case 'r':
		case 'R': // Research
			p = PointType.Research;
			break;
		case 'm': // Military
		case 'M':
			p = PointType.Military;
			break;
		default:
			throw new IllegalArgumentException("Unrecognized point type character: " + pointType + ". Use 'e', 'r', or 'm'");
		}
		return of(t, p, metal, crystal, deut, energy, time);
	}

	class SimpleUpgradeCost implements UpgradeCost {
		private final UpgradeType theType;
		private final PointType thePointType;

		private final long theMetal;
		private final long theCrystal;
		private final long theDeuterium;
		private final int theEnergy;
		private final Duration theTime;

		SimpleUpgradeCost(UpgradeType type, PointType pointType, long metal, long crystal, long deuterium, int energy, Duration time) {
			theType = type;
			thePointType = pointType;
			theMetal = metal;
			theCrystal = crystal;
			theDeuterium = deuterium;
			theEnergy = energy;
			theTime = time;
		}

		@Override
		public long getMetal() {
			return theMetal;
		}

		@Override
		public long getCrystal() {
			return theCrystal;
		}

		@Override
		public long getDeuterium() {
			return theDeuterium;
		}

		@Override
		public int getEnergy() {
			return theEnergy;
		}

		@Override
		public Duration getUpgradeTime() {
			return theTime;
		}

		@Override
		public UpgradeCost ofType(UpgradeType type) {
			if (type == theType) {
				return this;
			} else {
				return ZERO;
			}
		}

		@Override
		public long getPoints(PointType type) {
			if (thePointType == type) {
				return getTotal() / 1000;
			} else {
				return 0;
			}
		}

		@Override
		public boolean isZero() {
			return theMetal == 0 && theCrystal == 0 && theDeuterium == 0 && theEnergy == 0 && theTime.isZero();
		}

		@Override
		public SimpleUpgradeCost negate() {
			if (isZero()) {
				return this;
			}
			return new SimpleUpgradeCost(theType, thePointType, -theMetal, -theCrystal, -theDeuterium, -theEnergy,
				theTime == null ? null : theTime.negated());
		}

		@Override
		public UpgradeCost plus(UpgradeCost cost) {
			if (cost == null || cost.isZero()) {
				return this;
			} else if (isZero()) {
				return cost;
			} else if (cost instanceof SimpleUpgradeCost) {
				SimpleUpgradeCost other = (SimpleUpgradeCost) cost;
				if (theType == other.theType && thePointType == other.thePointType) {
					return new SimpleUpgradeCost(theType, thePointType, //
						theMetal+other.theMetal, theCrystal+other.theCrystal, theDeuterium+other.theDeuterium,//
						Math.max(theEnergy, other.theEnergy), add(theTime, other.theTime));
				} else {
					return new CompositeUpgradeCost(this, other);
				}
			} else {
				return cost.plus(this);
			}
		}

		static Duration add(Duration d1, Duration d2) {
			if (d1 == null || d2 == null) {
				return null;
			}
			try {
				return d1.plus(d2);
			} catch (ArithmeticException e) {
				return null;
			}
		}

		@Override
		public SimpleUpgradeCost times(int mult) {
			if (mult == 0) {
				return (SimpleUpgradeCost) ZERO;
			} else if (isZero() || mult == 1) {
				return this;
			} else {
				return new SimpleUpgradeCost(theType, thePointType, theMetal * mult, theCrystal * mult, theDeuterium * mult,
					theEnergy, multiply(theTime, mult));
			}
		}

		static Duration multiply(Duration d, int mult) {
			if (d == null) {
				return null;
			}
			try {
				return d.multipliedBy(mult);
			} catch (ArithmeticException e) {
				return null;
			}
		}

		@Override
		public SimpleUpgradeCost divide(double div) {
			if (div == 1) {
				return this;
			}
			return new SimpleUpgradeCost(theType, thePointType, //
				Math.round(theMetal / div), Math.round(theCrystal / div), Math.round(theDeuterium / div), theEnergy,
				Duration.ofSeconds(Math.round(theTime.getSeconds() / div)));
		}

		@Override
		public int hashCode() {
			return Objects.hash(theType, thePointType, theMetal, theCrystal, theDeuterium, theTime);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			} else if (!(o instanceof SimpleUpgradeCost)) {
				return false;
			}
			SimpleUpgradeCost other = (SimpleUpgradeCost) o;
			return theType == other.theType && thePointType == other.thePointType //
				&& theMetal == other.theMetal && theCrystal == other.theCrystal && theDeuterium == other.theDeuterium//
				&& theTime.equals(other.theTime);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder().append(OGameUtils.printResourceAmount(theMetal)).append(", ")//
				.append(OGameUtils.printResourceAmount(theCrystal)).append(", ")//
				.append(OGameUtils.printResourceAmount(theDeuterium));
			if (theTime == null) {
				str.append(" (unknown time)");
			} else {
				str.append(' ').append(QommonsUtils.printDuration(theTime, true));
			}
			return str.toString();
		}
	}

	class CompositeUpgradeCost implements UpgradeCost {
		private final List<SimpleUpgradeCost> theComponents;

		CompositeUpgradeCost(SimpleUpgradeCost... components) {
			List<SimpleUpgradeCost> list = new ArrayList<>(components.length);
			for (SimpleUpgradeCost c : components) {
				list.add(c);
			}
			theComponents = Collections.unmodifiableList(list);
		}

		CompositeUpgradeCost(List<SimpleUpgradeCost> components) {
			theComponents = Collections.unmodifiableList(components);
		}

		@Override
		public long getMetal() {
			long cost = 0;
			for (UpgradeCost c : theComponents) {
				cost += c.getMetal();
			}
			return cost;
		}

		@Override
		public long getCrystal() {
			long cost = 0;
			for (UpgradeCost c : theComponents) {
				cost += c.getCrystal();
			}
			return cost;
		}

		@Override
		public long getDeuterium() {
			long cost = 0;
			for (UpgradeCost c : theComponents) {
				cost += c.getDeuterium();
			}
			return cost;
		}

		@Override
		public int getEnergy() {
			int cost = 0;
			for (UpgradeCost c : theComponents) {
				cost = Math.max(cost, c.getEnergy());
			}
			return cost;
		}

		@Override
		public Duration getUpgradeTime() {
			Duration time = Duration.ZERO;
			for (UpgradeCost c : theComponents) {
				time = SimpleUpgradeCost.add(time, c.getUpgradeTime());
			}
			return time;
		}

		@Override
		public UpgradeCost ofType(UpgradeType type) {
			UpgradeCost cost = ZERO;
			for (UpgradeCost c : theComponents) {
				cost = cost.plus(c.ofType(type));
			}
			return cost;
		}

		@Override
		public long getPoints(PointType type) {
			long points = 0;
			for (UpgradeCost c : theComponents) {
				points += c.getPoints(type);
			}
			return points;
		}

		@Override
		public boolean isZero() {
			for (UpgradeCost c : theComponents) {
				if (!c.isZero()) {
					return false;
				}
			}
			return true;
		}

		@Override
		public UpgradeCost negate() {
			if (isZero()) {
				return this;
			}
			List<SimpleUpgradeCost> components = new ArrayList<>(theComponents.size() + 1);
			for (SimpleUpgradeCost c : theComponents) {
				components.add(c.negate());
			}
			return new CompositeUpgradeCost(components);
		}

		@Override
		public UpgradeCost plus(UpgradeCost cost) {
			if (cost == null || cost.isZero()) {
				return this;
			} else if (isZero()) {
				return cost;
			} else if (cost instanceof SimpleUpgradeCost) {
				SimpleUpgradeCost other = (SimpleUpgradeCost) cost;
				List<SimpleUpgradeCost> components = new ArrayList<>(theComponents.size() + 1);
				components.addAll(theComponents);
				boolean found = false;
				for (int i = 0; i < theComponents.size(); i++) {
					SimpleUpgradeCost c = theComponents.get(i);
					if (c.theType == other.theType && c.thePointType == other.thePointType) {
						components.set(i, (SimpleUpgradeCost) c.plus(other));
						found = true;
					}
				}
				if (!found) {
					components.add(other);
				}
				return new CompositeUpgradeCost(components);
			} else if (cost instanceof CompositeUpgradeCost) {
				CompositeUpgradeCost other = (CompositeUpgradeCost) cost;
				List<SimpleUpgradeCost> components = new ArrayList<>(theComponents.size() + other.theComponents.size());
				components.addAll(theComponents);
				for (SimpleUpgradeCost comp : other.theComponents) {
					boolean found = false;
					for (int i = 0; i < theComponents.size(); i++) {
						SimpleUpgradeCost c = theComponents.get(i);
						if (c.theType == comp.theType && c.thePointType == comp.thePointType) {
							components.set(i, (SimpleUpgradeCost) c.plus(comp));
							found = true;
						}
					}
					if (!found) {
						components.add(comp);
					}
				}
				return new CompositeUpgradeCost(components);
			} else {
				return cost.plus(this);
			}
		}

		@Override
		public UpgradeCost times(int mult) {
			List<SimpleUpgradeCost> components = new ArrayList<>(theComponents.size());
			for (SimpleUpgradeCost c : theComponents) {
				components.add(c.times(mult));
			}
			return new CompositeUpgradeCost(components);
		}

		@Override
		public UpgradeCost divide(double div) {
			List<SimpleUpgradeCost> components = new ArrayList<>(theComponents.size());
			for (SimpleUpgradeCost c : theComponents) {
				components.add(c.divide(div));
			}
			return new CompositeUpgradeCost(components);
		}

		@Override
		public int hashCode() {
			return theComponents.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof CompositeUpgradeCost && theComponents.equals(((CompositeUpgradeCost) o).theComponents);
		}

		@Override
		public String toString() {
			return new StringBuilder().append(OGameUtils.printResourceAmount(getMetal())).append(", ")//
				.append(OGameUtils.printResourceAmount(getCrystal())).append(", ")//
				.append(OGameUtils.printResourceAmount(getDeuterium())).toString();
		}
	}
}
