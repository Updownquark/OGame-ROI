package org.quark.ogame.uni.versions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.AccountClass;
import org.quark.ogame.uni.FleetRules;
import org.quark.ogame.uni.ShipyardItemType;

public class OGameFleet710 implements FleetRules {
	static class ShipInfo {
		final int baseCargo;

		ShipInfo(int baseCargo) {
			this.baseCargo = baseCargo;
		}
	}

	private static Map<ShipyardItemType, ShipInfo> SHIP_INFO;

	static {
		Map<ShipyardItemType, ShipInfo> shipInfo = new LinkedHashMap<>();
		for (ShipyardItemType type : ShipyardItemType.values()) {
			switch (type) {
			case SmallCargo:
				shipInfo.put(type, new ShipInfo(5000));
				break;
			case LargeCargo:
				shipInfo.put(type, new ShipInfo(25000));
				break;
			case Recycler:
				shipInfo.put(type, new ShipInfo(20000));
				break;
			}
		}
		SHIP_INFO = Collections.unmodifiableMap(shipInfo);
	}

	@Override
	public int getCargoSpace(ShipyardItemType type, Account account) {
		ShipInfo info = SHIP_INFO.get(type);
		if (info == null) {
			return 0;
		}
		double bonus = 0;
		switch (type) {
		case SmallCargo:
		case LargeCargo:
			if (account.getGameClass() == AccountClass.Collector) {
				bonus += .25;
			}
			break;
		default:
			break;
		}
		bonus += account.getUniverse().getHyperspaceCargoBonus() / 100 * account.getResearch().getHyperspace();
		return (int) Math.floor(info.baseCargo * (1 + bonus));
	}
}
