package org.quark.ogame.uni.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableConfigFormat;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryRenderStrategy;
import org.observe.util.swing.JustifiedBoxLayout;
import org.observe.util.swing.ObservableSwingUtils;
import org.observe.util.swing.PanelPopulation;
import org.qommons.ArrayUtils;
import org.qommons.TimeUtils;
import org.qommons.collect.BetterList;
import org.qommons.io.Format;
import org.qommons.io.SpinnerFormat;
import org.quark.ogame.OGameUtils;
import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.AccountClass;
import org.quark.ogame.uni.AccountUpgrade;
import org.quark.ogame.uni.AccountUpgradeType;
import org.quark.ogame.uni.BuildingType;
import org.quark.ogame.uni.Moon;
import org.quark.ogame.uni.OGameEconomyRuleSet.Production;
import org.quark.ogame.uni.OGameEconomyRuleSet.ProductionSource;
import org.quark.ogame.uni.Planet;
import org.quark.ogame.uni.Research;
import org.quark.ogame.uni.ResourceType;
import org.quark.ogame.uni.ShipyardItemType;
import org.quark.ogame.uni.UpgradeCost;

import com.google.common.reflect.TypeToken;

public class PlanetTable {
	private final OGameUniGui theUniGui;

	private final ObservableCollection<PlanetWithProduction> selectedPlanets;
	private final SettableValue<PlanetWithProduction> theSelectedPlanet;

	private final SettableValue<Boolean> showFields;
	private final SettableValue<Boolean> showTemps;
	private final SettableValue<Boolean> showMines;
	private final SettableValue<Boolean> showEnergy;
	private final SettableValue<Boolean> showStorage;
	private final SettableValue<ProductionDisplayType> productionType;
	private final SettableValue<Boolean> showMainFacilities;
	private final SettableValue<Boolean> showOtherFacilities;
	private final SettableValue<Boolean> showMoonBuildings;

	private final ObservableCollection<AccountUpgrade> theSelectedPlanetUpgrades;

