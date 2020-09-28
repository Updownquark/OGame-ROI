package org.quark.ogame.uni.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import javax.swing.JPanel;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryRenderStrategy;
import org.observe.util.swing.ObservableSwingUtils;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.TimeUtils;
import org.qommons.io.SpinnerFormat;
import org.quark.ogame.OGameUtils;
import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.AccountClass;
import org.quark.ogame.uni.AccountUpgradeType;
import org.quark.ogame.uni.OGameEconomyRuleSet.Production;
import org.quark.ogame.uni.OGameEconomyRuleSet.ProductionSource;
import org.quark.ogame.uni.Planet;
import org.quark.ogame.uni.ResourceType;
import org.quark.ogame.uni.ShipyardItemType;
import org.quark.ogame.uni.Utilizable;

public class ResourceSettingsPanel extends JPanel {
	private final OGameUniGui theUniGui;
	private final Map<Integer, ObservableCollection<String>> thePercentages;

	public ResourceSettingsPanel(OGameUniGui uniGui) {
		theUniGui = uniGui;
		thePercentages = new HashMap<>();
	}

	ObservableCollection<String> getPercentages(int max) {
		return thePercentages.computeIfAbsent(max, __ -> {
			List<String> pc = new ArrayList<>(max / 10 + 1);
			for (int i = max; i >= 0; i -= 10) {
				pc.add(i + "%");
			}
			return ObservableCollection.of(TypeTokens.get().STRING, pc);
		});
	}

	public void addPanel(PanelPopulator<?, ?> panel) {
		SettableValue<PlanetWithProduction> selectedPlanet = theUniGui.getSelectedPlanet();
		panel.addComboField("Planet:", selectedPlanet, theUniGui.getPlanets(), null)//
			.addSplit(false,
				split -> split.fill().fillV().visibleWhen(selectedPlanet.map(p -> p != null && p.planet != null))//
					.withSplitProportion(.5)//
					.firstV(left -> configureResourceSettingsPanel(left, false))//
					.lastV(right -> configureResourceSettingsPanel(right, true)));
	}

	private void configureResourceSettingsPanel(PanelPopulator<?, ?> panel, boolean goals) {
		SettableValue<PlanetWithProduction> selectedPlanet = theUniGui.getSelectedPlanet();
		panel.fill().fillV()//
			.addComponent(null, ObservableSwingUtils.label(goals ? "Goals" : "Current").bold().withFontSize(16).label, null)//
			.addTable(
				ObservableCollection.of(TypeTokens.get().of(ResourceRow.class), ResourceRow.values()).flow()
					.refresh(selectedPlanet.noInitChanges()).collect(),
				resTable -> resTable.fill()//
					.withColumn("Type", ResourceRow.class, t -> t, typeCol -> typeCol.withWidths(100, 100, 100))//
					.withColumn(
						resourceColumn("", int.class, goals, (p, row) -> getPSValue(p, row, goals), this::setPSValue, selectedPlanet, 0, 35)
							.formatText((row, v) -> renderResourceRow(row, v))//
							.withMutation(m -> m.asText(SpinnerFormat.INT).editableIf((row, v) -> !goals && canEditPSValue(row))))//
					.withColumn(resourceColumn("Metal", String.class, goals,
						(planet, row) -> printProductionBySource(planet, row, ResourceType.Metal, goals), null, selectedPlanet, "0", 55))//
					.withColumn(resourceColumn("Crystal", String.class, goals,
						(planet, row) -> printProductionBySource(planet, row, ResourceType.Crystal, goals), null, selectedPlanet, "0", 55))//
					.withColumn(resourceColumn("Deuterium", String.class, goals,
						(planet, row) -> printProductionBySource(planet, row, ResourceType.Deuterium, goals), null, selectedPlanet, "0",
						65))//
					.withColumn(resourceColumn("Energy", String.class, goals,
						(planet, row) -> printProductionBySource(planet, row, ResourceType.Energy, goals), null, selectedPlanet, "0", 65))//
					.withColumn("Utilization", String.class, row -> getUtilization(selectedPlanet.get(), row, goals),
						utilColumn -> utilColumn
						.withWidths(60, 60, 60)
						.withMutation(m -> m.mutateAttribute((row, u) -> setUtilization(selectedPlanet.get(), row, u))
								.editableIf((row, u) -> !goals && isUtilEditable(row)).asCombo(s -> s, (cell, until) -> {
									int maxUtil = theUniGui.getRules().get().economy().getMaxUtilization(cell.getModelValue().utilizable, //
										theUniGui.getSelectedAccount().get(), selectedPlanet.get().planet);
									return getPercentages(maxUtil);
								})
							.filterAccept((row, util) -> isUtilAcceptable(row.get(), util))//
							.clicks(1)))//
		);
	}

