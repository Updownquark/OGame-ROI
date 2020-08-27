package org.quark.ogame.uni.ui;

import java.awt.EventQueue;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.util.TypeTokens;
import org.observe.util.swing.JustifiedBoxLayout;
import org.observe.util.swing.ObservableCellRenderer;
import org.observe.util.swing.PanelPopulation;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.io.Format;
import org.qommons.io.SpinnerFormat;
import org.quark.ogame.OGameUtils;
import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.FleetRules;
import org.quark.ogame.uni.PlannedFlight;
import org.quark.ogame.uni.ShipyardItemType;

public class FlightPanel {
	static class ShipAmount {
		final ShipyardItemType type;
		int amount;

		ShipAmount(ShipyardItemType type, int amount) {
			this.type = type;
			this.amount = amount;
		}
	}

	private final OGameUniGui theUniGui;
	private final SettableValue<PlannedFlight> theSelectedFlight;
	private final ObservableSortedSet<ShipAmount> theShipAmounts;
	private final ObservableCollection<ShipyardItemType> theAvailableShipsToAdd;
	private final SettableValue<ShipyardItemType> theShipsToAdd;
	private final ObservableValue<Integer> theDistance;
	private final ObservableValue<Duration> theFlightTime;
	private final ObservableValue<Long> theFuelConsumption;

	public FlightPanel(OGameUniGui uniGui) {
		theUniGui = uniGui;
		theSelectedFlight = SettableValue.build(PlannedFlight.class).safe(false).build();
		theShipAmounts = ObservableSortedSet.build(ShipAmount.class, (sa1, sa2) -> sa1.type.compareTo(sa2.type)).safe(false).build();

		boolean[] shipAmountChanging = new boolean[1];
		theSelectedFlight.changes().act(evt -> {
			if (shipAmountChanging[0]) {
				return;
			}
			shipAmountChanging[0] = true;
			try {
				theShipAmounts.clear();
				if (evt.getNewValue() == null) {
					return;
				}
				for (ShipyardItemType type : ShipyardItemType.values()) {
					int amount = evt.getNewValue().getFleet().getItems(type);
					if (amount > 0) {
						theShipAmounts.add(new ShipAmount(type, amount));
					}
				}
			} finally {
				shipAmountChanging[0] = false;
			}
		});
		theShipAmounts.changes().act(evt -> {
			if (shipAmountChanging[0]) {
				return;
			}
			shipAmountChanging[0] = true;
			try {
				switch (evt.type) {
				case add:
					for (ShipAmount sa : evt.getValues()) {
						theSelectedFlight.get().getFleet().setItems(sa.type, sa.amount);
					}
					break;
				case remove:
					for (ShipAmount sa : evt.getValues()) {
						theSelectedFlight.get().getFleet().setItems(sa.type, 0);
					}
					break;
				case set:
					for (ShipAmount sa : evt.getValues()) {
						theSelectedFlight.get().getFleet().setItems(sa.type, sa.amount);
					}
					break;
				}
				ObservableCollection<PlannedFlight> flights = theUniGui.getSelectedAccount().get()
					.getPlannedFlights().getValues();

				flights.mutableElement(flights.getElement(theSelectedFlight.get(), true).getElementId())//
					.set(theSelectedFlight.get());
			} finally {
				shipAmountChanging[0] = false;
			}
		});

		theAvailableShipsToAdd = ObservableCollection
			.of(TypeTokens.get().of(ShipyardItemType.class), //
				Arrays.stream(ShipyardItemType.values()).filter(type -> type.mobile).collect(Collectors.toList()))//
			.flow().whereContained(theShipAmounts.flow().map(TypeTokens.get().of(ShipyardItemType.class), sa -> sa.type), false).collect();
		theShipsToAdd = SettableValue.build(ShipyardItemType.class).safe(false).build();
		theShipsToAdd.changes().act(evt -> {
			if (evt.getNewValue() == null) {
				return;
			}
			theShipAmounts.add(new ShipAmount(evt.getNewValue(), 1));
			EventQueue.invokeLater(() -> theShipsToAdd.set(null, null));
		});

		theDistance = theSelectedFlight.map(f -> f == null ? 0 : getFlightDistance(f));
		theFlightTime = theSelectedFlight.map(f -> f == null ? Duration.ZERO : getFlightTime(f));
		theFuelConsumption = theSelectedFlight.map(f -> f == null ? 0 : getFuelConsumption(f));
	}