	public PlanetTable(OGameUniGui uniGui) {
		theUniGui = uniGui;

		ObservableConfig config = uniGui.getConfig();
		showFields = config.asValue(boolean.class).at("planet-categories/fields").withFormat(Format.BOOLEAN, () -> false)
			.buildValue(null);
		showTemps = config.asValue(boolean.class).at("planet-categories/temps").withFormat(Format.BOOLEAN, () -> true).buildValue(null);
		showMines = config.asValue(boolean.class).at("planet-categories/mines").withFormat(Format.BOOLEAN, () -> true).buildValue(null);
		showEnergy = config.asValue(boolean.class).at("planet-categories/energy").withFormat(Format.BOOLEAN, () -> false)
			.buildValue(null);
		showStorage = config.asValue(boolean.class).at("planet-categories/storage").withFormat(Format.BOOLEAN, () -> false)
			.buildValue(null);
		showMainFacilities = config.asValue(boolean.class).at("planet-categories/main-facilities")
			.withFormat(Format.BOOLEAN, () -> false).buildValue(null);
		showOtherFacilities = config.asValue(boolean.class).at("planet-categories/other-facilities")
			.withFormat(Format.BOOLEAN, () -> false).buildValue(null);
		showMoonBuildings = config.asValue(boolean.class).at("planet-categories/moon-buildings").withFormat(Format.BOOLEAN, () -> false)
			.buildValue(null);
		productionType = config.asValue(ProductionDisplayType.class).at("planet-categories/production")
			.withFormat(ObservableConfigFormat.enumFormat(ProductionDisplayType.class, () -> ProductionDisplayType.Hourly))
			.buildValue(null);

		ObservableCollection<Research> researchColl = ObservableCollection.flattenValue(theUniGui.getSelectedAccount()
			.<ObservableCollection<Research>> map(account -> ObservableCollection.of(TypeTokens.get().of(Research.class),
				account == null ? Collections.emptyList() : Arrays.asList(account.getResearch()))));
		// TODO Do I need this?
		// researchColl.simpleChanges().act(__ -> theUniGui.refreshProduction());

		selectedPlanets = theUniGui.getPlanets().flow().refresh(productionType.noInitChanges()).collect();

		theSelectedPlanet = SettableValue.build(PlanetWithProduction.class).safe(false).build();
		
		theSelectedPlanetUpgrades=ObservableCollection.build(AccountUpgrade.class).safe(false).build();
		Observable.or(theSelectedPlanet.changes(), theUniGui.getReferenceAccount().noInitChanges()).act(__ -> {
			PlanetWithProduction p=theSelectedPlanet.get();
			Account refAcct = theUniGui.getReferenceAccount().get();
			if(p==null || refAcct==null){
				theSelectedPlanetUpgrades.clear();
				return;
			}
			Account account = theUniGui.getSelectedAccount().get();
			int pIdx=account.getPlanets().getValues().indexOf(p.planet);
			Planet refPlanet=refAcct.getPlanets().getValues().size()<=pIdx ? null : refAcct.getPlanets().getValues().get(pIdx);
			List<AccountUpgrade> upgrades=new ArrayList<>();
			for(BuildingType bdg : BuildingType.values()){
				int fromLevel=refPlanet==null ? 0 : refPlanet.getBuildingLevel(bdg);
				int toLevel=p.planet.getBuildingLevel(bdg);
				if(fromLevel!=toLevel){
					AccountUpgradeType type=AccountUpgradeType.getBuildingUpgrade(bdg);
					UpgradeCost cost = theUniGui.getRules().get().economy().getUpgradeCost(account, p.planet, type, fromLevel, toLevel);
					upgrades.add(new AccountUpgrade(type, p.planet, fromLevel, toLevel, cost));
				}
			}
			for (ShipyardItemType ship : ShipyardItemType.values()) {
				int fromLevel = refPlanet == null ? 0 : refPlanet.getStationedShips(ship);
				int toLevel = p.planet.getStationedShips(ship);
				if (fromLevel != toLevel) {
					AccountUpgradeType type = AccountUpgradeType.getShipyardItemUpgrade(ship);
					UpgradeCost cost = theUniGui.getRules().get().economy().getUpgradeCost(account, p.planet, type, fromLevel, toLevel);
					upgrades.add(new AccountUpgrade(type, p.planet, fromLevel, toLevel, cost));
				}
			}
			
			ArrayUtils.adjust(theSelectedPlanetUpgrades, upgrades, new ArrayUtils.DifferenceListener<AccountUpgrade, AccountUpgrade>(){
				@Override
				public boolean identity(AccountUpgrade o1, AccountUpgrade o2) {
					return o1.equals(o2);
				}

				@Override
				public AccountUpgrade added(AccountUpgrade o, int mIdx, int retIdx) {
					return o;
				}

				@Override
				public AccountUpgrade removed(AccountUpgrade o, int oIdx, int incMod, int retIdx) {
					return null;
				}

				@Override
				public AccountUpgrade set(AccountUpgrade o1, int idx1, int incMod, AccountUpgrade o2, int idx2, int retIdx) {
					return o1;
				}
			});
		});
	}

