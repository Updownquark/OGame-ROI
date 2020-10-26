package org.quark.ogame.uni.ui;

import java.awt.Color;
import java.time.Duration;
import java.util.function.Function;

import javax.swing.JPanel;

import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableConfigFormat;
import org.observe.util.swing.CategoryRenderStrategy;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.QommonsUtils;
import org.qommons.TimeUtils;
import org.quark.ogame.OGameUtils;
import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.AccountUpgradeType;
import org.quark.ogame.uni.ResourceType;
import org.quark.ogame.uni.ShipyardItemType;
import org.quark.ogame.uni.TradeRatios;

public class ProductionPanel extends JPanel {
	private final OGameUniGui theUniGui;

	private final SettableValue<ProductionDisplayType> theProductionType;
	private final SettableValue<Boolean> showGoalProduction;

	public ProductionPanel(OGameUniGui uniGui) {
		theUniGui = uniGui;

		ObservableConfig config = uniGui.getConfig();
		theProductionType = config.asValue(ProductionDisplayType.class).at("planet-categories/production")
			.withFormat(ObservableConfigFormat.enumFormat(ProductionDisplayType.class, () -> ProductionDisplayType.Hourly))
			.buildValue(null);
		// Not persisted, always start on the actual planets production
		showGoalProduction = SettableValue.build(boolean.class).safe(false).withValue(false).build();
	}

