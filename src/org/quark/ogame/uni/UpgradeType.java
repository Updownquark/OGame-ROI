package org.quark.ogame.uni;

public enum UpgradeType {
	Building(BuildingType.class), Research(ResearchType.class), ShipyardItem(ShipyardItemType.class);

	public final Class<? extends Enum<?>> subType;

	private UpgradeType(Class<? extends Enum<?>> subType) {
		this.subType = subType;
	}
}
