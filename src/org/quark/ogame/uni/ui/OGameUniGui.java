package org.quark.ogame.uni.ui;

import java.awt.Color;
import java.awt.Component;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;

import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableConfigFormat;
import org.observe.config.ObservableValueSet;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryRenderStrategy;
import org.observe.util.swing.JustifiedBoxLayout;
import org.observe.util.swing.ModelCell;
import org.observe.util.swing.ObservableCellRenderer;
import org.observe.util.swing.ObservableSwingUtils;
import org.observe.util.swing.PanelPopulation;
import org.qommons.StringUtils;
import org.qommons.TimeUtils;
import org.qommons.TimeUtils.DurationComponentType;
import org.qommons.Transaction;
import org.qommons.collect.CollectionElement;
import org.qommons.io.Format;
import org.qommons.io.SpinnerFormat;
import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.AccountClass;
import org.quark.ogame.uni.AccountUpgrade;
import org.quark.ogame.uni.BuildingType;
import org.quark.ogame.uni.Moon;
import org.quark.ogame.uni.OGameEconomyRuleSet.Production;
import org.quark.ogame.uni.OGameEconomyRuleSet.ProductionSource;
import org.quark.ogame.uni.OGameRuleSet;
import org.quark.ogame.uni.Planet;
import org.quark.ogame.uni.Research;
import org.quark.ogame.uni.ResourceType;
import org.quark.ogame.uni.ShipyardItemType;
import org.quark.ogame.uni.UpgradeCost;
import org.quark.ogame.uni.versions.OGameRuleSet710;
import org.xml.sax.SAXException;

import com.google.common.reflect.TypeToken;

public class OGameUniGui extends JPanel {
	private final ObservableConfig theConfig;
	private final List<OGameRuleSet> theRuleSets;
	private final ObservableValueSet<Account> theAccounts;
	private final SettableValue<OGameRuleSet> theSelectedRuleSet;
	private final SettableValue<Account> theSelectedAccount;
	private final SettableValue<Account> theReferenceAccount;

	public OGameUniGui(ObservableConfig config, List<OGameRuleSet> ruleSets, ObservableValueSet<Account> accounts) {
		theConfig = config;
		theRuleSets = ruleSets;
		theAccounts = accounts;
		theSelectedRuleSet = config.observeValue("selected-rule-set").map(TypeTokens.get().of(OGameRuleSet.class), name -> {
			for (OGameRuleSet ruleSet : theRuleSets) {
				if (ruleSet.getName().equals(name)) {
					return ruleSet;
				}
			}
			return theRuleSets.get(theRuleSets.size() - 1);
		}, OGameRuleSet::getName, null);
		theSelectedAccount = config.observeValue("selected-account").map(TypeTokens.get().of(Account.class), name -> {
			try (Transaction t = theAccounts.getValues().lock(false, null)) {
				for (Account account : theAccounts.getValues()) {
					if (("" + account.getId()).equals(name)) {
						return account;
					}
				}
			}
			if (!theAccounts.getValues().isEmpty()) {
				return theAccounts.getValues().getFirst();
			} else {
				return null;
			}
		}, account -> account == null ? "" : "" + account.getId(), null);
		theReferenceAccount = theSelectedAccount.refresh(theAccounts.getValues().simpleChanges()).map(TypeTokens.get().of(Account.class),
			Account::getReferenceAccount, Account::getReferenceAccount, null);

		initComponents();
	}

	enum ProductionDisplayType {
		None(null),
		Hourly(TimeUtils.DurationComponentType.Hour),
		Daily(TimeUtils.DurationComponentType.Day),
		Weekly(TimeUtils.DurationComponentType.Week),
		Monthly(TimeUtils.DurationComponentType.Month),
		Yearly(TimeUtils.DurationComponentType.Year);

		public final TimeUtils.DurationComponentType type;

		private ProductionDisplayType(DurationComponentType type) {
			this.type = type;
		}
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

