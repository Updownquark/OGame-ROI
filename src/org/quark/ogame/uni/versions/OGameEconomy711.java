package org.quark.ogame.uni.versions;

import java.util.EnumMap;
import java.util.Map;

import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.Planet;
import org.quark.ogame.uni.ResourceType;

public class OGameEconomy711 extends OGameEconomy710 {
	@Override
	public Production getProduction(Account account, Planet planet, ResourceType resourceType, double energyFactor) {
		Production superP = super.getProduction(account, planet, resourceType, energyFactor);
		if (resourceType == ResourceType.Crystal) {
			double crystalMult;
			if (planet.getMinimumTemperature() >= 200) { // Slot 1
				crystalMult = 1.3;
			} else if (planet.getMinimumTemperature() >= 150) { // Slot 2
				crystalMult = 1.225;
			} else if (planet.getMinimumTemperature() >= 100) { // Slot 3
				crystalMult = 1.15;
			} else {
				return superP;
			}
			Map<ProductionSource, Integer> byType = new EnumMap<>(ProductionSource.class);
			for (ProductionSource source : ProductionSource.values()) {
				Integer value = superP.byType.get(source);
				if (value != null) {
					value = (int) (value * crystalMult);
					byType.put(source, value);
				}
			}
			return new Production(byType, (int) (superP.totalProduction * crystalMult), superP.totalConsumption);
		} else {
			return superP;
		}
	}
}