	public void addFlightPanel(PanelPopulation.PanelPopulator<?, ?> panel) {
		ObservableCollection<PlannedFlight> flights = ObservableCollection.<PlannedFlight> flattenValue(
			theUniGui.getSelectedAccount().map(ObservableCollection.TYPE_KEY.getCompoundType(PlannedFlight.class),
				account -> account.getPlannedFlights().getValues(), opts -> opts.nullToNull(true)));
		panel.addTable(flights, table -> {
			table.fill().dragSourceRow(d -> d.toObject()).dragAcceptRow(d -> d.fromObject())//
				.withColumn("Name", String.class, PlannedFlight::getName, nameCol -> {
					nameCol.withMutation(mut -> mut.mutateAttribute(PlannedFlight::setName).asText(SpinnerFormat.TEXT));
				}).withColumn("Ships", String.class, FlightPanel::countShips, col -> col.withWidth("pref", 150))//
				.withColumn("Flight Time", Duration.class, this::getFlightTime,
					col -> col.withWidth("pref", 100).formatText(d -> QommonsUtils.printDuration(d, true)))//
				.withColumn("Fuel Consumption", String.class, flight -> OGameUtils.printResourceAmount(getFuelConsumption(flight)),
					col -> col.withWidth("pref", 100))//
				.withAdd(() -> theUniGui.getSelectedAccount().get().getPlannedFlights().create()//
					.with(PlannedFlight::getName,
						StringUtils.getNewItemName(flights, PlannedFlight::getName, "New Flight", StringUtils.PAREN_DUPLICATES))//
					.with(PlannedFlight::getSourceGalaxy, 1).with(PlannedFlight::getSourceSystem, 1).with(PlannedFlight::getSourceSlot, 1)//
					.with(PlannedFlight::getDestGalaxy, 1).with(PlannedFlight::getDestSystem, 1).with(PlannedFlight::getDestSlot, 1)//
					.with(PlannedFlight::getSpeed, 100)//
					.create().get(), null)//
				.withRemove(null, null)//
				.withSelection(theSelectedFlight, false)//
			;
		}).addVPanel(this::addConfigPanel);
	}

	static String countShips(PlannedFlight flight) {
		StringBuilder sb = new StringBuilder();
		for (ShipyardItemType type : ShipyardItemType.values()) {
			int amount = type.mobile ? flight.getFleet().getItems(type) : 0;
			if (amount > 0) {
				if (sb.length() > 0) {
					sb.append(", ");
				}
				INT_FORMAT.append(sb, amount * 1.0);
				sb.append(' ').append(OGameUtils.abbreviate(type));
			}
		}
		if (sb.length() == 0) {
			sb.append("None");
		}
		return sb.toString();
	}

	int getFlightDistance(PlannedFlight flight) {
		int distance = theUniGui.getRules().get().fleet().getDistance(theUniGui.getSelectedAccount().get().getUniverse(), //
			flight.getSourceGalaxy(), flight.getSourceSystem(), flight.getSourceSlot(), //
			flight.getDestGalaxy(), flight.getDestSystem(), flight.getDestSlot());
		return distance;
	}

	Duration getFlightTime(PlannedFlight flight) {
		int distance = getFlightDistance(flight);
		Account account = theUniGui.getSelectedAccount().get();
		FleetRules fleet = theUniGui.getRules().get().fleet();
		Duration maxFlightTime = Duration.ZERO;
		for (ShipyardItemType type : ShipyardItemType.values()) {
			if (!type.mobile || flight.getFleet().getItems(type) == 0) {
				continue;
			}
			Duration shipFlightTime = fleet.getFlightTime(//
				fleet.getSpeed(type, account), distance, flight.getSpeed());
			if (shipFlightTime.compareTo(maxFlightTime) > 0) {
				maxFlightTime = shipFlightTime;
			}
		}
		return maxFlightTime;
	}

	long getFuelConsumption(PlannedFlight flight) {
		int distance = getFlightDistance(flight);
		Duration flightTime = getFlightTime(flight);
		Account account = theUniGui.getSelectedAccount().get();
		FleetRules fleet = theUniGui.getRules().get().fleet();
		double fuel = 0;
		for (ShipyardItemType type : ShipyardItemType.values()) {
			int amount = type.mobile ? flight.getFleet().getItems(type) : 0;
			if (amount > 0) {
				double rate = fleet.getFuelConsumption(type, account, distance, flightTime);
				fuel += rate * amount;
			}
		}
		return (long) Math.ceil(fuel);
	}

	double getSpeed(ShipyardItemType type) {
		return theUniGui.getRules().get().fleet().getSpeed(type, theUniGui.getSelectedAccount().get());
	}

	double getCargo(ShipyardItemType type, int count) {
		return theUniGui.getRules().get().fleet().getCargoSpace(type, theUniGui.getSelectedAccount().get()) * 1.0 * count;
	}

	static final Format<Double> INT_FORMAT = Format.doubleFormat("#,##0");

