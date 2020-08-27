package org.quark.ogame.uni;

public interface CondensedFleet extends Fleet {
	@Override
	abstract int getItems(ShipyardItemType type);

	@Override
	abstract Fleet setItems(ShipyardItemType type, int number);

	@Override
	default int getLightFighters() {
		return getItems(ShipyardItemType.LightFighter);
	}

	@Override
	default Fleet setLightFighters(int lightFighters) {
		return setItems(ShipyardItemType.LightFighter, lightFighters);
	}

	@Override
	default int getHeavyFighters() {
		return getItems(ShipyardItemType.HeavyFighter);
	}

	@Override
	default Fleet setHeavyFighters(int heavyFighters) {
		return setItems(ShipyardItemType.HeavyFighter, heavyFighters);
	}

	@Override
	default int getCruisers() {
		return getItems(ShipyardItemType.Cruiser);
	}

	@Override
	default Fleet setCruisers(int cruisers) {
		return setItems(ShipyardItemType.Cruiser, cruisers);
	}

	@Override
	default int getBattleShips() {
		return getItems(ShipyardItemType.BattleShip);
	}

	@Override
	default Fleet setBattleShips(int battleShips) {
		return setItems(ShipyardItemType.BattleShip, battleShips);
	}

	@Override
	default int getBattleCruisers() {
		return getItems(ShipyardItemType.BattleCruiser);
	}

	@Override
	default Fleet setBattleCruisers(int battleCruisers) {
		return setItems(ShipyardItemType.BattleCruiser, battleCruisers);
	}

	@Override
	default int getBombers() {
		return getItems(ShipyardItemType.Bomber);
	}

	@Override
	default Fleet setBombers(int bombers) {
		return setItems(ShipyardItemType.Bomber, bombers);
	}

	@Override
	default int getDestroyers() {
		return getItems(ShipyardItemType.Destroyer);
	}

	@Override
	default Fleet setDestroyers(int destroyers) {
		return setItems(ShipyardItemType.Destroyer, destroyers);
	}

	@Override
	default int getDeathStars() {
		return getItems(ShipyardItemType.DeathStar);
	}

	@Override
	default Fleet setDeathStars(int deathStars) {
		return setItems(ShipyardItemType.DeathStar, deathStars);
	}

	@Override
	default int getReapers() {
		return getItems(ShipyardItemType.Reaper);
	}

	@Override
	default Fleet setReapers(int reapers) {
		return setItems(ShipyardItemType.Reaper, reapers);
	}

	@Override
	default int getPathFinders() {
		return getItems(ShipyardItemType.PathFinder);
	}

	@Override
	default Fleet setPathFinders(int pathFinders) {
		return setItems(ShipyardItemType.PathFinder, pathFinders);
	}

	@Override
	default int getSmallCargos() {
		return getItems(ShipyardItemType.SmallCargo);
	}

	@Override
	default Fleet setSmallCargos(int smallCargos) {
		return setItems(ShipyardItemType.SmallCargo, smallCargos);
	}

	@Override
	default int getLargeCargos() {
		return getItems(ShipyardItemType.LargeCargo);
	}

	@Override
	default Fleet setLargeCargos(int largeCargos) {
		return setItems(ShipyardItemType.LargeCargo, largeCargos);
	}

	@Override
	default int getColonyShips() {
		return getItems(ShipyardItemType.ColonyShip);
	}

	@Override
	default Fleet setColonyShips(int colonyShips) {
		return setItems(ShipyardItemType.ColonyShip, colonyShips);
	}

	@Override
	default int getRecyclers() {
		return getItems(ShipyardItemType.Recycler);
	}

	@Override
	default Fleet setRecyclers(int recyclers) {
		return setItems(ShipyardItemType.Recycler, recyclers);
	}

	@Override
	default int getEspionageProbes() {
		return getItems(ShipyardItemType.EspionageProbe);
	}

	@Override
	default Fleet setEspionageProbes(int espionageProbes) {
		return setItems(ShipyardItemType.EspionageProbe, espionageProbes);
	}
}