	static <T> CategoryRenderStrategy<ResourceRow, T> resourceColumn(String name, Class<T> type, boolean goal,
		BiFunction<PlanetWithProduction, ResourceRow, T> getter, TriConsumer<PlanetWithProduction, ResourceRow, T> setter,
		SettableValue<PlanetWithProduction> selectedPlanet, T defValue, int width) {
		CategoryRenderStrategy<ResourceRow, T> column = new CategoryRenderStrategy<ResourceRow, T>(name, TypeTokens.get().of(type), t -> {
			PlanetWithProduction planet = selectedPlanet.get();
			if (planet == null || planet.planet == null) {
				return defValue;
			} else {
				return getter.apply(planet, t);
			}
		});
		column.withWidths(width, width, width);
		if (!goal && setter != null) {
			column.withMutation(m -> m.mutateAttribute((t, v) -> setter.accept(selectedPlanet.get(), t, v)).withRowUpdate(false));
		}
		return column;
	}

	interface TriConsumer<T, U, V> {
		void accept(T t, U u, V v);
	}

	enum ResourceRow {
		Basic(null, "Basic Income"),
		Metal(Utilizable.MetalMine, "Metal Mine"),
		Crystal(Utilizable.CrystalMine, "Crystal Mine"),
		Deut(Utilizable.DeuteriumSynthesizer, "Deuterium Synthesizer"), //
		Solar(Utilizable.SolarPlant, "Solar Plant"),
		Fusion(Utilizable.FusionReactor, "Fusion Reactor"),
		Satellite(Utilizable.SolarSatellite, "Solar Satellite"),
		SlotBonus(null, "Slot Bonus"), //
		Crawler(Utilizable.Crawler, "Crawler"), //
		Plasma(null, "Plasma Technology"),
		Items(null, "Items"),
		Geologist(null, "Geologist"),
		Engineer(null, "Engineer"),
		CommandingStaff(null, "Commanding Staff"), //
		Collector(null, "Collector"),
		Storage(null, "Storage Capacity"),
		Divider(null, "-------------------"),
		Hourly(null, "Total per Hour"),
		Daily(null, "Total per Day"),
		Weeky(null, "Total per Week");

		public final Utilizable utilizable;
		private final String display;

		private ResourceRow(Utilizable util, String display) {
			this.utilizable = util;
			this.display = display;
		}

		@Override
		public String toString() {
			return display;
		}
	}

	enum ResourceColumn {
		Type, Level, Metal, Crystal, Deut, Energy, Util;
	}

	static String printProduction(double production, ProductionDisplayType time) {
		if (time.type == null) {
			return "";
		}
		switch (time.type) {
		case Year:
			production *= TimeUtils.getDaysInYears(1) * 24;
			break;
		case Month:
			production *= TimeUtils.getDaysInMonths(1) * 24;
			break;
		case Week:
			production *= 7 * 24;
			break;
		case Day:
			production *= 24;
			break;
		default:
			break;
		}
		return OGameUtils.printResourceAmount(production);
	}

	int getCargoes(PlanetWithProduction planet, boolean subtractCargoCost, ProductionDisplayType time) {
		double production = planet.getMetal().totalNet * 1.0 + planet.getCrystal().totalNet + planet.getDeuterium().totalNet;
		switch (time.type) {
		case Year:
			production *= TimeUtils.getDaysInYears(1) * 24;
			break;
		case Month:
			production *= TimeUtils.getDaysInMonths(1) * 24;
			break;
		case Week:
			production *= 7 * 24;
			break;
		case Day:
			production *= 24;
			break;
		default:
			break;
		}
		long capacity = theUniGui.getRules().get().fleet().getCargoSpace(//
			ShipyardItemType.LargeCargo, theUniGui.getSelectedAccount().get());
		if (subtractCargoCost) {
			capacity += theUniGui.getRules().get().economy()
				.getUpgradeCost(theUniGui.getSelectedAccount().get(), planet.planet, AccountUpgradeType.LargeCargo, 0, 1).getTotal();
		}
		return (int) Math.ceil(production / capacity);
	}