	public void addPlanetTable(PanelPopulation.PanelPopulator<?, ?> panel) {
		List<Object> planetUpgradeList = new ArrayList<>();
		planetUpgradeList.add("None");
		for (BuildingType b : BuildingType.values()) {
			if (b.isPlanetBuilding) {
				planetUpgradeList.add(b);
			}
		}
		ObservableCollection<Object> planetUpgrades = ObservableCollection.build(Object.class).withBacking(BetterList.of(planetUpgradeList))
			.build();

		TypeToken<CategoryRenderStrategy<PlanetWithProduction, ?>> planetColumnType = new TypeToken<CategoryRenderStrategy<PlanetWithProduction, ?>>() {};
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> basicPlanetColumns = ObservableCollection
			.create(planetColumnType);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> fieldColumns = ObservableCollection.of(planetColumnType,
			intPlanetColumn("Total Fields", false, p -> theUniGui.getRules().get().economy().getFields(p), (p, f) -> {
				int currentTotal = theUniGui.getRules().get().economy().getFields(p);
				int currentBase = p.getBaseFields();
				int diff = f - currentTotal;
				int newBase = currentBase + diff;
				if (newBase < 0) {
					newBase = 0;
				}
				p.setBaseFields(newBase);
			}, 80).withMutation(m -> m.filterAccept((p, f) -> {
				int currentTotal = theUniGui.getRules().get().economy().getFields(((PlanetWithProduction) p.get()).planet);
				int currentBase = ((PlanetWithProduction) p.get()).planet.getBaseFields();
				int diff = f - currentTotal;
				int newBase = currentBase + diff;
				if (newBase < 0) {
					return ((PlanetWithProduction) p.get()).planet.getUsedFields() + " fields are used";
				}
				return null;
			})), //
			intPlanetColumn("Free Fields", false, planet -> {
				return theUniGui.getRules().get().economy().getFields(planet) - planet.getUsedFields();
			}, null, 80)//
			);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> moonFieldColumns = ObservableCollection.of(planetColumnType,
			intMoonColumn("Moon Fields", moon -> theUniGui.getRules().get().economy().getFields(moon), null, 80), //
			intMoonColumn("Bonus Fields", Moon::getFieldBonus, Moon::setFieldBonus, 80), //
			intMoonColumn("Free Fields", moon -> {
				return theUniGui.getRules().get().economy().getFields(moon) - moon.getUsedFields();
			}, null, 80)//
			);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> tempColumns = ObservableCollection.of(planetColumnType,
			intPlanetColumn("Min T", true, Planet::getMinimumTemperature, (planet, t) -> {
				planet.setMinimumTemperature(t);
				planet.setMaximumTemperature(t + 40);
			}, 40), //
			intPlanetColumn("Max T", true, Planet::getMaximumTemperature, (planet, t) -> {
				planet.setMaximumTemperature(t);
				planet.setMinimumTemperature(t - 40);
			}, 40)//
			);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> mineColumns = ObservableCollection.of(planetColumnType,
			intPlanetColumn("M Mine", false, Planet::getMetalMine, Planet::setMetalMine, 55), //
			intPlanetColumn("C Mine", false, Planet::getCrystalMine, Planet::setCrystalMine, 55), //
			intPlanetColumn("D Synth", false, Planet::getDeuteriumSynthesizer, Planet::setDeuteriumSynthesizer, 50), //
			intPlanetColumn("Crawlers", false, Planet::getCrawlers, Planet::setCrawlers, 60).formatText((planet, crawlers) -> {
				StringBuilder str = new StringBuilder();
				str.append(crawlers).append('/');
				str.append(theUniGui.getRules().get().economy().getMaxCrawlers(theUniGui.getSelectedAccount().get(), planet.planet));
				return str.toString();
			}) //
			);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> energyBldgs = ObservableCollection.of(planetColumnType,
			intPlanetColumn("Sats", false, Planet::getSolarSatellites, Planet::setSolarSatellites, 75), //
			intPlanetColumn("Solar", false, Planet::getSolarPlant, Planet::setSolarPlant, 55), //
			intPlanetColumn("Fusion", false, Planet::getFusionReactor, Planet::setFusionReactor, 55));
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> storageColumns = ObservableCollection.of(planetColumnType,
			intPlanetColumn("M Stor", false, Planet::getMetalStorage, Planet::setMetalStorage, 55), //
			intPlanetColumn("C Stor", false, Planet::getCrystalStorage, Planet::setCrystalStorage, 55), //
			intPlanetColumn("D Stor", false, Planet::getDeuteriumStorage, Planet::setDeuteriumStorage, 55)//
			);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> productionColumns = ObservableCollection.of(planetColumnType,
			planetColumn("M Prod", String.class, planet -> printProduction(planet.getMetal().totalNet, productionType.get()), null, 80), //
			planetColumn("C Prod", String.class, planet -> printProduction(planet.getCrystal().totalNet, productionType.get()), null, 80), //
			planetColumn("D Prod", String.class, planet -> printProduction(planet.getDeuterium().totalNet, productionType.get()), null, 80), //
			planetColumn("Cargoes", int.class, planet -> getCargoes(planet, false, productionType.get()), null, 80), //
			planetColumn("SS Cargoes", int.class, planet -> getCargoes(planet, true, productionType.get()), null, 80)//
			).flow().refresh(productionType.noInitChanges()).collect();
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> mainFacilities = ObservableCollection.of(planetColumnType,
			intPlanetColumn("Robotics", false, Planet::getRoboticsFactory, Planet::setRoboticsFactory, 60), //
			intPlanetColumn("Shipyard", false, Planet::getShipyard, Planet::setShipyard, 60), //
			intPlanetColumn("Lab", false, Planet::getResearchLab, Planet::setResearchLab, 55), //
			intPlanetColumn("Nanite", false, Planet::getNaniteFactory, Planet::setNaniteFactory, 55)//
			);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> otherFacilities = ObservableCollection.of(planetColumnType,
			intPlanetColumn("Ally Depot", false, Planet::getAllianceDepot, Planet::setAllianceDepot, 65), //
			intPlanetColumn("Silo", false, Planet::getMissileSilo, Planet::setMissileSilo, 45), //
			intPlanetColumn("Terraformer", false, Planet::getTerraformer, Planet::setTerraformer, 65), //
			intPlanetColumn("Space Dock", false, Planet::getSpaceDock, Planet::setSpaceDock, 65)//
			);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> moonBuildings = ObservableCollection.of(planetColumnType,
			intMoonColumn("Lunar Base", Moon::getLunarBase, Moon::setLunarBase, 60), //
			intMoonColumn("Phalanx", Moon::getSensorPhalanx, Moon::setSensorPhalanx, 55), //
			intMoonColumn("Jump Gate", Moon::getJumpGate, Moon::setJumpGate, 60), //
			intMoonColumn("Shipyard", Moon::getShipyard, Moon::setShipyard, 55), //
			intMoonColumn("Robotics", Moon::getRoboticsFactory, Moon::setRoboticsFactory, 55)//
			);

		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> emptyColumns = ObservableCollection.of(planetColumnType);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> planetColumns = ObservableCollection
			.flattenCollections(planetColumnType, //
				basicPlanetColumns, //
				ObservableCollection.flattenValue(showFields.map(show -> show ? fieldColumns : emptyColumns)), //
				ObservableCollection.flattenValue(showTemps.map(show -> show ? tempColumns : emptyColumns)), //
				ObservableCollection.flattenValue(showMines.map(show -> show ? mineColumns : emptyColumns)), //
				ObservableCollection.flattenValue(showEnergy.map(show -> show ? energyBldgs : emptyColumns)), //
				ObservableCollection.flattenValue(showStorage.map(show -> show ? storageColumns : emptyColumns)), //
				ObservableCollection.flattenValue(productionType.map(type -> type.type == null ? emptyColumns : productionColumns)), //
				ObservableCollection.flattenValue(showMainFacilities.map(show -> show ? mainFacilities : emptyColumns)), //
				ObservableCollection.flattenValue(showOtherFacilities.map(show -> show ? otherFacilities : emptyColumns)), //
				ObservableCollection.flattenValue(showFields.map(show -> show ? moonFieldColumns : emptyColumns)), //
				ObservableCollection.flattenValue(showMoonBuildings.map(show -> show ? moonBuildings : emptyColumns))//
				).collect();

		panel.fill().fillV()//
		.addHPanel("Show Properties:", new JustifiedBoxLayout(false).setMainAlignment(JustifiedBoxLayout.Alignment.LEADING),
			fieldPanel -> fieldPanel//
			.addCheckField("Fields:", showFields, null).spacer(3)//
			.addCheckField("Temps:", showTemps, null).spacer(3)//
			.addCheckField("Mines:", showMines, null).spacer(3)//
			.addCheckField("Energy:", showEnergy, null).spacer(3)//
			.addCheckField("Storage:", showStorage, null).spacer(3)//
			.addComboField("Production:", productionType, null, ProductionDisplayType.values())//
			.addCheckField("Main Facilities:", showMainFacilities, null).spacer(3)//
			.addCheckField("Other Facilities:", showOtherFacilities, null).spacer(3)//
			.addCheckField("Moon Buildings:", showMoonBuildings, null).spacer(3)//
			)//
		.addTable(selectedPlanets,
				planetTable -> planetTable.fill().withItemName("planet")//
			// This is a little hacky, but the next line tells the column the item name
			.withColumns(basicPlanetColumns)
			// function
			.withNameColumn(p -> p.planet.getName(), (p, name) -> p.planet.setName(name), false,
				nameCol -> nameCol.withWidths(50, 100, 150))//
			.withColumn("Upgrd", Object.class, p -> p.planet.getCurrentUpgrade(), upgradeCol -> upgradeCol.withWidths(40, 40, 40)
				.formatText(bdg -> bdg == null ? "" : ((BuildingType) bdg).shortName).withMutation(m -> m.asCombo(bdg -> {
					if (bdg instanceof BuildingType) {
						return ((BuildingType) bdg).shortName;
					} else {
						return "None";
					}
						}, planetUpgrades).clicks(1).mutateAttribute((p, bdg) -> {
					if (bdg instanceof BuildingType) {
						p.planet.setCurrentUpgrade((BuildingType) bdg);
					} else {
						p.planet.setCurrentUpgrade(null);
					}
				})))//
			.withColumns(planetColumns)//
			.withSelection(theSelectedPlanet, false)//
					.withAdd(() -> theUniGui.createPlanet(), null)//
					.withRemove(planets -> theUniGui.getSelectedAccount().get().getPlanets().getValues().removeAll(planets),
						action -> action//
				.confirmForItems("Delete Planets?", "Are you sure you want to delete ", null, true))//
			)//
			.addHPanel(null, new JustifiedBoxLayout(false).mainJustified().crossJustified(),
				bottomSplit -> bottomSplit.fill()//
					.addVPanel(resPanel -> resPanel.fill().fillV()//
						.addComponent(null, ObservableSwingUtils.label("Resources").bold().withFontSize(16).label, null)//
						.addTable(
							ObservableCollection.of(TypeTokens.get().of(ResourceRow.class), ResourceRow.values()).flow()
								.refresh(theSelectedPlanet.noInitChanges()).collect(),
							resTable -> resTable.fill().visibleWhen(theSelectedPlanet.map(p -> p != null))//
								.withColumn("Type", ResourceRow.class, t -> t, typeCol -> typeCol.withWidths(100, 100, 100))//
								.withColumn(resourceColumn("", int.class, this::getPSValue, this::setPSValue, theSelectedPlanet, 0, 35)
									.formatText((row, v) -> renderResourceRow(row, v))//
									.withMutation(m -> m.asText(SpinnerFormat.INT).editableIf((row, v) -> canEditPSValue(row))))
								.withColumn(resourceColumn("Metal", String.class,
									(planet, row) -> printProductionBySource(planet, row, ResourceType.Metal), null, theSelectedPlanet, "0",
									45))//
								.withColumn(resourceColumn("Crystal", String.class,
									(planet, row) -> printProductionBySource(planet, row, ResourceType.Crystal), null, theSelectedPlanet,
									"0", 45))//
								.withColumn(resourceColumn("Deuterium", String.class,
									(planet, row) -> printProductionBySource(planet, row, ResourceType.Deuterium), null, theSelectedPlanet,
									"0", 65))//
								.withColumn(resourceColumn("Energy", String.class,
									(planet, row) -> printProductionBySource(planet, row, ResourceType.Energy), null, theSelectedPlanet,
									"0", 65))//
								.withColumn("Utilization", String.class, row -> getUtilization(theSelectedPlanet.get(), row),
									utilColumn -> utilColumn.withWidths(60, 60, 60)
										.withMutation(m -> m.mutateAttribute((row, u) -> setUtilization(theSelectedPlanet.get(), row, u))
											.editableIf((row, u) -> isUtilEditable(row))
											.asCombo(s -> s, ObservableCollection.of(TypeTokens.get().STRING, "0%", "10%", "20%", "30%",
												"40%", "50%", "60%", "70%", "80%", "90%", "100%"))
											.clicks(1)))//
					)//
					)//
					.addVPanel(upgradePanel -> upgradePanel.fill().fillV()// ;
						.addComponent(null, ObservableSwingUtils.label("Upgrade Costs").bold().withFontSize(16).label, null)//
						.addTable(theSelectedPlanetUpgrades, upgradeTable -> upgradeTable.fill()//
							.visibleWhen(theSelectedPlanet.combine((p, a) -> p != null && a != null, theUniGui.getReferenceAccount()))//
								.withColumn("Planet", String.class, upgrade -> upgrade.planet.getName(), null)//
								.withColumn("Upgrade", AccountUpgradeType.class, upgrade -> upgrade.type, null)//
								.withColumn("From", int.class, upgrade -> upgrade.fromLevel, null)//
								.withColumn("To", int.class, upgrade -> upgrade.toLevel, null)//
							.withColumn("Metal", String.class, upgrade -> OGameUtils.printResourceAmount(upgrade.cost.getMetal()), null)//
							.withColumn("Crystal", String.class, upgrade -> OGameUtils.printResourceAmount(upgrade.cost.getCrystal()), null)//
							.withColumn("Deut", String.class, upgrade -> OGameUtils.printResourceAmount(upgrade.cost.getDeuterium()), null)//
							.withColumn("Time", String.class, upgrade -> OGameUniGui.printUpgradeTime(upgrade.cost.getUpgradeTime()), null)//
				)//
				)//
			);
	}

