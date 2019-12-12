package org.quark.ogame;

public class OGameConstants {
	public static double CRAWLER_BONUS = .0003;
	public static int CRAWLER_ENERGY = 50;
	private static int CRAWLER_CAP_PER_TOTAL_MINES = 8;

	public static int getCrawlerCap(OGameState state, int resourceType) {
		int totalMines = state.getBuildingLevel(OGameBuildingType.Metal)//
			+ state.getBuildingLevel(OGameBuildingType.Crystal)//
			+ state.getBuildingLevel(OGameBuildingType.Deuterium);
		return totalMines * CRAWLER_CAP_PER_TOTAL_MINES;
	}
}
