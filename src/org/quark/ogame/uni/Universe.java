package org.quark.ogame.uni;

import org.qommons.Nameable;

public interface Universe extends Nameable {
	int getEconomySpeed();
	void setEconomySpeed(int economySpeed);

	int getResearchSpeed();
	void setResearchSpeed(int researchSpeed);

	int getCollectorProductionBonus();
	void setCollectorProductionBonus(int collectorBonus);

	int getCollectorEnergyBonus();
	void setCollectorEnergyBonus(int collectorEnergyBonus);

	double getHyperspaceCargoBonus();
	void setHyperspaceCargoBonus(double cargoBonus);

	int getFleetSpeed();
	void setFleetSpeed(int fleetSpeed);

	int getCrawlerCap();
	void setCrawlerCap(int crawlerCap);

	TradeRatios getTradeRatios();
}