	static <T> CategoryRenderStrategy<PlanetWithProduction, T> planetColumn(String name, Class<T> type,
		Function<PlanetWithProduction, T> getter, BiConsumer<Planet, T> setter, int width) {
		CategoryRenderStrategy<PlanetWithProduction, T> column = new CategoryRenderStrategy<PlanetWithProduction, T>(name,
			TypeTokens.get().of(type), getter);
		column.withWidths(width, width, width);
		if (setter != null) {
			column.withMutation(m -> m.mutateAttribute((p, v) -> setter.accept(p.planet, v)).withRowUpdate(true));
		}
		return column;
	}

	CategoryRenderStrategy<PlanetWithProduction, Integer> intPlanetColumn(String name, boolean allowNegative,
		Function<Planet, Integer> getter, BiConsumer<Planet, Integer> setter, int width) {
		CategoryRenderStrategy<PlanetWithProduction, Integer> column = planetColumn(name, int.class, p -> getter.apply(p.planet), setter,
			width);
		OGameUniGui.decorateDiffColumn(column, planetIdx -> {
			Account refAccount = theUniGui.getReferenceAccount().get();
			if (refAccount == null) {
				return null;
			} else if (refAccount.getPlanets().getValues().size() <= planetIdx) {
				return 0;
			}
			return getter.apply(refAccount.getPlanets().getValues().get(planetIdx));
		});
		if (setter != null) {
			column.withMutation(m -> {
				m.asText(SpinnerFormat.INT).clicks(1);
				if (!allowNegative) {
					m.filterAccept((p, value) -> value >= 0 ? null : "Must not be negative");
				}
			});
		}
		return column;
	}

