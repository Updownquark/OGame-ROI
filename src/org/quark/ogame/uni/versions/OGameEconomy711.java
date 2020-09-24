package org.quark.ogame.uni.versions;

import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.Planet;
import org.quark.ogame.uni.ResourceType;

public class OGameEconomy711 extends OGameEconomy710 {
	@Override
	public Production getProduction(Account account, Planet planet, ResourceType resourceType, double energyFactor) {
		Production superP = super.getProduction(account, planet, resourceType, energyFactor);
		if (resourceType == ResourceType.Crystal) {
			double crystalMult;
			if (planet.getCoordinates().getSlot() == 0) {
				// If the coordinates haven't been entered, go by temperature,
				// since I believe the temperature ranges of slots 1-3 are distinct
				if (planet.getMinimumTemperature() >= 200) { // Slot 1
					crystalMult = 1.3;
				} else if (planet.getMinimumTemperature() >= 150) { // Slot 2
					crystalMult = 1.225;
				} else if (planet.getMinimumTemperature() >= 100) { // Slot 3
					crystalMult = 1.15;
				} else {
					return superP;
				}
			} else if (planet.getCoordinates().getSlot() == 1) {
				crystalMult = 1.3;
			} else if (planet.getCoordinates().getSlot() == 2) {
				crystalMult = 1.225;
			} else if (planet.getCoordinates().getSlot() == 3) {
				crystalMult = 1.15;
			} else {
				return superP;
			}
			return superP.plus((int) (superP.byType.get(ProductionSource.Base) * crystalMult), ProductionSource.Slot);
		} else {
			return superP;
		}
	}
}
