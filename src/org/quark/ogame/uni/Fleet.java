package org.quark.ogame.uni;

public interface Fleet extends ShipyardItemSet {
	int getLightFighters();
	Fleet setLightFighters(int lightFighters);

	int getHeavyFighters();
	Fleet setHeavyFighters(int heavyFighters);

	int getCruisers();
	Fleet setCruisers(int cruisers);

	int getBattleShips();
	Fleet setBattleShips(int battleShips);

	int getBattleCruisers();
	Fleet setBattleCruisers(int battleCruisers);

	int getBombers();
	Fleet setBombers(int bombers);

	int getDestroyers();
	Fleet setDestroyers(int destroyers);

	int getDeathStars();
	Fleet setDeathStars(int deathStars);

	int getReapers();
	Fleet setReapers(int reapers);

	int getPathFinders();
	Fleet setPathFinders(int pathFinders);

	int getSmallCargos();
	Fleet setSmallCargos(int smallCargos);

	int getLargeCargos();
	Fleet setLargeCargos(int largeCargos);

	int getColonyShips();
	Fleet setColonyShips(int colonyShips);

	int getRecyclers();
	Fleet setRecyclers(int recyclers);

	int getEspionageProbes();
	Fleet setEspionageProbes(int espionageProbes);

	@Override
	default int getItems(ShipyardItemType type) {
		if (!type.mobile) {
			return 0;
		}
		switch (type) {
		case LightFighter:
			return getLightFighters();
		case HeavyFighter:
			return getHeavyFighters();
		case Cruiser:
			return getCruisers();
		case BattleShip:
			return getBattleShips();
		case BattleCruiser:
			return getBattleCruisers();
		case Bomber:
			return getBombers();
		case Destroyer:
			return getDestroyers();
		case DeathStar:
			return getDeathStars();
		case Reaper:
			return getReapers();
		case PathFinder:
			return getPathFinders();
		case SmallCargo:
			return getSmallCargos();
		case LargeCargo:
			return getLargeCargos();
		case ColonyShip:
			return getColonyShips();
		case Recycler:
			return getRecyclers();
		case EspionageProbe:
			return getEspionageProbes();
		default:
			throw new IllegalStateException("Unrecognized mobile ship: " + type);
		}
	}

	@Override
	default Fleet setItems(ShipyardItemType type, int number) {
		if (!type.mobile) {
			throw new IllegalArgumentException(type + " is not a mobile ship");
		}
		switch (type) {
		case LightFighter:
			return setLightFighters(number);
		case HeavyFighter:
			return setHeavyFighters(number);
		case Cruiser:
			return setCruisers(number);
		case BattleShip:
			return setBattleShips(number);
		case BattleCruiser:
			return setBattleCruisers(number);
		case Bomber:
			return setBombers(number);
		case Destroyer:
			return setDestroyers(number);
		case DeathStar:
			return setDeathStars(number);
		case Reaper:
			return setReapers(number);
		case PathFinder:
			return setPathFinders(number);
		case SmallCargo:
			return setSmallCargos(number);
		case LargeCargo:
			return setLargeCargos(number);
		case ColonyShip:
			return setColonyShips(number);
		case Recycler:
			return setRecyclers(number);
		case EspionageProbe:
			return setEspionageProbes(number);
		default:
			throw new IllegalStateException("Unrecognized mobile ship: " + type);
		}
	}
}