	CategoryRenderStrategy<PlanetWithProduction, Integer> intMoonColumn(String name, Function<Moon, Integer> getter,
		BiConsumer<Moon, Integer> setter, int width) {
		CategoryRenderStrategy<PlanetWithProduction, Integer> column = planetColumn(name, int.class, p -> getter.apply(p.planet.getMoon()),
			(p, v) -> setter.accept(p.getMoon(), v), width);
		OGameUniGui.decorateDiffColumn(column, planetIdx -> {
			Account refAccount = theUniGui.getReferenceAccount().get();
			if (refAccount == null) {
				return null;
			} else if (refAccount.getPlanets().getValues().size() <= planetIdx) {
				return 0;
			}
			return getter.apply(refAccount.getPlanets().getValues().get(planetIdx).getMoon());
		});
		if (setter != null) {
			column.withMutation(
				m -> m.asText(SpinnerFormat.INT).clicks(1).filterAccept((p, value) -> value >= 0 ? null : "Must not be negative"));
		}
		return column;
	}

	interface TriConsumer<T, U, V> {
		void accept(T t, U u, V v);
	}

	static <T> CategoryRenderStrategy<ResourceRow, T> resourceColumn(String name, Class<T> type,
		BiFunction<PlanetWithProduction, ResourceRow, T> getter, TriConsumer<PlanetWithProduction, ResourceRow, T> setter,
		SettableValue<PlanetWithProduction> selectedPlanet, T defValue, int width) {
		CategoryRenderStrategy<ResourceRow, T> column = new CategoryRenderStrategy<ResourceRow, T>(name, TypeTokens.get().of(type), t -> {
			PlanetWithProduction planet = selectedPlanet.get();
			if (planet == null) {
				return defValue;
			} else {
				return getter.apply(planet, t);
			}
		});
		column.withWidths(width, width, width);
		if (setter != null) {
			column.withMutation(m -> m.mutateAttribute((t, v) -> setter.accept(selectedPlanet.get(), t, v)).withRowUpdate(false));
		}
		return column;
	}

