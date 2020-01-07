package org.quark.ogame.uni;

import java.util.List;
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

	public class Requirement {
		public final AccountUpgradeType type;
		public final int level;

		public Requirement(AccountUpgradeType type, int level) {
			this.type = type;
			this.level = level;
		}
	}

	Production getProduction(Account account, Planet planet, ResourceType resourceType, double energyFactor);
	int getSatelliteEnergy(Account account, Planet planet);
	long getStorage(Planet planet, ResourceType resourceType);
	int getMaxCrawlers(Account account, Planet planet);

	UpgradeCost getUpgradeCost(Account account, RockyBody planetOrMoon, AccountUpgradeType upgrade, int fromLevel, int toLevel);
	List<Requirement> getRequirements(AccountUpgradeType target);

	int getFields(Planet planet);
	int getFields(Moon moon);
}
