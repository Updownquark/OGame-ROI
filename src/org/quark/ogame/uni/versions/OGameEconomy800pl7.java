package org.quark.ogame.uni.versions;

import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.AllianceClass;
import org.quark.ogame.uni.Planet;
import org.quark.ogame.uni.ResourceType;

public class OGameEconomy800pl7 extends OGameEconomy750 {
	@Override
	public synchronized long getStorage(Planet planet, ResourceType resourceType) {
		long storage = super.getStorage(planet, resourceType);
		if (planet.getAccount().getAllianceClass() != null) {
			switch (planet.getAccount().getAllianceClass()) {
			case Trader:
			case Researcher:
				storage = Math.round(storage * 1.1);
				break;
			case Warrior:
			case None:
				break;
			}
		}
		return storage;
	}

	@Override
	public Production getProduction(Account account, Planet planet, ResourceType resourceType, double energyFactor) {
		Production production = super.getProduction(account, planet, resourceType, energyFactor);
		if (account.getAllianceClass() == AllianceClass.Trader) {
			switch (resourceType) {
			case Metal:
			case Crystal:
			case Deuterium:
				int mineProduction = production.byType.get(ProductionSource.getMine(resourceType))//
					+ production.byType.get(ProductionSource.Slot);
				production = production.plus((int) Math.round(mineProduction * 0.05), ProductionSource.Trader);
				break;
			case Energy:
				long energyProduction = 0;
				for (ProductionSource src : ProductionSource.values()) {
					switch (src) {
					case Base:
					case Solar:
					case Satellite:
					case Fusion:
					case Slot:
						energyProduction += production.byType.getOrDefault(src, 0);
						break;
					default:
						break;
					}
				}
				production = production.plus((int) Math.round(energyProduction * 0.05), ProductionSource.Trader);
				break;
			}
		}
		return production;
	}
}
