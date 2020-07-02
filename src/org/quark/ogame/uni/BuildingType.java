package org.quark.ogame.uni;

public enum BuildingType {
	MetalMine("Metal", true, false),
	CrystalMine("Crystl", true, false),
	DeuteriumSynthesizer("Deut", true, false), //
	SolarPlant("Solar", true, false),
	FusionReactor("Fusion", true, false), //
	MetalStorage("M Stg", true, false),
	CrystalStorage("C Stg", true, false),
	DeuteriumStorage("D Stg", true, false), //
	RoboticsFactory("Robot", true, true),
	Shipyard("SY", true, true),
	ResearchLab("Lab", true, false), //
	AllianceDepot("Ally D", true, false),
	MissileSilo("Silo", true, false),
	NaniteFactory("Nanite", true, false), //
	Terraformer("Terra", true, false),
	SpaceDock("Dock", true, false), //
	// Moon-only Buildings
	LunarBase("Base", false, true),
	SensorPhalanx("Lanx", false, true),
	JumpGate("Gate", false, true);

	public final String shortName;
	public final boolean isPlanetBuilding;
	public final boolean isMoonBuilding;

	private BuildingType(String shortName, boolean forPlanet, boolean forMoon) {
		this.shortName = shortName;
		this.isPlanetBuilding = forPlanet;
		this.isMoonBuilding = forMoon;
	}

	public AccountUpgradeType getUpgrade() {
		return AccountUpgradeType.getBuildingUpgrade(this);
	}
}
