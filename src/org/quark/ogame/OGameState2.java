package org.quark.ogame;

import java.util.LinkedList;
import java.util.List;

import org.observe.config.ObservableConfig;
import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.AccountUpgradeType;
import org.quark.ogame.uni.BuildingType;
import org.quark.ogame.uni.OGameEconomyRuleSet;
import org.quark.ogame.uni.OGameEconomyRuleSet.Production;
import org.quark.ogame.uni.OGameRuleSet;
import org.quark.ogame.uni.Planet;
import org.quark.ogame.uni.ResourceType;
import org.quark.ogame.uni.ShipyardItemType;
import org.quark.ogame.uni.UpgradeCost;
import org.quark.ogame.uni.UpgradeType;
import org.quark.ogame.uni.ui.OGameUniGui;

public class OGameState2 {
	public static interface Upgrade {
		int effect();

		UpgradeCost getCost();

		void undo();
	}

	private final OGameRuleSet theRules;
	private final Account theAccount;
	private final Planet thePlanet;
	private int thePlanetCount;
	private UpgradeCost theAccountValue;

	public OGameState2(OGameRuleSet rules) {
		theRules = rules;
		// The easiest way to synthesize the entity is to back it with a config
		ObservableConfig config = ObservableConfig.createRoot("ogame-roi");
		theAccount = OGameUniGui.getAccounts(config, "state/account").create().create().get();
		thePlanet = theAccount.getPlanets().create().create().get(); // Add the initial planet
		thePlanet.setMetalUtilization(100).setCrystalUtilization(100).setDeuteriumUtilization(100)//
			.setSolarPlantUtilization(100).setFusionReactorUtilization(100).setSolarSatelliteUtilization(100).setCrawlerUtilization(100);
		thePlanetCount = 1;
		theAccountValue = UpgradeCost.ZERO;
	}

	public Account getAccount() {
		return theAccount;
	}

	public Planet getPlanet() {
		return thePlanet;
	}

	public int getPlanetCount() {
		return thePlanetCount;
	}

	public UpgradeCost getAccountValue() {
		return theAccountValue;
	}

	public Production getEnergy() {
		return theRules.economy().getProduction(theAccount, thePlanet, ResourceType.Energy, 1);
	}

	public long[] getProduction() {
		Production energy = theRules.economy().getProduction(theAccount, thePlanet, ResourceType.Energy, 1);
		double energyFactor = energy.totalConsumption == 0 ? 1 : Math.min(1.0, energy.totalProduction * 1.0 / energy.totalConsumption);
		Production metal = theRules.economy().getProduction(theAccount, thePlanet, ResourceType.Metal, energyFactor);
		Production crystal = theRules.economy().getProduction(theAccount, thePlanet, ResourceType.Crystal, energyFactor);
		Production deuterium = theRules.economy().getProduction(theAccount, thePlanet, ResourceType.Deuterium, energyFactor);
		return new long[] { metal.totalNet * thePlanetCount, crystal.totalNet * thePlanetCount, deuterium.totalNet * thePlanetCount };
	}

	class UpgradeSeqElement {
		final AccountUpgradeType type;
		final int levels;
		final boolean notional;

		UpgradeSeqElement(AccountUpgradeType type, int levels, List<UpgradeSeqElement> sequence) {
			this.type = type;
			this.levels = levels;
			notional = false;
			addRequirements(sequence);
		}

		UpgradeSeqElement(AccountUpgradeType type, int levels, boolean notional, List<UpgradeSeqElement> sequence) {
			this.type = type;
			this.levels = levels;
			this.notional = notional;
			addRequirements(sequence);
		}

		UpgradeSeqElement addRequirements(List<UpgradeSeqElement> sequence) {
			int preLevel = notional ? 0 : getLevel(type, sequence);
			if (preLevel == 0) {
				for (OGameEconomyRuleSet.Requirement requirement : theRules.economy().getRequirements(type)) {
					int preReqLevel = getLevel(requirement.type, sequence);
					if (preReqLevel < requirement.level) {
						int preSeqSize = sequence.size();
						sequence.add(new UpgradeSeqElement(requirement.type, requirement.level - preReqLevel, notional, sequence));
						for (int i = preSeqSize; i < sequence.size(); i++) {
							if (sequence.get(i).type == type) {
								preLevel += sequence.get(i).levels;
							}
						}
					}
				}
			}
			return this;
		}

