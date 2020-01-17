package org.quark.ogame.uni;

public interface ShipyardItemSet {
	int getItems(ShipyardItemType type);
	ShipyardItemSet setItems(ShipyardItemType type, int number);
}
