package org.quark.ogame.uni;

import org.qommons.Nameable;

public interface PlannedFlight extends Nameable {
	Fleet getFleet();

	int getSourceGalaxy();
	PlannedFlight setSourceGalaxy(int sourceGalaxy);
	int getSourceSystem();
	PlannedFlight setSourceSystem(int sourceSystem);
	int getSourceSlot();
	PlannedFlight setSourceSlot(int sourceSlot);
	boolean isSourceMoon();
	PlannedFlight setSourceMoon(boolean sourceMoon);

	int getDestGalaxy();
	PlannedFlight setDestGalaxy(int destGalaxy);
	int getDestSystem();
	PlannedFlight setDestSystem(int destSystem);
	int getDestSlot();
	PlannedFlight setDestSlot(int destSlot);
	boolean isDestMoon();
	PlannedFlight setDestMoon(boolean destMoon);

	int getSpeed();
	PlannedFlight setSpeed(int speed);
}
