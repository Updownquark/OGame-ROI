package org.quark.ogame.uni;

public interface TradeRatios {
	double getMetal();
	TradeRatios setMetal(double metal);

	double getCrystal();
	TradeRatios setCrystal(double crystal);

	double getDeuterium();
	TradeRatios setDeuterium(double deuterium);

	default TradeRatios set(TradeRatios other) {
		return setMetal(other.getMetal()).setCrystal(other.getCrystal()).setDeuterium(other.getDeuterium());
	}
}
