package org.quark.ogame.uni.versions;

import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.AccountClass;
import org.quark.ogame.uni.Planet;
import org.quark.ogame.uni.ResourceType;
import org.quark.ogame.uni.Utilizable;

public class OGameEconomy750 extends OGameEconomy740 {
	@Override
	public int getMaxCrawlers(Account account, Planet planet) {
		int superMax = super.getMaxCrawlers(account, planet);
		if (account.getGameClass() == AccountClass.Collector && account.getOfficers().isGeologist()) {
			superMax += superMax / 10;
		}
		return superMax;
	}

	@Override
	public int getMaxUtilization(Utilizable utilizable, Account account, Planet planet) {
		if (utilizable == Utilizable.Crawler && account.getGameClass() == AccountClass.Collector) {
			return 150;
		} else {
			return super.getMaxUtilization(utilizable, account, planet);
		}
	}

	@Override
	protected double getSlotProductionMultiplier(Account account, Planet planet, ResourceType resource) {
		if (resource == ResourceType.Metal) {
			double metalMult;
			switch (planet.getCoordinates().getSlot()) {
			case 6:
			case 10:
				metalMult = .17;
				break;
			case 7:
			case 9:
				metalMult = .23;
				break;
			case 8:
				metalMult = .35;
				break;
			default:
				metalMult = 0;
				break;
			}
			return metalMult;
		} else {
			return super.getSlotProductionMultiplier(account, planet, resource);
		}
	}
}