	int getPSValue(PlanetWithProduction planet, ResourceRow type, boolean goal) {
		Planet p = goal ? planet.upgradePlanet : planet.planet;
		Account a = goal ? theUniGui.getUpgradeAccount().get() : theUniGui.getSelectedAccount().get();
		switch (type) {
		case Basic:
			return 0;
		case Metal:
			return p.getMetalMine();
		case Crystal:
			return p.getCrystalMine();
		case Deut:
			return p.getDeuteriumSynthesizer();
		case Solar:
			return p.getSolarPlant();
		case Fusion:
			return p.getFusionReactor();
		case Satellite:
			return p.getSolarSatellites();
		case Crawler:
			return Math.min(p.getCrawlers(), //
				theUniGui.getRules().get().economy().getMaxCrawlers(a, p));
		case SlotBonus:
			return 0;
		case Plasma:
			return a.getResearch().getPlasma();
		case Items:
			int items = 0;
			if (p.getMetalBonus() > 0) {
				items++;
			}
			if (p.getCrystalBonus() > 0) {
				items++;
			}
			if (p.getDeuteriumBonus() > 0) {
				items++;
			}
			if (p.getEnergyBonus() > 0) {
				items++;
			}
			return items;
		case Geologist:
			return a.getOfficers().isGeologist() ? 1 : 0;
		case Engineer:
			return a.getOfficers().isEngineer() ? 1 : 0;
		case CommandingStaff:
			return a.getOfficers().isCommandingStaff() ? 1 : 0;
		case Collector:
			return a.getGameClass() == AccountClass.Collector ? 1 : 0;
		case Storage:
			return 0;
		case Divider:
			return 0;
		case Hourly:
			return 0;
		case Daily:
			return 0;
		case Weeky:
			return 0;
		}
		throw new IllegalStateException("Unrecognized resource row: " + type);
	}

	static String renderResourceRow(ResourceRow row, int value) {
		switch (row) {
		case Basic:
			return "";
		case Metal:
		case Crystal:
		case Deut:
		case Solar:
		case Fusion:
		case Satellite:
		case Crawler:
		case Plasma:
		case Items:
			return String.valueOf(value);
		case Geologist:
		case Engineer:
		case CommandingStaff:
		case Collector:
			return value == 0 ? "" : "Active";
		case Storage:
		case SlotBonus:
			return "";
		case Divider:
			return "-----";
		case Hourly:
		case Daily:
		case Weeky:
			return "";
		}
		throw new IllegalStateException("Unrecognized resource row: " + row);
	}

	static boolean canEditPSValue(ResourceRow type) {
		switch (type) {
		case Metal:
		case Crystal:
		case Deut:
		case Solar:
		case Fusion:
		case Satellite:
		case Plasma:
			return true;
		default:
			break;
		}
		return false;
	}

	void setPSValue(PlanetWithProduction planet, ResourceRow type, int value) {
		switch (type) {
		case Metal:
			planet.planet.setMetalMine(value);
			break;
		case Crystal:
			planet.planet.setCrystalMine(value);
			break;
		case Deut:
			planet.planet.setDeuteriumSynthesizer(value);
			break;
		case Solar:
			planet.planet.setSolarPlant(value);
			break;
		case Fusion:
			planet.planet.setFusionReactor(value);
			break;
		case Satellite:
			planet.planet.setSolarSatellites(value);
			break;
		case Crawler:
			planet.planet.setCrawlers(value);
			break;
		case Plasma:
			theUniGui.getSelectedAccount().get().getResearch().setPlasma(value);
			break;
		default:
			break;
		}
	}