	enum ResourceRow {
		Basic("Basic Income"),
		Metal("Metal Mine"),
		Crystal("Crystal Mine"),
		Deut("Deuterium Synthesizer"), //
		Solar("Solar Plant"),
		Fusion("Fusion Reactor"),
		Satellite("Solar Satellite"),
		Crawler("Crawler"), //
		Plasma("Plasma Technology"),
		Items("Items"),
		Geologist("Geologist"),
		Engineer("Engineer"),
		CommandingStaff("Commanding Staff"), //
		Collector("Collector"),
		Storage("Storage Capacity"),
		Divider("-------------------"),
		Hourly("Total per hour:"),
		Daily("Total per Day"),
		Weeky("Total per Week");

		private final String display;

		private ResourceRow(String display) {
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

	int getPSValue(PlanetWithProduction planet, ResourceRow type) {
		switch (type) {
		case Basic:
			return 0;
		case Metal:
			return planet.planet.getMetalMine();
		case Crystal:
			return planet.planet.getCrystalMine();
		case Deut:
			return planet.planet.getDeuteriumSynthesizer();
		case Solar:
			return planet.planet.getSolarSatellites();
		case Fusion:
			return planet.planet.getFusionReactor();
		case Satellite:
			return planet.planet.getSolarSatellites();
		case Crawler:
			return planet.planet.getCrawlers();
		case Plasma:
			return theUniGui.getSelectedAccount().get().getResearch().getPlasma();
		case Items:
			int items = 0;
			if (planet.planet.getMetalBonus() > 0) {
				items++;
			}
			if (planet.planet.getCrystalBonus() > 0) {
				items++;
			}
			if (planet.planet.getDeuteriumBonus() > 0) {
				items++;
			}
			return items;
		case Geologist:
			return theUniGui.getSelectedAccount().get().getOfficers().isGeologist() ? 1 : 0;
		case Engineer:
			return theUniGui.getSelectedAccount().get().getOfficers().isEngineer() ? 1 : 0;
		case CommandingStaff:
			return theUniGui.getSelectedAccount().get().getOfficers().isCommandingStaff() ? 1 : 0;
		case Collector:
			return theUniGui.getSelectedAccount().get().getGameClass() == AccountClass.Collector ? 1 : 0;
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
		case Crawler:
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

	String printProductionBySource(PlanetWithProduction planet, ResourceRow row, ResourceType resource) {
		Production p = null;
		switch (resource) {
		case Metal:
			p = planet.getMetal();
			break;
		case Crystal:
			p = planet.getCrystal();
			break;
		case Deuterium:
			p = planet.getDeuterium();
			break;
		case Energy:
			p = planet.getEnergy();
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

	static String getUtilization(PlanetWithProduction planet, ResourceRow row) {
		switch (row) {
		case Metal:
			return planet.planet.getMetalUtilization() + "%";
		case Crystal:
			return planet.planet.getCrystalUtilization() + "%";
		case Deut:
			return planet.planet.getDeuteriumUtilization() + "%";
		case Solar:
			return planet.planet.getSolarPlantUtilization() + "%";
		case Fusion:
			return planet.planet.getFusionReactorUtilization() + "%";
		case Satellite:
			return planet.planet.getSolarSatelliteUtilization() + "%";
		case Crawler:
			return planet.planet.getCrawlerUtilization() + "%";
		case Divider:
			return "-----------";
		default:
			return "";
		}
	}

	static boolean isUtilEditable(ResourceRow row) {
		switch (row) {
		case Metal:
		case Crystal:
		case Deut:
		case Solar:
		case Fusion:
		case Satellite:
		case Crawler:
			return true;
		default:
			break;
		}
		return false;
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
