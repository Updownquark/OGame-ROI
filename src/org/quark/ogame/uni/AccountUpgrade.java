package org.quark.ogame.uni;

public class AccountUpgrade {
	public final AccountUpgradeType type;
	public final Planet planet;
	public final int fromLevel;
	public final int toLevel;

	public final UpgradeCost cost;

	public AccountUpgrade(AccountUpgradeType upgradeType, Planet planet, int fromLevel, int toLevel, UpgradeCost cost) {
		this.type = upgradeType;
		this.planet = planet;
		this.fromLevel = fromLevel;
		this.toLevel = toLevel;
		this.cost = cost;
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		} else if (!(o instanceof AccountUpgrade)) {
			return false;
		}
		AccountUpgrade other = (AccountUpgrade) o;
		return type == other.type && planet == other.planet && fromLevel == other.fromLevel && toLevel == other.toLevel;
	}
}
