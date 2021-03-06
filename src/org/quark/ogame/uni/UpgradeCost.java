package org.quark.ogame.uni;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
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

	public static final UpgradeCost ZERO = new SimpleUpgradeCost(UpgradeType.Building, 0, 0, 0, 0, Duration.ZERO, 1, 1, 1);

	public static UpgradeCost of(UpgradeType type, long metal, long crystal, long deut, int energy, Duration time, //
		double ecoAmount, double rsrchAmount, double milAmount) {
		if (metal == 0 && crystal == 0 && deut == 0 && energy == 0 && time.isZero()) {
			return ZERO;
		}
		return new SimpleUpgradeCost(type, metal, crystal, deut, energy, time, ecoAmount, rsrchAmount, milAmount);
	}

	public static UpgradeCost of(char type, long metal, long crystal, long deut, int energy, Duration time, //
		double ecoAmount, double rsrchAmount, double milAmount) {
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
		return of(t, metal, crystal, deut, energy, time, ecoAmount, rsrchAmount, milAmount);
	}

	class SimpleUpgradeCost implements UpgradeCost {
		private final UpgradeType theType;
		private final double[] thePointTypeAmounts;

		private final long theMetal;
		private final long theCrystal;
		private final long theDeuterium;
		private final int theEnergy;
		private final Duration theTime;

		SimpleUpgradeCost(UpgradeType type, long metal, long crystal, long deuterium, int energy, Duration time, //
			double ecoAmount, double rsrchAmount, double milAmount) {
			this(type, metal, crystal, deuterium, energy, time, new double[] { ecoAmount, rsrchAmount, milAmount });
		}

		private SimpleUpgradeCost(UpgradeType type, long metal, long crystal, long deuterium, int energy, Duration time,
			double[] pointTypeAmounts) {
			theType = type;
			thePointTypeAmounts = pointTypeAmounts;
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
			if (type == PointType.Total || thePointTypeAmounts[type.ordinal() - 1] == 1) {
				return Math.round(getTotal() / 1000.0);
			} else if (thePointTypeAmounts[type.ordinal() - 1] == 0) {
				return 0;
			} else {
				return Math.round(getTotal() * thePointTypeAmounts[type.ordinal() - 1] / 1000);
			}
		}

		@Override
		public boolean isZero() {
			if (this == ZERO) {
				return true;
			}
			return theMetal == 0 && theCrystal == 0 && theDeuterium == 0 && theEnergy == 0 && theTime.isZero();
		}

		@Override
		public SimpleUpgradeCost negate() {
			if (isZero()) {
				return this;
			}
			return new SimpleUpgradeCost(theType, -theMetal, -theCrystal, -theDeuterium, -theEnergy,
				theTime == null ? null : theTime.negated(), thePointTypeAmounts);
		}

		@Override
		public UpgradeCost plus(UpgradeCost cost) {
			if (cost == null || cost.isZero()) {
				return this;
			} else if (isZero()) {
				return cost;
			} else if (cost instanceof SimpleUpgradeCost) {
				SimpleUpgradeCost other = (SimpleUpgradeCost) cost;
				if (theType == other.theType && Arrays.equals(thePointTypeAmounts, other.thePointTypeAmounts)) {
					return new SimpleUpgradeCost(theType, //
						theMetal+other.theMetal, theCrystal+other.theCrystal, theDeuterium+other.theDeuterium,//
						Math.max(theEnergy, other.theEnergy), add(theTime, other.theTime), thePointTypeAmounts);
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
				return new SimpleUpgradeCost(theType, theMetal * mult, theCrystal * mult, theDeuterium * mult, theEnergy,
					multiply(theTime, mult), thePointTypeAmounts);
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
			return new SimpleUpgradeCost(theType, //
				Math.round(theMetal / div), Math.round(theCrystal / div), Math.round(theDeuterium / div), theEnergy,
				Duration.ofSeconds(Math.round(theTime.getSeconds() / div)), thePointTypeAmounts);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theType, theMetal, theCrystal, theDeuterium, theTime, thePointTypeAmounts);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			} else if (!(o instanceof SimpleUpgradeCost)) {
				return false;
			}
			SimpleUpgradeCost other = (SimpleUpgradeCost) o;
			return theType == other.theType //
				&& theMetal == other.theMetal && theCrystal == other.theCrystal && theDeuterium == other.theDeuterium//
				&& theTime.equals(other.theTime) && Arrays.equals(thePointTypeAmounts, other.thePointTypeAmounts);
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
					if (c.theType == other.theType && Arrays.equals(c.thePointTypeAmounts, other.thePointTypeAmounts)) {
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
						if (c.theType == comp.theType && Arrays.equals(c.thePointTypeAmounts, comp.thePointTypeAmounts)) {
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
