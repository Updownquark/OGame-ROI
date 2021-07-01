package org.quark.ogame.uni;

import java.time.Duration;

public interface FleetRules {
	int getCargoSpace(ShipyardItemType type, Account account);

	int getDistance(Universe universe, int sourceGalaxy, int sourceSystem, int sourceSlot, int destGalaxy, int destSystem, int destSlot);

	int getFuelConsumption(ShipyardItemType type, Account account);

	int getSpeed(ShipyardItemType type, Account account, boolean expedition, boolean allianceMember);

	Duration getFlightTime(int maxSpeed, int distance, int speedPercent);

	double getFuelConsumption(ShipyardItemType type, Account account, int distance, Duration flightTime);
}