		private int getLevel(AccountUpgradeType targetType, List<UpgradeSeqElement> sequence) {
			int level = notional ? 0 : targetType.getLevel(theAccount, thePlanet);
			for (UpgradeSeqElement el : sequence) {
				if (el.type == targetType) {
					level += el.levels;
				}
			}
			return level;
		}

		UpgradeCost getCost() {
			int preLevel = notional ? 0 : type.getLevel(theAccount, thePlanet);
			UpgradeCost cost = theRules.economy().getUpgradeCost(theAccount, thePlanet, type, preLevel, preLevel + levels);
			if (!notional && type.type != UpgradeType.Research) {
				cost = cost.times(thePlanetCount);
			}
			return cost;
		}

		void upgrade() {
			if (notional) {
				return;
			}
			int preLevel = type.getLevel(theAccount, thePlanet);
			type.setLevel(theAccount, thePlanet, preLevel + levels);
			if (type == AccountUpgradeType.Astrophysics) {
				thePlanetCount++;
			}
		}

		void downgrade() {
			if (notional) {
				return;
			}
			int upgradedLevel = type.getLevel(theAccount, thePlanet);
			type.setLevel(theAccount, thePlanet, upgradedLevel - levels);
			if (type == AccountUpgradeType.Astrophysics) {
				thePlanetCount--;
			}
		}

		@Override
		public String toString() {
			return type + " " + levels;
		}
	}

	public Upgrade upgrade(OGameImprovementType type) {
		final List<UpgradeSeqElement> seq = new LinkedList<>();
		boolean affectsUtilization = false;
		int newLevel = 0;
		switch (type) {
		case Metal:
			newLevel = thePlanet.getMetalMine() + 1;
			seq.add(new UpgradeSeqElement(AccountUpgradeType.MetalMine, 1, seq));
			affectsUtilization = true;
			break;
		case Crystal:
			newLevel = thePlanet.getCrystalMine() + 1;
			seq.add(new UpgradeSeqElement(AccountUpgradeType.CrystalMine, 1, seq));
			affectsUtilization = true;
			break;
		case Deut:
			newLevel = thePlanet.getDeuteriumSynthesizer() + 1;
			seq.add(new UpgradeSeqElement(AccountUpgradeType.DeuteriumSynthesizer, 1, seq));
			affectsUtilization = true;
			break;
		case Planet:
			newLevel = thePlanetCount + 1;
			seq.add(new UpgradeSeqElement(AccountUpgradeType.Astrophysics, thePlanetCount == 1 ? 1 : 2, seq));
			for (BuildingType b : BuildingType.values()) {
				int level = thePlanet.getBuildingLevel(b);
				if (level > 0) {
					seq.add(new UpgradeSeqElement(AccountUpgradeType.getBuildingUpgrade(b), level, true, seq));
				}
			}
			for (ShipyardItemType s : ShipyardItemType.values()) {
				int level = thePlanet.getStationedShips(s);
				if (level > 0) {
					seq.add(new UpgradeSeqElement(AccountUpgradeType.getShipyardItemUpgrade(s), level, true, seq));
				}
			}
			break;
		case Plasma:
			newLevel = theAccount.getResearch().getPlasma() + 1;
			seq.add(new UpgradeSeqElement(AccountUpgradeType.Plasma, 1, seq));
			break;
		case Crawler:
			newLevel = thePlanet.getCrawlers() + 1;
			seq.add(new UpgradeSeqElement(AccountUpgradeType.Crawler, 1, seq));
			affectsUtilization = true;
			break;
		case Fusion:
			newLevel = thePlanet.getFusionReactor() + 1;
			seq.add(new UpgradeSeqElement(AccountUpgradeType.FusionReactor, 1, seq));
			affectsUtilization = true;
			break;
		case Energy:
			newLevel = theAccount.getResearch().getEnergy() + 1;
			seq.add(new UpgradeSeqElement(AccountUpgradeType.Energy, 1, seq));
			affectsUtilization = true;
			break;
		case Robotics:
			newLevel = thePlanet.getRoboticsFactory() + 1;
			seq.add(new UpgradeSeqElement(AccountUpgradeType.RoboticsFactory, 1, seq));
			break;
		case Nanite:
			newLevel = thePlanet.getNaniteFactory() + 1;
			seq.add(new UpgradeSeqElement(AccountUpgradeType.NaniteFactory, 1, seq));
			break;
		case ResearchLab:
			newLevel = thePlanet.getResearchLab() + 1;
			seq.add(new UpgradeSeqElement(AccountUpgradeType.ResearchLab, 1, seq));
			break;
		case IRN:
			newLevel = theAccount.getResearch().getIntergalacticResearchNetwork() + 1;
			seq.add(new UpgradeSeqElement(AccountUpgradeType.IntergalacticResearchNetwork, 1, seq));
			break;
		case MetalStorage:
			newLevel = thePlanet.getMetalStorage() + 1;
			seq.add(new UpgradeSeqElement(AccountUpgradeType.MetalStorage, 1, seq));
			break;
		case CrystalStorage:
			newLevel = thePlanet.getCrystalStorage() + 1;
			seq.add(new UpgradeSeqElement(AccountUpgradeType.CrystalStorage, 1, seq));
			break;
		case DeutStorage:
			newLevel = thePlanet.getDeuteriumStorage() + 1;
			seq.add(new UpgradeSeqElement(AccountUpgradeType.DeuteriumStorage, 1, seq));
			break;
		}

		return testUpgrade(seq, affectsUtilization, newLevel);
	}

