package org.quark.ogame.uni;

public enum ResearchType {
	Energy("Energy"),
	Laser("Laser"),
	Ion("Ion"),
	Hyperspace("Hyperspace"), //
	Plasma("Plasma"),
	Combustion("Combustion"),
	Impulse("Impulse"),
	Hyperdrive("Hyperdrive"), //
	Espionage("Espionage"),
	Computer("Computer"),
	Astrophysics("Astro"),
	IntergalacticResearchNetwork("IRN"), //
	Graviton("Graviton"),
	Weapons("Weapons"),
	Shielding("Shielding"),
	Armor("Armor");

	public final String shortName;

	private ResearchType(String shortName) {
		this.shortName = shortName;
	}

	public AccountUpgradeType getUpgrade() {
		return AccountUpgradeType.getResearchUpgrade(this);
	}
}
