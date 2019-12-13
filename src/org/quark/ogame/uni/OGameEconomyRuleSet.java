package org.quark.ogame.uni;

import java.util.Map;

public interface OGameEconomyRuleSet {
	public enum ProductionSource {
		Base,
		MetalMine,
		CrystalMine,
		DeuteriumSynthesizer,
		Solar,
		Fusion,
		Satellite,
		Crawler,
		Plasma,
		Item,
		Geologist,
		Engineer,
		CommandingStaff,
		Collector;
	}

	public class Production {
		public final Map<ProductionSource, Integer> byType;
		public final int totalProduction;
		public final int totalConsumption;
		public final int totalNet;

		public Production(Map<ProductionSource, Integer> byType, int produced, int consumed) {
			this.byType = byType;
			this.totalProduction = produced;
			this.totalConsumption = consumed;
			this.totalNet = produced - consumed;
		}
	}

	Production getProduction(Account account, Planet planet, ResourceType resourceType, double energyFactor);
	long getStorage(Planet planet, ResourceType resourceType);

	UpgradeCost getUpgradeCost(Account account, RockyBody planetOrMoon, AccountUpgrade upgrade, int fromLevel, int toLevel);

	int getFields(Planet planet);
	int getFields(Moon moon);
}
