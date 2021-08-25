package org.quark.ogame.uni.versions;

import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.AllianceClass;
import org.quark.ogame.uni.ShipyardItemType;

public class OGameFleet800pl7 extends OGameFleet710 {
	public OGameFleet800pl7() {
		for (ShipyardItemType ship : ShipyardItemType.values()) {
			switch (ship) {
			case LargeCargo:
			case SmallCargo:
				modifyShipInfo(ship, info -> info.withClassBonus(AllianceClass.Trader, 10, 0, 0));
				break;
			default:
				break;
			}
		}
	}

	@Override
	public int getSpeed(ShipyardItemType type, Account account, boolean expedition, boolean allianceMember) {
		int speed = super.getSpeed(type, account, expedition, allianceMember);
		double speedMult = 1;
		if (account.getAllianceClass() != null) {
			switch (account.getAllianceClass()) {
			case None:
				break;
			case Researcher:
				if (expedition) {
					speedMult += 0.1;
				}
				break;
			case Warrior:
				if (allianceMember) {
					speedMult += 0.1;
				}
				break;
			case Trader:
				break;
			}
		}
		if (speedMult != 1) {
			speed = (int) Math.round(speed * speedMult);
		}
		return speed;
	}
}
