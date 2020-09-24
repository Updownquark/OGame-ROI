package org.quark.ogame.uni.versions;

import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.AccountClass;
import org.quark.ogame.uni.Planet;
import org.quark.ogame.uni.ResourceType;

public class OGameEconomy750 extends OGameEconomy711 {
	@Override
	public int getMaxCrawlers(Account account, Planet planet) {
		int superMax = super.getMaxCrawlers(account, planet);
		if (account.getGameClass() == AccountClass.Collector && account.getOfficers().isGeologist()) {
			superMax += superMax / 10;
		}
		return superMax;
	}

	@Override
	public int getMaxCrawlerUtilization(Account account) {
		if (account.getGameClass() == AccountClass.Collector) {
			return 150;
		} else {
			return super.getMaxCrawlerUtilization(account);
		}
	}

	@Override
	public Production getProduction(Account account, Planet planet, ResourceType resourceType, double energyFactor) {
		Production superP = super.getProduction(account, planet, resourceType, energyFactor);
		if (resourceType == ResourceType.Metal) {
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
			}
			return superP.plus((int) (superP.byType.get(ProductionSource.Base) * metalMult), ProductionSource.Slot);
		}
		return superP;
	}
}
