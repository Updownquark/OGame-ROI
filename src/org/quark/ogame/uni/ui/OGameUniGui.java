package org.quark.ogame.uni.ui;

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

import javax.swing.JFrame;
import javax.swing.JPanel;

import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableValueSet;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryRenderStrategy;
import org.observe.util.swing.JustifiedBoxLayout;
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
import org.quark.ogame.uni.OGameEconomyRuleSet.Production;
import org.quark.ogame.uni.OGameEconomyRuleSet.ProductionSource;
import org.quark.ogame.uni.OGameRuleSet;
import org.quark.ogame.uni.Planet;
import org.quark.ogame.uni.Research;
import org.quark.ogame.uni.ResourceType;
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
			return null;
		}, account -> "" + account.getId(), null);
		theReferenceAccount = theSelectedAccount.refresh(theAccounts.getValues().simpleChanges()).map(TypeTokens.get().of(Account.class),
			this::getReferenceAccount, (selectedAccount, referenceAccount) -> {
				selectedAccount.setReferenceAccount(referenceAccount == null ? 0 : referenceAccount.getId());
				return selectedAccount;
			}, null);

		initComponents();
	}

	enum ProductionDisplayType {
		None(null),
		Hourly(TimeUtils.DurationComponentType.Hour),
		Daily(TimeUtils.DurationComponentType.Day),
		Monthly(TimeUtils.DurationComponentType.Month),
		Yearly(TimeUtils.DurationComponentType.Year);

		public final TimeUtils.DurationComponentType type;

		private ProductionDisplayType(DurationComponentType type) {
			this.type = type;
		}
	}

	void initComponents() {
		ObservableCollection<Research> researchColl = ObservableCollection.flattenValue(
			theSelectedAccount.<ObservableCollection<Research>> map(account -> ObservableCollection.of(TypeTokens.get().of(Research.class),
				account == null ? Collections.emptyList() : Arrays.asList(account.getResearch()))));
		ObservableCollection<PlanetWithProduction> selectedPlanets = ObservableCollection
			.flattenValue(theSelectedAccount.map(
				account -> account == null ? ObservableCollection.of(TypeTokens.get().of(Planet.class)) : account.getPlanets().getValues()))
			.flow().refresh(researchColl.simpleChanges())//
			.map(TypeTokens.get().of(PlanetWithProduction.class), this::productionFor, opts -> opts.cache(true).reEvalOnUpdate(false))
			.collect();
		selectedPlanets.changes().act(evt -> {
			if (evt.type == CollectionChangeType.set) {
				for (PlanetWithProduction p : evt.getValues()) {
					updateProduction(p);
				}
			}
		});

		ObservableCollection<Account> referenceAccounts = ObservableCollection.flattenCollections(TypeTokens.get().of(Account.class), //
			ObservableCollection.of(TypeTokens.get().of(Account.class), (Account) null), //
			theAccounts.getValues().flow().refresh(theSelectedAccount.noInitChanges())
				.filter(account -> account == theSelectedAccount.get() ? "Selected" : null).collect()//
		).collect();

		TypeToken<CategoryRenderStrategy<PlanetWithProduction, ?>> planetColumnType = new TypeToken<CategoryRenderStrategy<PlanetWithProduction, ?>>() {};
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> basicPlanetColumns = ObservableCollection
			.create(planetColumnType);
		SettableValue<Boolean> showTemps = SettableValue.build(boolean.class).safe(false).withValue(true).build();
		SettableValue<Boolean> showMines = SettableValue.build(boolean.class).safe(false).withValue(true).build();
		SettableValue<Boolean> showResourceBldgs = SettableValue.build(boolean.class).safe(false).withValue(true).build();
		SettableValue<Boolean> showStorage = SettableValue.build(boolean.class).safe(false).withValue(false).build();
		SettableValue<Boolean> showMainFacilities = SettableValue.build(boolean.class).safe(false).withValue(false).build();
		SettableValue<Boolean> showOtherFacilities = SettableValue.build(boolean.class).safe(false).withValue(false).build();
		SettableValue<Boolean> showMoonBuildings = SettableValue.build(boolean.class).safe(false).withValue(false).build();

		SettableValue<Boolean> showBasicEnergy = SettableValue.build(boolean.class).safe(false).withValue(false).build();
		SettableValue<Boolean> showAdvancedEnergy = SettableValue.build(boolean.class).safe(false).withValue(false).build();

		SettableValue<ProductionDisplayType> productionType = SettableValue.build(ProductionDisplayType.class).safe(false)
			.withValue(ProductionDisplayType.Hourly).build();

		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> tempColumns = ObservableCollection.of(planetColumnType,
			new CategoryRenderStrategy<PlanetWithProduction, Integer>("Min T", TypeTokens.get().INT, p -> p.planet.getMinimumTemperature())
				.filterable(false).withWidths(40, 40, 40)//
				.withMutation(m -> m.mutateAttribute((p, level) -> {
					p.planet.setMinimumTemperature(level);
					p.planet.setMaximumTemperature(level + 40);
					return level;
				}).withRowUpdate(true).asText(SpinnerFormat.INT).clicks(0)), //
			new CategoryRenderStrategy<PlanetWithProduction, Integer>("Max T", TypeTokens.get().INT, p -> p.planet.getMaximumTemperature())
				.filterable(false).withWidths(40, 40, 40)//
				.withMutation(m -> m.mutateAttribute((p, level) -> {
					p.planet.setMaximumTemperature(level);
					p.planet.setMinimumTemperature(level - 40);
					return level;
				}).withRowUpdate(true).asText(SpinnerFormat.INT).clicks(0)) //
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> mineColumns = ObservableCollection.of(planetColumnType,
			new CategoryRenderStrategy<PlanetWithProduction, Integer>("M Mine", TypeTokens.get().INT, p -> p.planet.getMetalMine())
				.filterable(false).withWidths(45, 45, 45)//
				.withMutation(m -> m.mutateAttribute((p, level) -> {
					p.planet.setMetalMine(level);
					return level;
				}).withRowUpdate(true).filterAccept((p, level) -> level >= 0 ? null : "No negative buildings").asText(SpinnerFormat.INT)
					.clicks(1)), //
			new CategoryRenderStrategy<PlanetWithProduction, Integer>("C Mine", TypeTokens.get().INT, p -> p.planet.getCrystalMine())
				.filterable(false).withWidths(45, 45, 45)//
				.withMutation(m -> m.mutateAttribute((p, level) -> {
					p.planet.setCrystalMine(level);
					return level;
				}).withRowUpdate(true).filterAccept((p, level) -> level >= 0 ? null : "No negative buildings").asText(SpinnerFormat.INT)
					.clicks(1)), //
			new CategoryRenderStrategy<PlanetWithProduction, Integer>("D Synth", TypeTokens.get().INT,
				p -> p.planet.getDeuteriumSynthesizer()).filterable(false).withWidths(50, 50, 50)//
					.withMutation(m -> m.mutateAttribute((p, level) -> {
						p.planet.setDeuteriumSynthesizer(level);
						return level;
					}).withRowUpdate(true).filterAccept((p, level) -> level >= 0 ? null : "No negative buildings").asText(SpinnerFormat.INT)
						.clicks(1))//
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> resourceBldgs = ObservableCollection.of(planetColumnType,
			new CategoryRenderStrategy<PlanetWithProduction, Integer>("Crawlers", TypeTokens.get().INT, p -> p.planet.getCrawlers())
				.filterable(false).withWidths(60, 60, 60)//
				.withMutation(m -> m.mutateAttribute((p, level) -> {
					p.planet.setCrawlers(level);
					return level;
				}).withRowUpdate(true).filterAccept((p, level) -> level >= 0 ? null : "No negative buildings").asText(SpinnerFormat.INT)
					.clicks(1)), //
			new CategoryRenderStrategy<PlanetWithProduction, Integer>("Solar", TypeTokens.get().INT, p -> p.planet.getSolarPlant())
				.filterable(false).withWidths(45, 45, 45)//
				.withMutation(m -> m.mutateAttribute((p, level) -> {
					p.planet.setSolarPlant(level);
					return level;
				}).withRowUpdate(true).filterAccept((p, level) -> level >= 0 ? null : "No negative buildings").asText(SpinnerFormat.INT)
					.clicks(1)), //
			new CategoryRenderStrategy<PlanetWithProduction, Integer>("Fusion", TypeTokens.get().INT, p -> p.planet.getFusionReactor())
				.filterable(false).withWidths(45, 45, 45)//
				.withMutation(m -> m.mutateAttribute((p, level) -> {
					p.planet.setFusionReactor(level);
					return level;
				}).withRowUpdate(true).filterAccept((p, level) -> level >= 0 ? null : "No negative buildings").asText(SpinnerFormat.INT)
					.clicks(1)) //
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> storageColumns = ObservableCollection.of(planetColumnType,
			new CategoryRenderStrategy<PlanetWithProduction, Integer>("M Stor", TypeTokens.get().INT, p -> p.planet.getMetalStorage())
				.filterable(false).withWidths(45, 45, 45)//
				.withMutation(m -> m.mutateAttribute((p, level) -> {
					p.planet.setMetalStorage(level);
					return level;
				}).filterAccept((p, level) -> level >= 0 ? null : "No negative buildings").asText(SpinnerFormat.INT).clicks(1)), //
			new CategoryRenderStrategy<PlanetWithProduction, Integer>("C Stor", TypeTokens.get().INT, p -> p.planet.getCrystalStorage())
				.filterable(false).withWidths(45, 45, 45)//
				.withMutation(m -> m.mutateAttribute((p, level) -> {
					p.planet.setCrystalStorage(level);
					return level;
				}).filterAccept((p, level) -> level >= 0 ? null : "No negative buildings").asText(SpinnerFormat.INT).clicks(1)), //
			new CategoryRenderStrategy<PlanetWithProduction, Integer>("D Stor", TypeTokens.get().INT, p -> p.planet.getDeuteriumStorage())
				.filterable(false).withWidths(45, 45, 45)//
				.withMutation(m -> m.mutateAttribute((p, level) -> {
					p.planet.setDeuteriumStorage(level);
					return level;
				}).filterAccept((p, level) -> level >= 0 ? null : "No negative buildings").asText(SpinnerFormat.INT).clicks(1)) //
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> productionColumns = ObservableCollection
			.of(planetColumnType,
				new CategoryRenderStrategy<PlanetWithProduction, String>("M Production", TypeTokens.get().STRING,
					planet -> printProduction(planet.metal.totalNet, productionType.get())).withWidths(80, 80, 80), //
				new CategoryRenderStrategy<PlanetWithProduction, String>("C Production", TypeTokens.get().STRING,
					planet -> printProduction(planet.crystal.totalNet, productionType.get())).withWidths(80, 80, 80), //
				new CategoryRenderStrategy<PlanetWithProduction, String>("D Production", TypeTokens.get().STRING,
					planet -> printProduction(planet.deuterium.totalNet, productionType.get())).withWidths(80, 80, 80))
			.flow().refresh(productionType.noInitChanges()).collect();

		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> advancedEColumns = ObservableCollection.of(planetColumnType,
			new CategoryRenderStrategy<PlanetWithProduction, String>("M Energy", TypeTokens.get().STRING,
				p -> printProduction(p.energy.byType.getOrDefault(ProductionSource.MetalMine, 0), productionType.get())).withWidths(65, 65,
					65), //
			new CategoryRenderStrategy<PlanetWithProduction, String>("C Energy", TypeTokens.get().STRING,
				p -> printProduction(p.energy.byType.getOrDefault(ProductionSource.CrystalMine, 0), productionType.get())).withWidths(65,
					65, 65), //
			new CategoryRenderStrategy<PlanetWithProduction, String>("D Energy", TypeTokens.get().STRING,
				p -> printProduction(p.energy.byType.getOrDefault(ProductionSource.DeuteriumSynthesizer, 0), productionType.get()))
					.withWidths(65, 65, 65), //
			new CategoryRenderStrategy<PlanetWithProduction, String>("S Energy", TypeTokens.get().STRING,
				p -> printProduction(p.energy.byType.getOrDefault(ProductionSource.Solar, 0), productionType.get())).withWidths(65, 65, 65), //
			new CategoryRenderStrategy<PlanetWithProduction, String>("F Energy", TypeTokens.get().STRING,
				p -> printProduction(p.energy.byType.getOrDefault(ProductionSource.Fusion, 0), productionType.get())).withWidths(65, 65,
					65), //
			new CategoryRenderStrategy<PlanetWithProduction, String>("Sat Energy", TypeTokens.get().STRING,
				p -> printProduction(p.energy.byType.getOrDefault(ProductionSource.Satellite, 0), productionType.get())).withWidths(65, 65,
					65) //
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> basicEColumns = ObservableCollection.of(planetColumnType,
			new CategoryRenderStrategy<PlanetWithProduction, String>("+E", TypeTokens.get().STRING,
				p -> printProduction(p.energy.totalProduction, productionType.get())).withWidths(60, 60, 60), //
			new CategoryRenderStrategy<PlanetWithProduction, String>("-E", TypeTokens.get().STRING,
				p -> printProduction(p.energy.totalConsumption, productionType.get())).withWidths(60, 60, 60), //
			new CategoryRenderStrategy<PlanetWithProduction, String>("Total E", TypeTokens.get().STRING,
				p -> printProduction(p.energy.totalNet, productionType.get())).withWidths(60, 60, 60) //
		);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> emptyColumns = ObservableCollection.of(planetColumnType);
		ObservableCollection<CategoryRenderStrategy<PlanetWithProduction, ?>> planetColumns = ObservableCollection
			.flattenCollections(planetColumnType, //
				basicPlanetColumns, //
				ObservableCollection.flattenValue(showTemps.map(show -> show ? tempColumns : emptyColumns)), //
				ObservableCollection.flattenValue(showMines.map(show -> show ? mineColumns : emptyColumns)), //
				ObservableCollection.flattenValue(showResourceBldgs.map(show -> show ? resourceBldgs : emptyColumns)), //
				ObservableCollection.flattenValue(showStorage.map(show -> show ? storageColumns : emptyColumns)), //
				ObservableCollection.flattenValue(showAdvancedEnergy.map(show -> show ? advancedEColumns : emptyColumns)), //
				ObservableCollection.flattenValue(showBasicEnergy.map(show -> show ? basicEColumns : emptyColumns)), //
				ObservableCollection.flattenValue(productionType.map(type -> type.type == null ? emptyColumns : productionColumns))//
			).collect();
		PanelPopulation.populateVPanel(this, Observable.empty())//
			.addSplit(true,
				mainSplit -> mainSplit.withSplitLocation(250).fill().fillV()//
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
								.withColumn("Reference", Account.class, this::getReferenceAccount,
									refColumn -> refColumn//
										.formatText(account -> account == null ? "" : account.getName()))//
								.withColumn("Eco Points", String.class, account -> OGameUniGui.this.printPoints(account), //
									pointsColumn -> pointsColumn.withWidths(75, 75, 75))//
								.withSelection(theSelectedAccount, false)//
								.withAdd(() -> theAccounts.create()//
									.with("name",
										StringUtils.getNewItemName(theAccounts.getValues(), Account::getName, "New Account",
											StringUtils.PAREN_DUPLICATES))//
									.with("id", getNewId())//
									.create().get(), null)//
								.withRemove(accounts -> theAccounts.getValues().removeAll(accounts), action -> action//
									.confirmForItems("Delete Accounts?", "Are you sure you want to delete ", null, true))//
					)//
					).lastV(selectedAccountPanel -> selectedAccountPanel//
						.fill().visibleWhen(theSelectedAccount.map(account -> account != null))//
						.addTextField("Name:",
							theSelectedAccount.asFieldEditor(TypeTokens.get().STRING, Account::getName, Account::setName, null),
							SpinnerFormat.NUMERICAL_TEXT, f -> f.fill())//
						.addComboField("Compare To:",
							theSelectedAccount.asFieldEditor(TypeTokens.get().of(Account.class), this::getReferenceAccount,
								(selectedAccount, refAccount) -> selectedAccount
									.setReferenceAccount(refAccount == null ? 0 : refAccount.getId()),
								null),
							referenceAccounts, f -> f.fill())//
						.addTextField("Universe Name:",
							theSelectedAccount.asFieldEditor(TypeTokens.get().STRING, account -> account.getUniverse().getName(),
								(account, name) -> account.getUniverse().setName(name), null),
							Format.TEXT, f -> f.fill())//
						.addTextField("Economy Speed:",
							theSelectedAccount.asFieldEditor(TypeTokens.get().INT, account -> account.getUniverse().getEconomySpeed(),
								(account, speed) -> account.getUniverse().setEconomySpeed(speed), null),
							SpinnerFormat.INT, f -> f.fill())//
						.addTextField("Research Speed:",
							theSelectedAccount.asFieldEditor(TypeTokens.get().INT, account -> account.getUniverse().getResearchSpeed(),
								(account, speed) -> account.getUniverse().setResearchSpeed(speed), null),
							SpinnerFormat.INT, f -> f.fill())//
						.addComboField("Account Class:",
							theSelectedAccount.asFieldEditor(TypeTokens.get().of(AccountClass.class), Account::getGameClass,
								Account::setGameClass, null),
							ObservableCollection.of(TypeTokens.get().of(AccountClass.class), AccountClass.values()), //
							classEditor -> classEditor.fill().withValueTooltip(clazz -> describeClass(clazz)))//
						.addHPanel("Show Fields:", new JustifiedBoxLayout(false).setMainAlignment(JustifiedBoxLayout.Alignment.LEADING),
							fieldPanel -> fieldPanel//
								.addCheckField("Temps:", showTemps, null).spacer(3)//
								.addCheckField("Mines:", showMines, null).spacer(3)//
								.addCheckField("Resource Buildings:", showResourceBldgs, null).spacer(3)//
								.addCheckField("Storage:", showStorage, null).spacer(3)//
								.addCheckField("Basic Energy:", showBasicEnergy, null).spacer(3)//
								.addCheckField("Advanced Energy:", showAdvancedEnergy, null).spacer(3)//
								.addComboField("Production", productionType, null, ProductionDisplayType.values())//
						)//
						.addTable(selectedPlanets, planetTable -> planetTable//
							.fill().withColumns(basicPlanetColumns)// A little hacky, but the next line tells the column the item name
							// function
							.withNameColumn(p -> p.planet.getName(), (p, name) -> p.planet.setName(name), false,
								nameCol -> nameCol.withWidths(50, 100, 150))//
							.withColumns(planetColumns)//
							.withAdd(() -> createPlanet(selectedPlanets), null)//
							.withRemove(planets -> theSelectedAccount.get().getPlanets().getValues().removeAll(planets), action -> action//
								.confirmForItems("Delete Planets?", "Are you sure you want to delete ", null, true))//
						)//
						.addTable(researchColl,
							researchTable -> researchTable.fill()//
								.withColumn("Energy", int.class, acct -> acct.getEnergy(),
									energyCol -> energyCol.withWidths(65, 65, 65)
										.withMutation(m -> m.mutateAttribute(Research::setEnergy).asText(SpinnerFormat.INT)))//
								.withColumn("Laser", int.class, acct -> acct.getLaser(),
									laserCol -> laserCol.withWidths(65, 65, 65)
										.withMutation(m -> m.mutateAttribute(Research::setLaser).asText(SpinnerFormat.INT)))//
								.withColumn("Ion", int.class, acct -> acct.getIon(),
									ionCol -> ionCol.withWidths(65, 65, 65)
										.withMutation(m -> m.mutateAttribute(Research::setIon).asText(SpinnerFormat.INT)))//
								.withColumn("Hyperspace", int.class, acct -> acct.getHyperspace(),
									hypspcCol -> hypspcCol.withWidths(65, 65, 65)
										.withMutation(m -> m.mutateAttribute(Research::setHyperspace).asText(SpinnerFormat.INT)))//
								.withColumn("Plasma", int.class, acct -> acct.getPlasma(),
									plasmaCol -> plasmaCol.withWidths(65, 65, 65)
										.withMutation(m -> m.mutateAttribute(Research::setPlasma).asText(SpinnerFormat.INT)))//
								.withColumn("Combustion", int.class, acct -> acct.getCombustionDrive(),
									combstnCol -> combstnCol.withWidths(65, 65, 65)
										.withMutation(m -> m.mutateAttribute(Research::setCombustionDrive).asText(SpinnerFormat.INT)))//
								.withColumn("Impulse", int.class, acct -> acct.getImpulseDrive(),
									impulseCol -> impulseCol.withWidths(65, 65, 65)
										.withMutation(m -> m.mutateAttribute(Research::setImpulseDrive).asText(SpinnerFormat.INT)))//
								.withColumn("Hyperdrive", int.class, acct -> acct.getHyperspaceDrive(),
									hypdrvCol -> hypdrvCol.withWidths(65, 65, 65)
										.withMutation(m -> m.mutateAttribute(Research::setHyperspaceDrive).asText(SpinnerFormat.INT)))//
								.withColumn("Espionage", int.class, acct -> acct.getEspionage(),
									espCol -> espCol.withWidths(65, 65, 65)
										.withMutation(m -> m.mutateAttribute(Research::setEspionage).asText(SpinnerFormat.INT)))//
								.withColumn("Computer", int.class, acct -> acct.getComputer(),
									comptrCol -> comptrCol.withWidths(65, 65, 65)
										.withMutation(m -> m.mutateAttribute(Research::setComputer).asText(SpinnerFormat.INT)))//
								.withColumn("Astro", int.class, acct -> acct.getAstrophysics(),
									astroCol -> astroCol.withWidths(65, 65, 65)
										.withMutation(m -> m.mutateAttribute(Research::setAstrophysics).asText(SpinnerFormat.INT)))//
								.withColumn("IRN", int.class, acct -> acct.getIntergalacticResearchNetwork(),
									irnCol -> irnCol.withWidths(65, 65, 65).withMutation(
										m -> m.mutateAttribute(Research::setIntergalacticResearchNetwork).asText(SpinnerFormat.INT)))//
								.withColumn("Graviton", int.class, acct -> acct.getGraviton(),
									gravCol -> gravCol.withWidths(65, 65, 65)
										.withMutation(m -> m.mutateAttribute(Research::setGraviton).asText(SpinnerFormat.INT)))//
								.withColumn("Weapons", int.class, acct -> acct.getWeapons(),
									weaponCol -> weaponCol.withWidths(65, 65, 65)
										.withMutation(m -> m.mutateAttribute(Research::setWeapons).asText(SpinnerFormat.INT)))//
								.withColumn("Shielding", int.class, acct -> acct.getShielding(),
									shieldCol -> shieldCol.withWidths(65, 65, 65)
										.withMutation(m -> m.mutateAttribute(Research::setShielding).asText(SpinnerFormat.INT)))//
								.withColumn("Armor", int.class, acct -> acct.getArmor(), armorCol -> armorCol.withWidths(65, 65, 65)
									.withMutation(m -> m.mutateAttribute(Research::setArmor).asText(SpinnerFormat.INT)))//
				)//
				)//
		);
	}

	Account getReferenceAccount(Account account) {
		if (account.getReferenceAccount() <= 0) {
			return null;
		}
		for (Account a : theAccounts.getValues()) {
			if (a.getId() == account.getReferenceAccount()) {
				return a;
			}
		}
		return null;
	}

	private static final DecimalFormat WHOLE_FORMAT = new DecimalFormat("#,##0");
	private static final DecimalFormat TWO_DIGIT_FORMAT = new DecimalFormat("#,##0.00");

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
			return TWO_DIGIT_FORMAT.format(value / 1E6) + "M";
		} else {
			return TWO_DIGIT_FORMAT.format(value / 1E9) + "B";
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

	String printProduction(double production, ProductionDisplayType time) {
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
			str.append(TWO_DIGIT_FORMAT.format(production / 1E6)).append('M');
		} else if (production < 1E12) {
			str.append(TWO_DIGIT_FORMAT.format(production / 1E9)).append('B');
		} else {
			str.append(TWO_DIGIT_FORMAT.format(production / 1E12)).append('T');
		}
		return str.toString();
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
		OGameUniGui ui = new OGameUniGui(config, ruleSets,
			config.asValue(TypeTokens.get().of(Account.class)).at("accounts/account").buildEntitySet());
		JFrame frame = new JFrame("OneSAF Runner");
		// frame.setContentPane(ui);
		frame.getContentPane().add(ui);
		frame.setVisible(true);
		frame.pack();
		ObservableSwingUtils.configureFrameBounds(frame, config);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}
}