	void addConfigPanel(PanelPopulation.PanelPopulator<?, ?> panel) {
		panel.fill().visibleWhen(theSelectedFlight.map(f -> f != null))//
			.addHPanel("Start:", new JustifiedBoxLayout(false).setMainAlignment(JustifiedBoxLayout.Alignment.LEADING), start -> {
				start
					.addTextField(null,
						theSelectedFlight.asFieldEditor(TypeTokens.get().of(Integer.class), PlannedFlight::getSourceGalaxy,
							PlannedFlight::setSourceGalaxy, null),
						SpinnerFormat.wrapAround(SpinnerFormat.INT, () -> 1,
							() -> theUniGui.getSelectedAccount().get().getUniverse().getGalaxies()),
						tf -> tf.modifyEditor(tf2 -> tf2.withColumns(1)))//
					.addTextField(null,
						theSelectedFlight.asFieldEditor(TypeTokens.get().of(Integer.class), PlannedFlight::getSourceSystem,
							PlannedFlight::setSourceSystem, null),
						SpinnerFormat.wrapAround(SpinnerFormat.INT, () -> 1, () -> 499), tf -> tf.modifyEditor(tf2 -> tf2.withColumns(3)))//
					.addTextField(null,
						theSelectedFlight.asFieldEditor(TypeTokens.get().of(Integer.class), PlannedFlight::getSourceSlot,
							PlannedFlight::setSourceSlot, null),
						SpinnerFormat.wrapAround(SpinnerFormat.INT, () -> 1, () -> 15), tf -> tf.modifyEditor(tf2 -> tf2.withColumns(2)));//
			}).addHPanel("Destination:", new JustifiedBoxLayout(false).setMainAlignment(JustifiedBoxLayout.Alignment.LEADING), dest -> {
				dest.addTextField(null,
					theSelectedFlight.asFieldEditor(TypeTokens.get().of(Integer.class), PlannedFlight::getDestGalaxy,
						PlannedFlight::setDestGalaxy, null),
					SpinnerFormat.wrapAround(SpinnerFormat.INT, () -> 1,
						() -> theUniGui.getSelectedAccount().get().getUniverse().getGalaxies()),
					tf -> tf.modifyEditor(tf2 -> tf2.withColumns(1)))//
					.addTextField(null,
						theSelectedFlight.asFieldEditor(TypeTokens.get().of(Integer.class), PlannedFlight::getDestSystem,
							PlannedFlight::setDestSystem, null),
						SpinnerFormat.wrapAround(SpinnerFormat.INT, () -> 1, () -> 499), tf -> tf.modifyEditor(tf2 -> tf2.withColumns(3)))//
					.addTextField(null,
						theSelectedFlight.asFieldEditor(TypeTokens.get().of(Integer.class), PlannedFlight::getDestSlot,
							PlannedFlight::setDestSlot, null),
						SpinnerFormat.wrapAround(SpinnerFormat.INT, () -> 1, () -> 15), tf -> tf.modifyEditor(tf2 -> tf2.withColumns(2)));
			})
			.addComboField("Speed:",
				theSelectedFlight.asFieldEditor(TypeTokens.get().of(Integer.class), PlannedFlight::getSpeed, PlannedFlight::setSpeed, null),
				combo -> combo.renderWith(ObservableCellRenderer.formatted(speed -> speed + "%"))//
				, 100, 90, 80, 70, 60, 50, 40, 30, 20, 10)//
			.addComboField("Add Ships:", theShipsToAdd, theAvailableShipsToAdd, null)//
			.addTable(theShipAmounts, table -> {
				table.fill()//
					.withColumn("Ship Type", ShipyardItemType.class, amt -> amt.type, null)//
					.withColumn("Amount", Integer.class, amt -> amt.amount,
						col -> col.formatText(i -> INT_FORMAT.format(i * 1.0)).withMutation(
							mut -> mut.mutateAttribute((sa, amt) -> sa.amount = amt).asText(SpinnerFormat.INT).withRowUpdate(true)))//
					.withColumn("Speed", Double.class, amt -> getSpeed(amt.type),
						col -> col.formatText((sa, speed) -> INT_FORMAT.format(speed)))//
					.withColumn("Storage", Double.class, amt -> getCargo(amt.type, amt.amount),
						col -> col.formatText((sa, speed) -> INT_FORMAT.format(speed)))//
					.withRemove(null, null)//
				;
			})//
			.addLabel("Distance:", theDistance.map(i -> i * 1.0), INT_FORMAT, null)//
			.addLabel("Flight Time:", theFlightTime, Format.DURATION, null)//
			.addLabel("Fuel Consumption:", theFuelConsumption.map(i -> i * 1.0), INT_FORMAT, null)//
		;
	}
}
