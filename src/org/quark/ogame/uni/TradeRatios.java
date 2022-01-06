package org.quark.ogame.uni;

public interface TradeRatios {
	public static TradeRatios DEFAULT = new TradeRatios() {
		@Override
		public double getMetal() {
			return 2.5;
		}

		@Override
		public TradeRatios setMetal(double metal) {
			return this;
		}

		@Override
		public double getCrystal() {
			return 1.5;
		}

		@Override
		public TradeRatios setCrystal(double crystal) {
			return this;
		}

		@Override
		public double getDeuterium() {
			return 1;
		}

		@Override
		public TradeRatios setDeuterium(double deuterium) {
			return this;
		}
	};

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