	String printProductionBySource(PlanetWithProduction planet, ResourceRow row, ResourceType resource, boolean goal) {
		Production p = null;
		switch (resource) {
		case Metal:
			p = planet.getMetal(goal);
			break;
		case Crystal:
			p = planet.getCrystal(goal);
			break;
		case Deuterium:
			p = planet.getDeuterium(goal);
			break;
		case Energy:
			p = planet.getEnergy(goal);
			break;
		}
		switch (row) {
		case Basic:
			return printProduction(p.byType.getOrDefault(ProductionSource.Base, 0), ProductionDisplayType.Hourly);
		case Metal:
			return printProduction(p.byType.getOrDefault(ProductionSource.MetalMine, 0), ProductionDisplayType.Hourly);
		case Crystal:
			return printProduction(p.byType.getOrDefault(ProductionSource.CrystalMine, 0), ProductionDisplayType.Hourly);
		case Deut:
			return printProduction(p.byType.getOrDefault(ProductionSource.DeuteriumSynthesizer, 0), ProductionDisplayType.Hourly);
		case Solar:
			return printProduction(p.byType.getOrDefault(ProductionSource.Solar, 0), ProductionDisplayType.Hourly);
		case Fusion:
			return printProduction(p.byType.getOrDefault(ProductionSource.Fusion, 0), ProductionDisplayType.Hourly);
		case Satellite:
			return printProduction(p.byType.getOrDefault(ProductionSource.Satellite, 0), ProductionDisplayType.Hourly);
		case Crawler:
			return printProduction(p.byType.getOrDefault(ProductionSource.Crawler, 0), ProductionDisplayType.Hourly);
		case SlotBonus:
			return printProduction(p.byType.getOrDefault(ProductionSource.Slot, 0), ProductionDisplayType.Hourly);
		case Plasma:
			return printProduction(p.byType.getOrDefault(ProductionSource.Plasma, 0), ProductionDisplayType.Hourly);
		case Items:
			return printProduction(p.byType.getOrDefault(ProductionSource.Item, 0), ProductionDisplayType.Hourly);
		case Geologist:
			return printProduction(p.byType.getOrDefault(ProductionSource.Geologist, 0), ProductionDisplayType.Hourly);
		case Engineer:
			return printProduction(p.byType.getOrDefault(ProductionSource.Engineer, 0), ProductionDisplayType.Hourly);
		case CommandingStaff:
			return printProduction(p.byType.getOrDefault(ProductionSource.CommandingStaff, 0), ProductionDisplayType.Hourly);
		case Collector:
			return printProduction(p.byType.getOrDefault(ProductionSource.Collector, 0), ProductionDisplayType.Hourly);
		case Storage:
			if (resource == ResourceType.Energy) {
				return "0";
			}
			return printProduction(theUniGui.getRules().get().economy().getStorage(planet.planet, resource), ProductionDisplayType.Hourly);
		case Divider:
			return "----------";
		case Hourly:
			return printProduction(p.totalNet, ProductionDisplayType.Hourly);
		case Daily:
			if (resource == ResourceType.Energy) {
				return printProduction(p.totalNet, ProductionDisplayType.Hourly);
			}
			return printProduction(p.totalNet, ProductionDisplayType.Daily);
		case Weeky:
			if (resource == ResourceType.Energy) {
				return printProduction(p.totalNet, ProductionDisplayType.Hourly);
			}
			return printProduction(p.totalNet, ProductionDisplayType.Weekly);
		}
		return "";
	}

	static String getUtilization(PlanetWithProduction planet, ResourceRow row, boolean goals) {
		switch (row) {
		case Metal:
			return (goals ? planet.upgradePlanet : planet.planet).getMetalUtilization() + "%";
		case Crystal:
			return (goals ? planet.upgradePlanet : planet.planet).getCrystalUtilization() + "%";
		case Deut:
			return (goals ? planet.upgradePlanet : planet.planet).getDeuteriumUtilization() + "%";
		case Solar:
			return (goals ? planet.upgradePlanet : planet.planet).getSolarPlantUtilization() + "%";
		case Fusion:
			return (goals ? planet.upgradePlanet : planet.planet).getFusionReactorUtilization() + "%";
		case Satellite:
			return (goals ? planet.upgradePlanet : planet.planet).getSolarSatelliteUtilization() + "%";
		case Crawler:
			return (goals ? planet.upgradePlanet : planet.planet).getCrawlerUtilization() + "%";
		case Divider:
			return "-----------";
		default:
			return "";
		}
	}

	static boolean isUtilEditable(ResourceRow row) {
		return row.utilizable != null;
	}

	@SuppressWarnings("static-method")
	String isUtilAcceptable(ResourceRow row, String util) {
		return null;
	}

	static void setUtilization(PlanetWithProduction planet, ResourceRow row, String util) {
		int value = Integer.parseInt(util.substring(0, util.length() - 1));
		switch (row) {
		case Metal:
			planet.planet.setMetalUtilization(value);
			break;
		case Crystal:
			planet.planet.setCrystalUtilization(value);
			break;
		case Deut:
			planet.planet.setDeuteriumUtilization(value);
			break;
		case Solar:
			planet.planet.setSolarPlantUtilization(value);
			break;
		case Fusion:
			planet.planet.setFusionReactorUtilization(value);
			break;
		case Satellite:
			planet.planet.setSolarSatelliteUtilization(value);
			break;
		case Crawler:
			planet.planet.setCrawlerUtilization(value);
			break;
		default:
			break;
		}
	}
}
