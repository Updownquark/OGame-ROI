package org.quark.ogame.uni;

import org.qommons.Nameable;

public interface Universe extends Nameable {
	int getEconomySpeed();
	Universe setEconomySpeed(int economySpeed);

	int getResearchSpeed();
	Universe setResearchSpeed(int researchSpeed);

	int getCollectorProductionBonus();
	Universe setCollectorProductionBonus(int collectorBonus);

	int getCollectorEnergyBonus();
	Universe setCollectorEnergyBonus(int collectorEnergyBonus);

	double getHyperspaceCargoBonus();
	Universe setHyperspaceCargoBonus(double cargoBonus);

	int getFleetSpeed();
	Universe setFleetSpeed(int fleetSpeed);

	int getCrawlerCap();
	Universe setCrawlerCap(int crawlerCap);

	TradeRatios getTradeRatios();

	int getGalaxies();
	Universe setGalaxies(int galaxies);

	boolean isCircularUniverse();
	Universe setCircularUniverse(boolean circular);

	boolean isCircularGalaxies();
	Universe setCircularGalaxies(boolean circular);
}
