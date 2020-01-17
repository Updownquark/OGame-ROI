package org.quark.ogame.uni;

public class AccountUpgrade {
	private final AccountUpgradeType type;
	private final Planet planet;
	private final boolean isMoon;
	private final int fromLevel;
	private final int toLevel;

	private final UpgradeCost cost;

	public AccountUpgrade(AccountUpgradeType upgradeType, Planet planet, boolean moon, int fromLevel, int toLevel, UpgradeCost cost) {
		this.type = upgradeType;
		this.planet = planet;
		this.isMoon = moon;
		this.fromLevel = fromLevel;
		this.toLevel = toLevel;
		this.cost = cost;
	}

	public AccountUpgradeType getType() {
		return type;
	}

	public Planet getPlanet() {
		return planet;
	}

	public boolean isMoon() {
		return isMoon;
	}

	public int getFromLevel() {
		return fromLevel;
	}

	public int getToLevel() {
		return toLevel;
	}

	public UpgradeCost getCost() {
		return cost;
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		} else if (!(o instanceof AccountUpgrade)) {
			return false;
		}
		AccountUpgrade other = (AccountUpgrade) o;
		return getType() == other.getType() && getPlanet() == other.getPlanet() && isMoon() == other.isMoon()
			&& getFromLevel() == other.getFromLevel() && getToLevel() == other.getToLevel();
	}
}