	void initComponents() {
		/* TODO
		 * ROI sequence
		 * Costs
		 * Production value in account table
		 * Total Upgrade Cost
		 * Trading
		 * Spitballing (general upgrade costs without reference to an account)
		 * Hyperspace tech guide
		 */
		ObservableCollection<Account> referenceAccounts = ObservableCollection.flattenCollections(TypeTokens.get().of(Account.class), //
			ObservableCollection.of(TypeTokens.get().of(Account.class), (Account) null), //
			theAccounts.getValues().flow().refresh(theSelectedAccount.noInitChanges())
			.filter(account -> account == theSelectedAccount.get() ? "Selected" : null).collect()//
			).collect();

		TypeToken<CategoryRenderStrategy<PlanetWithProduction, ?>> planetColumnType = new TypeToken<CategoryRenderStrategy<PlanetWithProduction, ?>>() {};
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> basicPlanetColumns = ObservableCollection
			.create(planetColumnType);
		SettableValue<Boolean> showFields = theConfig.asValue(boolean.class).at("planet-categories/fields")
			.withFormat(Format.BOOLEAN, () -> false).buildValue();
		SettableValue<Boolean> showTemps = theConfig.asValue(boolean.class).at("planet-categories/temps")
			.withFormat(Format.BOOLEAN, () -> true).buildValue();
		SettableValue<Boolean> showMines = theConfig.asValue(boolean.class).at("planet-categories/mines")
			.withFormat(Format.BOOLEAN, () -> true).buildValue();
		SettableValue<Boolean> showEnergy = theConfig.asValue(boolean.class).at("planet-categories/energy")
			.withFormat(Format.BOOLEAN, () -> false).buildValue();
		SettableValue<Boolean> showStorage = theConfig.asValue(boolean.class).at("planet-categories/storage")
			.withFormat(Format.BOOLEAN, () -> false).buildValue();
		SettableValue<Boolean> showMainFacilities = theConfig.asValue(boolean.class).at("planet-categories/main-facilities")
			.withFormat(Format.BOOLEAN, () -> false).buildValue();
		SettableValue<Boolean> showOtherFacilities = theConfig.asValue(boolean.class).at("planet-categories/other-facilities")
			.withFormat(Format.BOOLEAN, () -> false).buildValue();
		SettableValue<Boolean> showMoonBuildings = theConfig.asValue(boolean.class).at("planet-categories/moon-buildings")
			.withFormat(Format.BOOLEAN, () -> false).buildValue();

		SettableValue<ProductionDisplayType> productionType = theConfig.asValue(ProductionDisplayType.class)
			.at("planet-categories/production")
			.withFormat(ObservableConfigFormat.enumFormat(ProductionDisplayType.class, () -> ProductionDisplayType.Hourly)).buildValue();

		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> fieldColumns = ObservableCollection.of(planetColumnType,
			intPlanetColumn("Total Fields", p -> theSelectedRuleSet.get().economy().getFields(p), (p, f) -> {
				int currentTotal = theSelectedRuleSet.get().economy().getFields(p);
				int currentBase = p.getBaseFields();
				int diff = f - currentTotal;
				int newBase = currentBase + diff;
				if (newBase < 0) {
					newBase = 0;
				}
				p.setBaseFields(newBase);
			}, 80).withMutation(m -> m.filterAccept((p, f) -> {
				int currentTotal = theSelectedRuleSet.get().economy().getFields(((PlanetWithProduction) p.get()).planet);
				int currentBase = ((PlanetWithProduction) p.get()).planet.getBaseFields();
				int diff = f - currentTotal;
				int newBase = currentBase + diff;
				if (newBase < 0) {
					return ((PlanetWithProduction) p.get()).planet.getUsedFields() + " fields are used";
				}
				return null;
			})), //
			intPlanetColumn("Free Fields", planet -> {
				return theSelectedRuleSet.get().economy().getFields(planet) - planet.getUsedFields();
			}, null, 80)//
			);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> moonFieldColumns = ObservableCollection.of(planetColumnType,
			intMoonColumn("Moon Fields", moon -> theSelectedRuleSet.get().economy().getFields(moon), null, 80), //
			intMoonColumn("Bonus Fields", Moon::getFieldBonus, Moon::setFieldBonus, 80), //
			intMoonColumn("Free Fields", moon -> {
				return theSelectedRuleSet.get().economy().getFields(moon) - moon.getUsedFields();
			}, null, 80)//
			);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> tempColumns = ObservableCollection.of(planetColumnType,
			intPlanetColumn("Min T", Planet::getMinimumTemperature, (planet, t) -> {
				planet.setMinimumTemperature(t);
				planet.setMaximumTemperature(t + 40);
			}, 40), //
			intPlanetColumn("Max T", Planet::getMaximumTemperature, (planet, t) -> {
				planet.setMaximumTemperature(t);
				planet.setMinimumTemperature(t - 40);
			}, 40)//
			);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> mineColumns = ObservableCollection.of(planetColumnType,
			intPlanetColumn("M Mine", Planet::getMetalMine, Planet::setMetalMine, 55), //
			intPlanetColumn("C Mine", Planet::getCrystalMine, Planet::setCrystalMine, 55), //
			intPlanetColumn("D Synth", Planet::getDeuteriumSynthesizer, Planet::setDeuteriumSynthesizer, 50), //
			intPlanetColumn("Crawlers", Planet::getCrawlers, Planet::setCrawlers, 60) //
			);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> energyBldgs = ObservableCollection.of(planetColumnType,
			intPlanetColumn("Sats", Planet::getSolarSatellites, Planet::setSolarSatellites, 75), //
			intPlanetColumn("Solar", Planet::getSolarPlant, Planet::setSolarPlant, 55), //
			intPlanetColumn("Fusion", Planet::getFusionReactor, Planet::setFusionReactor, 55));
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> storageColumns = ObservableCollection.of(planetColumnType,
			intPlanetColumn("M Stor", Planet::getMetalStorage, Planet::setMetalStorage, 55), //
			intPlanetColumn("C Stor", Planet::getCrystalStorage, Planet::setCrystalStorage, 55), //
			intPlanetColumn("D Stor", Planet::getDeuteriumStorage, Planet::setDeuteriumStorage, 55)//
			);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> productionColumns = ObservableCollection.of(planetColumnType,
			planetColumn("M Prod", String.class, planet -> printProduction(planet.metal.totalNet, productionType.get()), null, 80), //
			planetColumn("C Prod", String.class, planet -> printProduction(planet.crystal.totalNet, productionType.get()), null, 80), //
			planetColumn("D Prod", String.class, planet -> printProduction(planet.deuterium.totalNet, productionType.get()), null, 80), //
			planetColumn("Cargoes", int.class, planet -> getCargoes(planet, false, productionType.get()), null, 80), //
			planetColumn("SS Cargoes", int.class, planet -> getCargoes(planet, true, productionType.get()), null, 80)//
			).flow().refresh(productionType.noInitChanges()).collect();
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> mainFacilities = ObservableCollection.of(planetColumnType,
			intPlanetColumn("Robotics", Planet::getRoboticsFactory, Planet::setRoboticsFactory, 60), //
			intPlanetColumn("Shipyard", Planet::getShipyard, Planet::setShipyard, 60), //
			intPlanetColumn("Lab", Planet::getResearchLab, Planet::setResearchLab, 55), //
			intPlanetColumn("Nanite", Planet::getNaniteFactory, Planet::setNaniteFactory, 55)//
			);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> otherFacilities = ObservableCollection.of(planetColumnType,
			intPlanetColumn("Ally Depot", Planet::getAllianceDepot, Planet::setAllianceDepot, 65), //
			intPlanetColumn("Silo", Planet::getMissileSilo, Planet::setMissileSilo, 45), //
			intPlanetColumn("Terraformer", Planet::getTerraformer, Planet::setTerraformer, 65), //
			intPlanetColumn("Space Dock", Planet::getSpaceDock, Planet::setSpaceDock, 65)//
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

		ObservableCollection<Research> researchColl = ObservableCollection.flattenValue(
			theSelectedAccount.<ObservableCollection<Research>> map(account -> ObservableCollection.of(TypeTokens.get().of(Research.class),
				account == null ? Collections.emptyList() : Arrays.asList(account.getResearch()))));
		ObservableCollection<PlanetWithProduction> selectedPlanets = ObservableCollection
			.flattenValue(theSelectedAccount.map(
				account -> account == null ? ObservableCollection.of(TypeTokens.get().of(Planet.class)) : account.getPlanets().getValues()))
			.flow().refresh(Observable.or(researchColl.simpleChanges(), productionType.noInitChanges()))//
			.map(TypeTokens.get().of(PlanetWithProduction.class), this::productionFor, opts -> opts.cache(true).reEvalOnUpdate(false))
			.collect();
		selectedPlanets.changes().act(evt -> {
			if (evt.type == CollectionChangeType.set) {
				for (PlanetWithProduction p : evt.getValues()) {
					updateProduction(p);
				}
			}
		});
		SettableValue<PlanetWithProduction> selectedPlanet = SettableValue.build(PlanetWithProduction.class).safe(false).build();
		PanelPopulation.populateVPanel(this, Observable.empty())//
		.addSplit(true,
			mainSplit -> mainSplit.withSplitLocation(150).fill().fillV()//
			.firstV(accountSelectPanel -> accountSelectPanel//
				.fill().addTable((ObservableCollection<Account>) theAccounts.getValues(),
					accountTable -> accountTable//
					.fill().withItemName("account")//
					.withNameColumn(Account::getName, Account::setName, true,
						nameColumn -> nameColumn.withWidths(50, 100, 300)//
						.withMutation(nameMutator -> nameMutator.asText(SpinnerFormat.NUMERICAL_TEXT)))//
					.withColumn("Universe", String.class, account -> account.getUniverse().getName(),
						uniColumn -> uniColumn//
						.withWidths(50, 100, 300)//
						.withMutation(uniMutator -> uniMutator.mutateAttribute((account, uniName) -> {
							account.getUniverse().setName(uniName);
							return uniName;
						}).asText(Format.TEXT)))//
					.withColumn("Planets", Integer.class, account -> account.getPlanets().getValues().size(), //
						planetColumn -> planetColumn.withWidths(50, 50, 50))//
								.withColumn("Reference", Account.class, Account::getReferenceAccount,
						refColumn -> refColumn.withWidths(50, 100, 300)//
						.formatText(account -> account == null ? "" : account.getName()))//
					.withColumn("Eco Points", String.class, account -> OGameUniGui.this.printPoints(account), //
						pointsColumn -> pointsColumn.withWidths(75, 75, 75))//
					.withSelection(theSelectedAccount, false)//
					.withAdd(() -> initAccount(theAccounts.create()//
						.with("name",
							StringUtils.getNewItemName(theAccounts.getValues(), Account::getName, "New Account",
								StringUtils.PAREN_DUPLICATES))//
						.with("id", getNewId())//
						.create().get()), null)//
					.withRemove(accounts -> theAccounts.getValues().removeAll(accounts), action -> action//
						.confirmForItems("Delete Accounts?", "Are you sure you want to delete ", null, true))//
					.withCopy(account -> {
						Account copy = theAccounts.copy(account).get();
						copy.setId(getNewId());
						copy.setName(StringUtils.getNewItemName(theAccounts.getValues(), Account::getName, account.getName(),
							StringUtils.PAREN_DUPLICATES));
									copy.setReferenceAccount(account);
						return copy;
					}, null)//
					)//
				).lastV(selectedAccountPanel -> selectedAccountPanel.visibleWhen(theSelectedAccount.map(account -> account != null))//
					.addTabs(
						tabs -> tabs.fill().fillV()
						.withVTab("settings",
							acctSettingsPanel -> acctSettingsPanel//
							.fill()//
							.addTextField("Name:",
								theSelectedAccount.asFieldEditor(TypeTokens.get().STRING, Account::getName, Account::setName,
									null),
								SpinnerFormat.NUMERICAL_TEXT, f -> f.fill())//
							.addComboField("Compare To:",
											theSelectedAccount.asFieldEditor(TypeTokens.get().of(Account.class),
												Account::getReferenceAccount, Account::setReferenceAccount, null),
								referenceAccounts, f -> f.fill().renderAs(account -> account == null ? "" : account.getName()))//
							.addTextField("Universe Name:",
								theSelectedAccount.asFieldEditor(TypeTokens.get().STRING,
									account -> account.getUniverse().getName(),
									(account, name) -> account.getUniverse().setName(name), null),
								Format.TEXT, f -> f.fill())//
							.addHPanel("Speed:",
								new JustifiedBoxLayout(false).setMainAlignment(JustifiedBoxLayout.Alignment.LEADING),
								speedPanel -> speedPanel//
								.addTextField("Economy:",
									theSelectedAccount.asFieldEditor(TypeTokens.get().INT,
										account -> account.getUniverse().getEconomySpeed(),
										(account, speed) -> account.getUniverse().setEconomySpeed(speed), null),
									SpinnerFormat.INT, f -> f.withPostLabel("x").modifyEditor(tf -> tf.withColumns(2)))//
								.spacer(3)//
								.addTextField("Research:",
									theSelectedAccount.asFieldEditor(TypeTokens.get().INT,
										account -> account.getUniverse().getResearchSpeed(),
										(account, speed) -> account.getUniverse().setResearchSpeed(speed), null),
									SpinnerFormat.INT, f -> f.withPostLabel("x").modifyEditor(tf -> tf.withColumns(2)))//
								.spacer(3)//
								.addTextField("Fleet:",
									theSelectedAccount.asFieldEditor(TypeTokens.get().INT,
										account -> account.getUniverse().getFleetSpeed(),
										(account, speed) -> account.getUniverse().setFleetSpeed(speed), null),
									SpinnerFormat.INT, f -> f.withPostLabel("x").modifyEditor(tf -> tf.withColumns(2)))//
								)//
							.addComboField("Account Class:",
								theSelectedAccount.asFieldEditor(TypeTokens.get().of(AccountClass.class), Account::getGameClass,
									Account::setGameClass, null),
								ObservableCollection.of(TypeTokens.get().of(AccountClass.class), AccountClass.values()), //
								classEditor -> classEditor.fill().withValueTooltip(clazz -> describeClass(clazz))//
								)//
							.addHPanel("Collector Bonus:", "box",
								collectorBonusPanel -> collectorBonusPanel//
								.addTextField("Production:",
									theSelectedAccount.asFieldEditor(TypeTokens.get().INT,
										account -> account.getUniverse().getCollectorProductionBonus(),
										(account, speed) -> account.getUniverse().setCollectorProductionBonus(speed), null),
									SpinnerFormat.INT, f -> f.withPostLabel("%").modifyEditor(tf -> tf.withColumns(2)))//
								.spacer(4)//
								.addTextField("Energy:",
									theSelectedAccount.asFieldEditor(TypeTokens.get().INT,
										account -> account.getUniverse().getCollectorEnergyBonus(),
										(account, speed) -> account.getUniverse().setCollectorEnergyBonus(speed), null),
									SpinnerFormat.INT, f -> f.withPostLabel("%").modifyEditor(tf -> tf.withColumns(2)))//
								)//
							.addTextField("Hyperspace Cargo Bonus:",
								theSelectedAccount.asFieldEditor(TypeTokens.get().DOUBLE,
									account -> account.getUniverse().getHyperspaceCargoBonus(),
									(account, speed) -> account.getUniverse().setHyperspaceCargoBonus(speed), null),
								SpinnerFormat.doubleFormat("0.##", 1),
								f -> f.withPostLabel("%").fill())//
							.addHPanel("Trade Ratios:", "box",
								tradeRatePanel -> tradeRatePanel//
								.addTextField(null,
									theSelectedAccount.asFieldEditor(TypeTokens.get().DOUBLE,
										account -> account.getUniverse().getTradeRatios().getMetal(),
										(account, rate) -> account.getUniverse().getTradeRatios().setMetal(rate), null),
									SpinnerFormat.doubleFormat("0.0#", 0.1), f -> f.modifyEditor(tf -> tf.withColumns(3)))
								.addComponent(null, new JLabel(":"), null)//
								.addTextField(null,
									theSelectedAccount.asFieldEditor(TypeTokens.get().DOUBLE,
										account -> account.getUniverse().getTradeRatios().getCrystal(),
										(account, rate) -> account.getUniverse().getTradeRatios().setCrystal(rate), null),
									SpinnerFormat.doubleFormat("0.0#", 0.1), f -> f.modifyEditor(tf -> tf.withColumns(3)))
								.addComponent(null, new JLabel(":"), null)//
								.addTextField(null,
									theSelectedAccount.asFieldEditor(TypeTokens.get().DOUBLE,
										account -> account.getUniverse().getTradeRatios().getDeuterium(),
										(account, rate) -> account.getUniverse().getTradeRatios().setDeuterium(rate), null),
									SpinnerFormat.doubleFormat("0.0#", 0.1), f -> f.modifyEditor(tf -> tf.withColumns(3)))//
								)//
							.addHPanel("Officers:", "box",
								officersPanel -> officersPanel//
								.addCheckField("Commander:",
									theSelectedAccount.asFieldEditor(TypeTokens.get().BOOLEAN,
										account -> account.getOfficers().isCommander(),
										(account, b) -> account.getOfficers().setCommander(b), null),
									null)
								.spacer(3)//
								.addCheckField("Admiral:",
									theSelectedAccount.asFieldEditor(TypeTokens.get().BOOLEAN,
										account -> account.getOfficers().isAdmiral(),
										(account, b) -> account.getOfficers().setAdmiral(b), null),
									null)
								.spacer(3)//
								.addCheckField("Engineer:",
									theSelectedAccount.asFieldEditor(TypeTokens.get().BOOLEAN,
										account -> account.getOfficers().isEngineer(),
										(account, b) -> account.getOfficers().setEngineer(b), null),
									null)
								.spacer(3)//
								.addCheckField("Geologist:",
									theSelectedAccount.asFieldEditor(TypeTokens.get().BOOLEAN,
										account -> account.getOfficers().isGeologist(),
										(account, b) -> account.getOfficers().setGeologist(b), null),
									null)
								.spacer(3)//
								.addCheckField("Technocrat:",
									theSelectedAccount.asFieldEditor(TypeTokens.get().BOOLEAN,
										account -> account.getOfficers().isTechnocrat(),
										(account, b) -> account.getOfficers().setTechnocrat(b), null),
									null)
								.spacer(3)//
								.addCheckField("Commanding Staff:",
									theSelectedAccount.asFieldEditor(TypeTokens.get().BOOLEAN,
										account -> account.getOfficers().isCommandingStaff(),
										(account, b) -> account.getOfficers().setCommandingStaff(b), null),
									null)
								.spacer(3)//
								)//
							, acctSettingsTab -> acctSettingsTab.setName("Settings"))//
						.withVTab("planets",
							acctBuildingsPanel -> acctBuildingsPanel.fill().fillV()//
							.addHPanel("Show Properties:",
								new JustifiedBoxLayout(false).setMainAlignment(JustifiedBoxLayout.Alignment.LEADING),
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
								.withColumns(planetColumns)//
								.withSelection(selectedPlanet, false)//
								.withAdd(() -> createPlanet(selectedPlanets), null)//
								.withRemove(planets -> theSelectedAccount.get().getPlanets().getValues().removeAll(planets),
									action -> action//
									.confirmForItems("Delete Planets?", "Are you sure you want to delete ", null, true))//
								)//
							.addComponent(null, ObservableSwingUtils.label("Resources").bold().withFontSize(16).label, null)//
							.addTable(
								ObservableCollection.of(TypeTokens.get().of(ResourceRow.class), ResourceRow.values()).flow()
								.refresh(selectedPlanet.noInitChanges()).collect(),
								resTable -> resTable.fill().visibleWhen(selectedPlanet.map(p -> p != null))//
												.withColumn("Type", ResourceRow.class, t -> t, typeCol -> typeCol.withWidths(100, 100, 100))//
								.withColumn(
									resourceColumn("", int.class, this::getPSValue, this::setPSValue, selectedPlanet, 0, 35)
									.formatText((row, v) -> renderResourceRow(row, v))//
									.withMutation(
										m -> m.asText(SpinnerFormat.INT).editableIf((row, v) -> canEditPSValue(row))))
								.withColumn(resourceColumn("Metal", String.class,
									(planet, row) -> printProductionBySource(planet, row, ResourceType.Metal), null,
									selectedPlanet, "0", 45))//
								.withColumn(resourceColumn("Crystal", String.class,
									(planet, row) -> printProductionBySource(planet, row, ResourceType.Crystal), null,
									selectedPlanet, "0", 45))//
								.withColumn(resourceColumn("Deuterium", String.class,
									(planet, row) -> printProductionBySource(planet, row, ResourceType.Deuterium), null,
									selectedPlanet, "0", 65))//
								.withColumn(resourceColumn("Energy", String.class,
									(planet, row) -> printProductionBySource(planet, row, ResourceType.Energy), null,
									selectedPlanet, "0", 65))//
								.withColumn("Utilization", String.class, row -> getUtilization(selectedPlanet.get(), row),
									utilColumn -> utilColumn.withWidths(60, 60, 60)
									.withMutation(
										m -> m.mutateAttribute((row, u) -> setUtilization(selectedPlanet.get(), row, u))
										.editableIf((row, u) -> isUtilEditable(row)).asCombo(s -> s,
											ObservableCollection.of(TypeTokens.get().STRING, "0%", "10%", "20%",
												"30%", "40%", "50%", "60%", "70%", "80%", "90%", "100%"))
										.clicks(1)))//
								)//
							, acctBuildingsTab -> acctBuildingsTab.setName("Planets"))//
						.withVTab("research", acctResearchPanel -> acctResearchPanel//
							.addTable(researchColl, researchTable -> researchTable.fill()//
								.withColumn(intResearchColumn("Energy", Research::getEnergy, Research::setEnergy, 60))//
								.withColumn(intResearchColumn("Laser", Research::getLaser, Research::setLaser, 55))//
								.withColumn(intResearchColumn("Ion", Research::getIon, Research::setIon, 35))//
								.withColumn(intResearchColumn("Hyperspace", Research::getHyperspace, Research::setHyperspace, 75))//
								.withColumn(intResearchColumn("Plasma", Research::getPlasma, Research::setPlasma, 60))//
								.withColumn(
									intResearchColumn("Combustion", Research::getCombustionDrive, Research::setCombustionDrive, 65))//
								.withColumn(intResearchColumn("Impulse", Research::getImpulseDrive, Research::setImpulseDrive, 60))//
								.withColumn(
									intResearchColumn("Hyperdrive", Research::getHyperspaceDrive, Research::setHyperspaceDrive, 70))//
								.withColumn(intResearchColumn("Espionage", Research::getEspionage, Research::setEspionage, 70))//
								.withColumn(intResearchColumn("Computer", Research::getComputer, Research::setComputer, 70))//
								.withColumn(intResearchColumn("Astro", Research::getAstrophysics, Research::setAstrophysics, 55))//
								.withColumn(intResearchColumn("IRN", Research::getIntergalacticResearchNetwork,
									Research::setIntergalacticResearchNetwork, 35))//
								.withColumn(intResearchColumn("Graviton", Research::getGraviton, Research::setGraviton, 65))//
								.withColumn(intResearchColumn("Weapons", Research::getWeapons, Research::setWeapons, 65))//
								.withColumn(intResearchColumn("Shielding", Research::getShielding, Research::setShielding, 65))//
								.withColumn(intResearchColumn("Armor", Research::getArmor, Research::setArmor, 55))//
								)//
							, acctResearchTab -> acctResearchTab.setName("Research")//
							)//
						))//
			);
	}

	static class SimpleLineBorder extends LineBorder {
		public SimpleLineBorder(Color color, int thickness) {
			super(color, thickness);
		}

		void setColor(Color color) {
			lineColor = color;
		}
	}

	static <M> void decorateDiffColumn(CategoryRenderStrategy<M, Integer> column, IntFunction<Integer> reference) {
		column.withRenderer(new ObservableCellRenderer.DefaultObservableCellRenderer<M, Integer>((m, c) -> String.valueOf(c)) {
			@Override
			public Component getCellRendererComponent(Component parent, ModelCell<M, Integer> cell, CellRenderContext ctx) {
				Component c = super.getCellRendererComponent(parent, cell, ctx);
				Integer value = cell.getCellValue();
				if (value == null) {
					return c;
				}
				Integer refValue = reference.apply(cell.getRowIndex());
				if (refValue != null && !value.equals(refValue)) {
					StringBuilder str = new StringBuilder().append(value);
					str.append(" (");
					if (value > refValue) {
						str.append('+');
					} else {
						str.append('-');
					}
					str.append(Math.abs(value - refValue));
					str.append(')');
					((JLabel) c).setText(str.toString());
				}
				return c;
			}
		}).decorate((cell, decorator) -> {
			Integer value = cell.getCellValue();
			if (value == null) {
				return;
			}
			Integer refValue = reference.apply(cell.getRowIndex());
			if (refValue == null || value.equals(refValue)) {
				return;
			}
			int diff = value - refValue;
			Color fg;
			if (diff < 0) {
				if (cell.isSelected() || diff < -3) {
					fg = Color.red;
				} else {
					int gb = 255 - (int) Math.round((4.0 + diff) * 255 / 4);
					fg = new Color(gb, 0, 0);
				}
			} else {
				if (cell.isSelected() || diff > 3) {
					fg = Color.green;
				} else {
					int rb = 255 - (int) Math.round((4.0 - diff) * 255 / 4);
					fg = new Color(0, rb, 0);
				}
			}
			decorator.withForeground(fg).bold();
		});
	}

	static <T> CategoryRenderStrategy<Account, T> accountColumn(String name, Class<T> type, Function<Account, T> getter,
		BiConsumer<Account, T> setter, int width) {
		CategoryRenderStrategy<Account, T> column = new CategoryRenderStrategy<Account, T>(name, TypeTokens.get().of(type), getter);
		column.withWidths(width, width, width);
		if (setter != null) {
			column.withMutation(m -> m.mutateAttribute((p, v) -> setter.accept(p, v)).withRowUpdate(true));
		}
		return column;
	}

	static CategoryRenderStrategy<Account, Integer> intAccountColumn(String name, Function<Account, Integer> getter,
		BiConsumer<Account, Integer> setter, int width) {
		CategoryRenderStrategy<Account, Integer> column = accountColumn(name, int.class, getter, setter, width);
		if (setter != null) {
			column.withMutation(
				m -> m.asText(SpinnerFormat.INT).clicks(1).filterAccept((p, value) -> value >= 0 ? null : "Must not be negative"));
		}
		return column;
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

	CategoryRenderStrategy<PlanetWithProduction, Integer> intPlanetColumn(String name, Function<Planet, Integer> getter,
		BiConsumer<Planet, Integer> setter, int width) {
		CategoryRenderStrategy<PlanetWithProduction, Integer> column = planetColumn(name, int.class, p -> getter.apply(p.planet), setter,
			width);
		decorateDiffColumn(column, planetIdx -> {
			Account refAccount = theReferenceAccount.get();
			if (refAccount == null) {
				return null;
			} else if (refAccount.getPlanets().getValues().size() <= planetIdx) {
				return 0;
			}
			return getter.apply(refAccount.getPlanets().getValues().get(planetIdx));
		});
		if (setter != null) {
			column.withMutation(
				m -> m.asText(SpinnerFormat.INT).clicks(1).filterAccept((p, value) -> value >= 0 ? null : "Must not be negative"));
		}
		return column;
	}

	CategoryRenderStrategy<PlanetWithProduction, Integer> intMoonColumn(String name, Function<Moon, Integer> getter,
		BiConsumer<Moon, Integer> setter, int width) {
		CategoryRenderStrategy<PlanetWithProduction, Integer> column = planetColumn(name, int.class, p -> getter.apply(p.planet.getMoon()),
			(p, v) -> setter.accept(p.getMoon(), v), width);
		decorateDiffColumn(column, planetIdx -> {
			Account refAccount = theReferenceAccount.get();
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

	<T> CategoryRenderStrategy<Research, T> researchColumn(String name, Class<T> type, Function<Research, T> getter,
		BiConsumer<Research, T> setter, int width) {
		CategoryRenderStrategy<Research, T> column = new CategoryRenderStrategy<Research, T>(name, TypeTokens.get().of(type), getter);
		column.withWidths(width, width, width);
		if (setter != null) {
			column.withMutation(m -> m.mutateAttribute((p, v) -> {
				setter.accept(p, v);
				theSelectedAccount.set(theSelectedAccount.get(), null);
			}).withRowUpdate(false));
		}
		return column;
	}

	CategoryRenderStrategy<Research, Integer> intResearchColumn(String name, Function<Research, Integer> getter,
		BiConsumer<Research, Integer> setter, int width) {
		CategoryRenderStrategy<Research, Integer> column = researchColumn(name, int.class, getter, setter, width);
		decorateDiffColumn(column, __ -> {
			Account refAccount = theReferenceAccount.get();
			if (refAccount == null) {
				return null;
			}
			return getter.apply(refAccount.getResearch());
		});
		if (setter != null) {
			column.withMutation(
				m -> m.asText(SpinnerFormat.INT).clicks(1).filterAccept((p, value) -> value >= 0 ? null : "Must not be negative"));
		}
		return column;
	}

	private static final DecimalFormat WHOLE_FORMAT = new DecimalFormat("#,##0");
	private static final DecimalFormat THREE_DIGIT_FORMAT = new DecimalFormat("#,##0.000");

	String printPoints(Account account) {
		if (account.getPlanets().getValues().isEmpty()) {
			return "0";
		}
		UpgradeCost cost = new UpgradeCost(0, 0, 0, 0, Duration.ZERO);
		OGameRuleSet rules = theSelectedRuleSet.get();
		for (AccountUpgrade upgrade : AccountUpgrade.values()) {
			switch (upgrade.type) {
			case Building:
				for (Planet planet : account.getPlanets().getValues()) {
					cost = cost
						.plus(rules.economy().getUpgradeCost(account, planet, upgrade, 0, planet.getBuildingLevel(upgrade.building)));
				}
				break;
			case ShipyardItem:
				for (Planet planet : account.getPlanets().getValues()) {
					cost = cost
						.plus(rules.economy().getUpgradeCost(account, planet, upgrade, 0, planet.getStationedShips(upgrade.shipyardItem)));
				}
				break;
			case Research:
				cost = cost.plus(rules.economy().getUpgradeCost(account, account.getPlanets().getValues().getFirst(), upgrade, 0,
					account.getResearch().getResearchLevel(upgrade.research)));
				break;
			}
		}
		double value = cost.getMetalValue(account.getUniverse().getTradeRatios());
		value /= 1E3;
		if (value < 1E6) {
			return WHOLE_FORMAT.format(value);
		} else if (value < 1E9) {
			return THREE_DIGIT_FORMAT.format(value / 1E6) + "M";
		} else {
			return THREE_DIGIT_FORMAT.format(value / 1E9) + "B";
		}
	}

	int getNewId() {
		int id = 1;
		boolean found = true;
		while (found) {
			found = false;
			for (Account account : theAccounts.getValues()) {
				if (account.getId() == id) {
					found = true;
					break;
				}
			}
			if (found) {
				id++;
			}
		}
		return id;
	}

	static String describeClass(AccountClass clazz) {
		switch (clazz) {
		case Unselected:
			return "No class bonuses";
		case Collector:
			return "<ul>" + "<li>Can produce crawlers</li>" + "<li>+25% mine production</li>" + "<li>+10% energy production</li>"
			+ "<li>+100% speed for Transporters</li>" + "<li>+25% cargo bay for Transporters</li>" + "<li>+2 offers</li>"
			+ "<li>Lower Market Fees</li>" + "<li>+50% Crawler bonus</li>" + "</ul>";
		case General:
			return "<ul>" + "<li>Can produce reapers</li>" + "<li>+100% speed for combat ships</li>" + "<li>+100% speed for Recyclers</li>"
			+ "<li>-25% deuterium consumption for all ships</li>" + "<li>-25% deuterium consumption for Recyclers</li>"
			+ "<li>A small chance to immediately destroy a Deathstar once in a battle using a light fighter.</li>"
			+ "<li>Wreckage at attack (transport to starting planet)</li>" + "<li>+2 combat research levels</li>"
			+ "<li>+2 fleet slots</li></li>" + "</ul>";
		case Discoverer:
			return "<ul>" + "<li>Can produce pathfinders</li>" + "<li>-25% research time</li>"
			+ "<li>+2% gain on successful expeditions</li>" + "<li>+10% larger planets on colonisation</li>"
			+ "<li>Debris fields created on expeditions will be visible in the Galaxy view.</li>" + "<li>+2 expeditions</li>"
			+ "<li>+20% phalanx range</li>" + "<li>75% loot from inactive players</li>" + "</ul>";
		}
		return null;
	}

	static String printProduction(double production, ProductionDisplayType time) {
		StringBuilder str = new StringBuilder();
		if (production < 0) {
			str.append('-');
			production = -production;
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
		if (production < 1E6) {
			str.append(WHOLE_FORMAT.format(production));
		} else if (production < 1E9) {
			str.append(THREE_DIGIT_FORMAT.format(production / 1E6)).append('M');
		} else if (production < 1E12) {
			str.append(THREE_DIGIT_FORMAT.format(production / 1E9)).append('B');
		} else {
			str.append(THREE_DIGIT_FORMAT.format(production / 1E12)).append('T');
		}
		return str.toString();
	}

	int getCargoes(PlanetWithProduction planet, boolean subtractCargoCost, ProductionDisplayType time) {
		double production = planet.metal.totalNet * 1.0 + planet.crystal.totalNet + planet.deuterium.totalNet;
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
		long capacity = theSelectedRuleSet.get().fleet().getCargoSpace(//
			ShipyardItemType.LargeCargo, theSelectedAccount.get());
		if (subtractCargoCost) {
			capacity += theSelectedRuleSet.get().economy()
				.getUpgradeCost(theSelectedAccount.get(), planet.planet, AccountUpgrade.LargeCargo, 0, 1).getTotal();
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
			return theSelectedAccount.get().getResearch().getPlasma();
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
			return theSelectedAccount.get().getOfficers().isGeologist() ? 1 : 0;
		case Engineer:
			return theSelectedAccount.get().getOfficers().isEngineer() ? 1 : 0;
		case CommandingStaff:
			return theSelectedAccount.get().getOfficers().isCommandingStaff() ? 1 : 0;
		case Collector:
			return theSelectedAccount.get().getGameClass() == AccountClass.Collector ? 1 : 0;
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
			theSelectedAccount.get().getResearch().setPlasma(value);
			break;
		default:
			break;
		}
	}

	String printProductionBySource(PlanetWithProduction planet, ResourceRow row, ResourceType resource) {
		Production p = null;
		switch (resource) {
		case Metal:
			p = planet.metal;
			break;
		case Crystal:
			p = planet.crystal;
			break;
		case Deuterium:
			p = planet.deuterium;
			break;
		case Energy:
			p = planet.energy;
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
			return printProduction(theSelectedRuleSet.get().economy().getStorage(planet.planet, resource), ProductionDisplayType.Hourly);
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

	PlanetWithProduction productionFor(Planet planet) {
		Production energy = theSelectedRuleSet.get().economy().getProduction(theSelectedAccount.get(), planet, ResourceType.Energy, 1);
		double energyFactor = Math.min(1, energy.totalProduction * 1.0 / energy.totalConsumption);
		Production metal = theSelectedRuleSet.get().economy().getProduction(theSelectedAccount.get(), planet, ResourceType.Metal,
			energyFactor);
		Production crystal = theSelectedRuleSet.get().economy().getProduction(theSelectedAccount.get(), planet, ResourceType.Crystal,
			energyFactor);
		Production deuterium = theSelectedRuleSet.get().economy().getProduction(theSelectedAccount.get(), planet, ResourceType.Deuterium,
			energyFactor);
		return new PlanetWithProduction(planet, metal, crystal, deuterium, energy);
	}

	void updateProduction(PlanetWithProduction p) {
		p.energy = theSelectedRuleSet.get().economy().getProduction(//
			theSelectedAccount.get(), p.planet, ResourceType.Energy, 1);
		double energyFactor = Math.min(1, p.energy.totalProduction * 1.0 / p.energy.totalConsumption);
		p.metal = theSelectedRuleSet.get().economy().getProduction(//
			theSelectedAccount.get(), p.planet, ResourceType.Metal, energyFactor);
		p.crystal = theSelectedRuleSet.get().economy().getProduction(//
			theSelectedAccount.get(), p.planet, ResourceType.Crystal, energyFactor);
		p.deuterium = theSelectedRuleSet.get().economy().getProduction(//
			theSelectedAccount.get(), p.planet, ResourceType.Deuterium, energyFactor);
	}

	static Account initAccount(Account account) {
		account.getUniverse().setName("");
		account.getUniverse().setCollectorProductionBonus(25);
		account.getUniverse().setCollectorEnergyBonus(10);
		account.getUniverse().setCrawlerCap(8);
		account.getUniverse().setEconomySpeed(1);
		account.getUniverse().setResearchSpeed(1);
		account.getUniverse().setFleetSpeed(1);
		account.getUniverse().setHyperspaceCargoBonus(5);
		account.getUniverse().getTradeRatios().setMetal(2.5);
		account.getUniverse().getTradeRatios().setCrystal(1.5);
		account.getUniverse().getTradeRatios().setDeuterium(1);
		return account;
	}

	static Account copy(Account source, Account dest) {
		dest.setGameClass(source.getGameClass());
		dest.setReferenceAccount(source);

		dest.getUniverse().setName(source.getUniverse().getName());
		dest.getUniverse().setCollectorProductionBonus(source.getUniverse().getCollectorProductionBonus());
		dest.getUniverse().setCollectorEnergyBonus(source.getUniverse().getCollectorEnergyBonus());
		dest.getUniverse().setCrawlerCap(source.getUniverse().getCrawlerCap());
		dest.getUniverse().setEconomySpeed(source.getUniverse().getEconomySpeed());
		dest.getUniverse().setResearchSpeed(source.getUniverse().getResearchSpeed());
		dest.getUniverse().setFleetSpeed(source.getUniverse().getFleetSpeed());
		dest.getUniverse().setHyperspaceCargoBonus(source.getUniverse().getHyperspaceCargoBonus());
		dest.getUniverse().getTradeRatios().setMetal(source.getUniverse().getTradeRatios().getMetal());
		dest.getUniverse().getTradeRatios().setCrystal(source.getUniverse().getTradeRatios().getCrystal());
		dest.getUniverse().getTradeRatios().setDeuterium(source.getUniverse().getTradeRatios().getDeuterium());

		dest.getOfficers().setCommander(source.getOfficers().isCommander());
		dest.getOfficers().setAdmiral(source.getOfficers().isAdmiral());
		dest.getOfficers().setEngineer(source.getOfficers().isEngineer());
		dest.getOfficers().setGeologist(source.getOfficers().isGeologist());
		dest.getOfficers().setTechnocrat(source.getOfficers().isTechnocrat());
		dest.getOfficers().setCommandingStaff(source.getOfficers().isCommandingStaff());

		dest.getResearch().setEnergy(source.getResearch().getEnergy());
		dest.getResearch().setLaser(source.getResearch().getLaser());
		dest.getResearch().setIon(source.getResearch().getIon());
		dest.getResearch().setHyperspace(source.getResearch().getHyperspace());
		dest.getResearch().setPlasma(source.getResearch().getPlasma());
		dest.getResearch().setCombustionDrive(source.getResearch().getCombustionDrive());
		dest.getResearch().setImpulseDrive(source.getResearch().getImpulseDrive());
		dest.getResearch().setHyperspaceDrive(source.getResearch().getHyperspaceDrive());
		dest.getResearch().setEspionage(source.getResearch().getEspionage());
		dest.getResearch().setComputer(source.getResearch().getComputer());
		dest.getResearch().setAstrophysics(source.getResearch().getAstrophysics());
		dest.getResearch().setIntergalacticResearchNetwork(source.getResearch().getIntergalacticResearchNetwork());
		dest.getResearch().setGraviton(source.getResearch().getGraviton());
		dest.getResearch().setWeapons(source.getResearch().getWeapons());
		dest.getResearch().setShielding(source.getResearch().getShielding());
		dest.getResearch().setArmor(source.getResearch().getArmor());

		for (Planet srcP : source.getPlanets().getValues()) {
			Planet destP = dest.getPlanets().create()//
				.with(Planet::getName, srcP.getName())//
				.with(Planet::getBaseFields, srcP.getBaseFields())//

				.with(Planet::getMinimumTemperature, srcP.getMinimumTemperature())//
				.with(Planet::getMaximumTemperature, srcP.getMaximumTemperature())//

				.with(Planet::getMetalUtilization, srcP.getMetalUtilization())//
				.with(Planet::getCrystalUtilization, srcP.getCrystalUtilization())//
				.with(Planet::getDeuteriumUtilization, srcP.getDeuteriumUtilization())//
				.with(Planet::getSolarPlantUtilization, srcP.getSolarPlantUtilization())//
				.with(Planet::getFusionReactorUtilization, srcP.getFusionReactorUtilization())//
				.with(Planet::getSolarSatelliteUtilization, srcP.getSolarSatelliteUtilization())//
				.with(Planet::getCrawlerUtilization, srcP.getCrawlerUtilization())//

				.with(Planet::getMetalBonus, srcP.getMetalBonus())//
				.with(Planet::getCrystalBonus, srcP.getCrystalBonus())//
				.with(Planet::getDeuteriumBonus, srcP.getDeuteriumBonus())//
				.create().get();

			for (BuildingType type : BuildingType.values()) {
				destP.setBuildingLevel(type, srcP.getBuildingLevel(type));
				destP.getMoon().setBuildingLevel(type, srcP.getMoon().getBuildingLevel(type));
			}
			for (ShipyardItemType type : ShipyardItemType.values()) {
				destP.setStationedShips(type, srcP.getStationedShips(type));
				destP.getMoon().setStationedShips(type, srcP.getMoon().getStationedShips(type));
			}
		}

		return dest;
	}

	PlanetWithProduction createPlanet(ObservableCollection<PlanetWithProduction> planets) {
		CollectionElement<Planet> newPlanet = theSelectedAccount.get().getPlanets().create()//
			.with(Planet::getName,
				StringUtils.getNewItemName(theSelectedAccount.get().getPlanets().getValues(), Planet::getName, "New Planet",
					StringUtils.SIMPLE_DUPLICATES))
			.with(Planet::getBaseFields, 173)//
			.with(Planet::getMetalUtilization, 100)//
			.with(Planet::getCrystalUtilization, 100)//
			.with(Planet::getDeuteriumUtilization, 100)//
			.with(Planet::getSolarPlantUtilization, 100)//
			.with(Planet::getSolarSatelliteUtilization, 100)//
			.with(Planet::getFusionReactorUtilization, 100)//
			.with(Planet::getCrawlerUtilization, 100)//
			.create();
		return planets.getElementsBySource(newPlanet.getElementId()).getFirst().get();
	}

	static class PlanetWithProduction {
		final Planet planet;
		Production metal;
		Production crystal;
		Production deuterium;
		Production energy;

		PlanetWithProduction(Planet planet, Production metal, Production crystal, Production deuterium, Production energy) {
			this.planet = planet;
			this.metal = metal;
			this.crystal = crystal;
			this.deuterium = deuterium;
			this.energy = energy;
		}
	}

	public static void main(String[] args) {
		String configFileLoc = System.getProperty("ogame.ui.config");
		if (configFileLoc == null) {
			configFileLoc = "./OGameUI.xml";
		}
		ObservableConfig config = ObservableConfig.createRoot("ogame-config");
		ObservableConfig.XmlEncoding encoding = ObservableConfig.XmlEncoding.DEFAULT;
		File configFile = new File(configFileLoc);
		if (configFile.exists()) {
			try {
				try (InputStream configStream = new BufferedInputStream(new FileInputStream(configFile))) {
					ObservableConfig.readXml(config, configStream, encoding);
				}
			} catch (IOException | SAXException e) {
				System.err.println("Could not read config file " + configFileLoc);
				e.printStackTrace();
			}
		}
		config.persistOnShutdown(ObservableConfig.toFile(configFile, encoding), ex -> {
			System.err.println("Could not persist UI config");
			ex.printStackTrace();
		});
		List<OGameRuleSet> ruleSets = new ArrayList<>();
		ruleSets.add(new OGameRuleSet710());
		ObservableSwingUtils.systemLandF();
		ObservableValueSet<Account>[] accounts = new ObservableValueSet[1];
		ObservableConfigFormat<Account> accountRefFormat = ObservableConfigFormat
			.<Account> buildReferenceFormat(fv -> accounts[0].getValues(), null)//
			.withField("id", Account::getId, ObservableConfigFormat.INT).build();
		accounts[0] = config.asValue(TypeTokens.get().of(Account.class))
			.asEntity(efb -> efb//
				.withFieldFormat(Account::getReferenceAccount, accountRefFormat))//
			.at("accounts/account").buildEntitySet();
		OGameUniGui ui = new OGameUniGui(config, ruleSets, accounts[0]);
		JFrame frame = new JFrame("OGame Account Helper");
		// frame.setContentPane(ui);
		frame.getContentPane().add(ui);
		frame.setVisible(true);
		frame.pack();
		ObservableSwingUtils.configureFrameBounds(frame, config);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}
}