	public void addPanel(PanelPopulator<?, ?> panel) {
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> initPlanetColumns = ObservableCollection
			.create(PlanetTable.PLANET_COLUMN_TYPE);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> productionColumns = ObservableCollection
			.of(PlanetTable.PLANET_COLUMN_TYPE,
				PlanetTable.planetColumn("M Prod", String.class,
					planet -> printProduction(planet.getMetal(showGoalProduction.get()).totalNet, theProductionType.get()), null, 80), //
				PlanetTable.planetColumn("C Prod", String.class,
					planet -> printProduction(planet.getCrystal(showGoalProduction.get()).totalNet, theProductionType.get()), null, 80), //
				PlanetTable.planetColumn("D Prod", String.class,
					planet -> printProduction(planet.getDeuterium(showGoalProduction.get()).totalNet, theProductionType.get()), null, 80) //
			).flow().refresh(theProductionType.noInitChanges()).collect();
		Function<Duration, String> durationFormat = d -> d == null ? "" : QommonsUtils.printDuration(d, true);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> storageFillTimes = ObservableCollection
			.of(PlanetTable.PLANET_COLUMN_TYPE,
				PlanetTable
					.planetColumn("M Fill Time", Duration.class,
						planet -> getStorageFillTime(planet, ResourceType.Metal, showGoalProduction.get()), null, 100)
					.formatText(durationFormat), //
				PlanetTable
					.planetColumn("C Fill Time", Duration.class,
						planet -> getStorageFillTime(planet, ResourceType.Crystal, showGoalProduction.get()), null, 100)
					.formatText(durationFormat), //
				PlanetTable
					.planetColumn("D Fill Time", Duration.class,
						planet -> getStorageFillTime(planet, ResourceType.Deuterium, showGoalProduction.get()), null, 100)
					.formatText(durationFormat)//
			).flow().refresh(theProductionType.noInitChanges()).collect();
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> productionCargoColumns = ObservableCollection
			.of(PlanetTable.PLANET_COLUMN_TYPE,
				PlanetTable.planetColumn("Cargoes", int.class, planet -> getCargoes(planet, false, theProductionType.get()), null, 80)
					.withHeaderTooltip("The number of Large Cargo ships required to carry away the planet's production"), //
				PlanetTable.planetColumn("SS Cargoes", int.class, planet -> getCargoes(planet, true, theProductionType.get()), null, 80)
					.withHeaderTooltip("The number of Large Cargo ships, built from the production resources of the planet,"
						+ " required to carry away the remainder of the planet's production")//
			).flow().refresh(theProductionType.noInitChanges()).collect();
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> productionTotalColumns = ObservableCollection
			.of(PlanetTable.PLANET_COLUMN_TYPE,
				PlanetTable.planetColumn("P. Total", String.class,
					planet -> {
						boolean goal = showGoalProduction.get();
						return printProduction(
							planet.getMetal(goal).totalNet + planet.getCrystal(goal).totalNet + planet.getDeuterium(goal).totalNet,
							theProductionType.get());
					}, null, 80), //
				PlanetTable.planetColumn("P. Value", String.class, planet -> {
					TradeRatios ratios = theUniGui.getSelectedAccount().get().getUniverse().getTradeRatios();
					boolean goal = showGoalProduction.get();
					return printProduction((long) (//
					planet.getMetal(goal).totalNet//
						+ planet.getCrystal(goal).totalNet / ratios.getCrystal() * ratios.getMetal()//
						+ planet.getDeuterium(goal).totalNet / ratios.getDeuterium() * ratios.getMetal()), theProductionType.get());
				}, null, 80).withHeaderTooltip("Metal-equivalent value of each planet's production")//
			).flow().refresh(theProductionType.noInitChanges()).collect();

		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> planetColumns = ObservableCollection
			.flattenCollections(PlanetTable.PLANET_COLUMN_TYPE, //
				initPlanetColumns, //
				productionColumns, productionTotalColumns, productionCargoColumns, storageFillTimes)
			.collect();
		ObservableCollection<PlanetWithProduction> planets = theUniGui.getPlanetsWithTotal().flow()//
			.refresh(Observable.or(theProductionType.noInitChanges(), showGoalProduction.noInitChanges())).collect();
		panel.fill().fillV()//
			.addTable(planets, //
				planetTable -> planetTable.fill().withItemName("planet").withAdaptiveHeight(6, 30, 50)//
					.decorate(d -> d.withTitledBorder("Planet Production", Color.black))//
					// This is a little hacky, but the next line tells the column the item name
					.withColumns(initPlanetColumns)
					// function
					.withNameColumn(p -> p.planet == null ? "Totals" : p.planet.getName(), (p, name) -> p.planet.setName(name), false,
						nameCol -> nameCol.withWidths(50, 100, 150).decorate((cell, decorator) -> {
							if (cell.getModelValue().planet == null) {
								decorator.bold();
							}
						}))//
					.withColumns(planetColumns)//
					.withSelection(theUniGui.getSelectedPlanet(), false)//
					.withTableOption(prodTypePanel -> {
						prodTypePanel.addComboField("Duration:", theProductionType, null, ProductionDisplayType.values())//
							.spacer(3)//
							.addCheckField("For Goals:", showGoalProduction, null);
					})//
		);
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
		boolean goal = showGoalProduction.get();
		double production = planet.getMetal(goal).totalNet * 1.0 + planet.getCrystal(goal).totalNet + planet.getDeuterium(goal).totalNet;
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
		Account account = goal ? theUniGui.getUpgradeAccount().get() : theUniGui.getSelectedAccount().get();
		long capacity = theUniGui.getRules().get().fleet().getCargoSpace(//
			ShipyardItemType.LargeCargo, account);
		if (subtractCargoCost) {
			capacity += theUniGui.getRules().get().economy()
				.getUpgradeCost(account, goal ? planet.upgradePlanet : planet.planet, AccountUpgradeType.LargeCargo, 0, 1).getTotal();
		}
		return (int) Math.ceil(production / capacity);
	}

	Duration getStorageFillTime(PlanetWithProduction planet, ResourceType resource, boolean goals) {
		if (planet == null || planet.planet == null) {
			return null;
		}
		long storage = theUniGui.getRules().get().economy().getStorage(//
			goals ? planet.upgradePlanet : planet.planet, resource);
		int production = planet.getProduction(resource, goals).totalNet;
		return Duration.ofSeconds(Math.round(storage * 3600.0 / production));
	}
}
