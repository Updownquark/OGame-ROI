package org.quark.ogame.uni.versions;

import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.Planet;
import org.quark.ogame.uni.ResourceType;

public class OGameEconomy740 extends OGameEconomy711 {
	@Override
	protected double getSlotProductionMultiplier(Account account, Planet planet, ResourceType resource) {
		if (resource == ResourceType.Crystal) {
			double crystalMult;
			if (planet.getCoordinates().getSlot() == 0) {
				// If the coordinates haven't been entered, go by temperature,
				// since I believe the temperature ranges of slots 1-3 are distinct
				if (planet.getMinimumTemperature() >= 200) { // Slot 1
					crystalMult = 0.4;
				} else if (planet.getMinimumTemperature() >= 150) { // Slot 2
					crystalMult = 0.3;
				} else if (planet.getMinimumTemperature() >= 100) { // Slot 3
					crystalMult = 0.2;
				} else {
					crystalMult = 0;
				}
			} else if (planet.getCoordinates().getSlot() == 1) {
				crystalMult = 0.4;
			} else if (planet.getCoordinates().getSlot() == 2) {
				crystalMult = 0.3;
			} else if (planet.getCoordinates().getSlot() == 3) {
				crystalMult = 0.2;
			} else {
				crystalMult = 0;
			}
			return crystalMult;
		} else {
			return super.getSlotProductionMultiplier(account, planet, resource);
		}
	}
}