	private Upgrade testUpgrade(List<UpgradeSeqElement> sequence, boolean affectsUtilization, int newLevel) {
		UpgradeCost tempCost = UpgradeCost.ZERO;
		for (UpgradeSeqElement el : sequence) {
			tempCost = tempCost.plus(el.getCost());
			el.upgrade();
		}

		int preSats = thePlanet.getSolarSatellites();
		int preFusionUtilization = thePlanet.getFusionReactorUtilization();
		if (affectsUtilization) {
			int satEnergy = theRules.economy().getSatelliteEnergy(theAccount, thePlanet);
			// Assume that the best production is always with mines running at 100%
			thePlanet.setSolarSatellites(0);
			Production energy;
			if (thePlanet.getFusionReactor() > 0) {
				thePlanet.setFusionReactorUtilization(100);
				energy = theRules.economy().getProduction(theAccount, thePlanet, ResourceType.Energy, 1);
				int production = energy.totalProduction;
				int consumption = energy.totalConsumption;
				if (production > consumption) {
					thePlanet.setFusionReactorUtilization((int) Math.ceil(consumption * 1.0 / production * 10) * 10);
				}
			} else {
				energy = theRules.economy().getProduction(theAccount, thePlanet, ResourceType.Energy, 1);
			}
			double requiredEnergy = energy.totalConsumption - energy.totalProduction;
			thePlanet.setSolarSatellites(Math.max(0, (int) Math.ceil(requiredEnergy / satEnergy)));
		}
		UpgradeCost cost = tempCost;
		return new Upgrade() {
			@Override
			public int effect() {
				theAccountValue = theAccountValue.plus(cost);
				return newLevel;
			}

			@Override
			public UpgradeCost getCost() {
				return cost;
			}

			@Override
			public void undo() {
				for (int i = sequence.size() - 1; i >= 0; i--) {
					sequence.get(i).downgrade();
				}
				if (affectsUtilization) {
					thePlanet.setSolarSatellites(preSats);
					thePlanet.setFusionReactorUtilization(preFusionUtilization);
				}
			}
		};
	}
}
