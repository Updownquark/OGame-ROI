package org.quark.ogame.uni;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.qommons.io.HtmlNavigator;
import org.qommons.io.HtmlNavigator.Tag;

public class OGamePageReader {
	public static int readEmpireView(Account account, Reader reader, OGameRuleSet rules, Supplier<Planet> createPlanet) throws IOException {
		HtmlNavigator nav = new HtmlNavigator(reader);
		int planetIdx = 0;
		while (nav.find("div", "planet") != null) {
			if (nav.getTop().getClasses().contains("summary")) {
				continue;
			}
			Planet planet;
			if (planetIdx == account.getPlanets().getValues().size()) {
				planet = createPlanet.get();
			} else {
				planet = account.getPlanets().getValues().get(planetIdx);
			}
			parsePlanet(account, planet, nav);
			int terraformerDiff = rules.economy().getFields(planet) - planet.getBaseFields();
			planet.setBaseFields(planet.getBaseFields() - terraformerDiff);
			planetIdx++;
		}
		return planetIdx;
	}

	public static void readEmpireMoonView(Account account, OGameRuleSet rules, Reader reader) throws IOException {
		HtmlNavigator nav = new HtmlNavigator(reader);
		while (nav.find("div", "planet") != null) {
			if (nav.getTop().getClasses().contains("summary")) {
				continue;
			}
			Tag tag = nav.find("div", "planetHead");
			if (tag == null) {
				continue;
			}
			Moon moon = parseMoonHead(account, nav);
			if (moon != null) {
				parsePlanet(account, moon, nav);
				int totalFields = moon.getFieldBonus();
				moon.setFieldBonus(0);
				moon.setFieldBonus(Math.min(0, totalFields - rules.economy().getFields(moon)));
			}
		}
	}

	public static int readOverview(Account account, Reader reader, OGameRuleSet rules, Supplier<Planet> createPlanet) throws IOException {
		HtmlNavigator nav = new HtmlNavigator(reader);
		int planetIdx = 0;
		Tag head;
		if ((head = nav.find("head")) == null) {
			return 0;
		}
		Tag meta;
		while ((meta = nav.find("meta")) != null) {
			String metaName = meta.getAttributes().get("name");
			String content = meta.getAttributes().get("content");
			if (metaName == null || content == null) {
				continue;
			}
			switch (metaName) {
			case "ogame-universe-name":
				account.getUniverse().setName(content);
				break;
			case "ogame-universe-speed":
				int speed = Integer.parseInt(content);
				account.getUniverse().setEconomySpeed(speed);
				if (speed > 1 && account.getUniverse().getResearchSpeed() <= 1) {
					account.getUniverse().setResearchSpeed(speed);
				}
				break;
			case "ogame-universe-speed-fleet":
				account.getUniverse().setFleetSpeed(Integer.parseInt(content));
				break;
			}
		}
		nav.close(head);
		nav.descend(); // Body
		Tag tag = nav.find("div", "fleft");
		if (tag != null) {
			Tag classTag = nav.find("a");
			String title = classTag.getAttributes().get("title");
			int idx = title.indexOf("class:");
			if (idx >= 0) {
				String className = getNextWord(title, idx + "class:".length()).toLowerCase();
				switch (className) {
				case "collector":
					account.setGameClass(AccountClass.Collector);
					idx = title.indexOf("% mine production");
					if (idx >= 0) {
						int lastIdx = idx;
						while (idx >= 0 && Character.isDigit(title.charAt(idx - 1))) {
							idx--;
						}
						account.getUniverse().setCollectorProductionBonus(Integer.parseInt(title.substring(idx, lastIdx)));
					}
					idx = title.indexOf("% energy");
					if (idx >= 0) {
						int lastIdx = idx;
						while (idx >= 0 && Character.isDigit(title.charAt(idx - 1))) {
							idx--;
						}
						account.getUniverse().setCollectorEnergyBonus(Integer.parseInt(title.substring(idx, lastIdx)));
					}
					break;
				case "general":
					account.setGameClass(AccountClass.General);
					break;
				case "discoverer":
					account.setGameClass(AccountClass.Discoverer);
					break;
				}
			}
			nav.close(tag);
		}
		tag = nav.find("div", "fright");
		if (tag != null) {
			Tag officerTag = nav.find("a");
			while (officerTag != null) {
				if (officerTag.getClasses().contains("commander")) {
					account.getOfficers().setCommander(officerTag.getAttributes().get("title").contains("active"));
				} else if (officerTag.getClasses().contains("admiral")) {
					account.getOfficers().setAdmiral(officerTag.getAttributes().get("title").contains("active"));
				} else if (officerTag.getClasses().contains("engineer")) {
					account.getOfficers().setEngineer(officerTag.getAttributes().get("title").contains("active"));
				} else if (officerTag.getClasses().contains("geologist")) {
					account.getOfficers().setGeologist(officerTag.getAttributes().get("title").contains("active"));
				} else if (officerTag.getClasses().contains("technocrat")) {
					account.getOfficers().setTechnocrat(officerTag.getAttributes().get("title").contains("active"));
				}
				nav.close(officerTag);
				officerTag = nav.find("a");
			}
			nav.close(tag);
		}
		while (nav.getTop() != null && !"pageContent".equals(nav.getTop().getAttributes().get("id"))) {
			nav.close(nav.getTop());
		}
		if (nav.find(t -> t.getName().equals("div") && "planetList".equals(t.getAttributes().get("id"))) == null) {
			return 0;
		}
		while (nav.find("div", "smallplanet") != null) {
			Planet planet;
			if (planetIdx == account.getPlanets().getValues().size()) {
				planet = createPlanet.get();
			} else {
				planet = account.getPlanets().getValues().get(planetIdx);
			}
			parsePlanetFromOverview(account, planet, nav);
			int terraformerDiff = rules.economy().getFields(planet) - planet.getBaseFields();
			planet.setBaseFields(planet.getBaseFields() - terraformerDiff);
			planetIdx++;
		}
		return planetIdx;
	}

	private static final Pattern FIELDS_PATTERN = Pattern.compile("/(?<fields>\\d+)\\)");
	private static final Pattern TEMP_PATTERN = Pattern.compile("(?<minTemp>\\-?\\d+)\u00b0C to (?<maxTemp>\\-?\\d+)\u00b0C");

	private static void parsePlanet(Account account, RockyBody place, HtmlNavigator reader) throws IOException {
		Tag tag = reader.descend();
		while (tag != null) {
			if (tag.matches("div")) {
				if (place instanceof Planet && tag.getClasses().contains("planetHead")) {
					parsePlanetHead((Planet) place, reader);
				} else if (place instanceof Planet && tag.getClasses().contains("items")) {
					parseItems((Planet) place, reader);
				} else if (tag.getClasses().contains("supply")) {
					parseBuildings(place, "supply", reader);
				} else if (tag.getClasses().contains("station")) {
					parseBuildings(place, "station", reader);
				} else if (tag.getClasses().contains("defence")) {
					parseShipyardItems(place, "defense", reader);
				} else if (tag.getClasses().contains("research")) {
					parseResearch(account, reader);
				} else if (tag.getClasses().contains("ships")) {
					parseShipyardItems(place, "ships", reader);
				}
			}
			reader.close(tag);
			tag = reader.descend();
		}
	}

	private static void parsePlanetFromOverview(Account account, Planet planet, HtmlNavigator reader) throws IOException {
		Tag planetTag = reader.getTop();
		Tag classTag = reader.find("a", "planetlink");
		Tag tag;
		if (classTag != null) {
			String title = classTag.getAttributes().get("title");
			title = title.replaceAll("&lt;", "<").replaceAll("&gt;", ">");
			HtmlNavigator titleNav = new HtmlNavigator(new StringReader(title));
			boolean hasFields = false, hasTemp = false;
			while (!titleNav.isDone() && (!hasFields || !hasTemp)) {
				titleNav.descend();
				Matcher m = FIELDS_PATTERN.matcher(titleNav.getLastContent());
				if (m.find()) {
					planet.setBaseFields(Integer.parseInt(m.group("fields")));
					hasFields = true;
				}
				m = TEMP_PATTERN.matcher(titleNav.getLastContent());
				if (m.find()) {
					planet.setMinimumTemperature(Integer.parseInt(m.group("minTemp")))//
						.setMaximumTemperature(Integer.parseInt(m.group("maxTemp")));
					hasTemp = true;
				}
			}
			tag = reader.find("span", "planet-name");
			if (tag != null) {
				reader.close(tag);
				planet.setName(reader.getLastContent());
			}
			tag = reader.find("span", "planet-koords");
			if (tag != null) {
				reader.close(tag);
				int[] coords = tryParseCoords(reader.getLastContent());
				if (coords != null) {
					planet.getCoordinates().set(coords[0], coords[1], coords[2]);
				}
			}
			reader.close(classTag);
		}
		classTag = reader.find("a", "constructionIcon");
		if (classTag != null) {
			switch (classTag.getAttributes().get("title")) {
			case "Metal Mine":
				planet.setCurrentUpgrade(BuildingType.MetalMine);
				break;
			case "Crystal Mine":
				planet.setCurrentUpgrade(BuildingType.CrystalMine);
				break;
			case "Deuterium Synthesizer":
				planet.setCurrentUpgrade(BuildingType.DeuteriumSynthesizer);
				break;
			case "Solar Plant":
				planet.setCurrentUpgrade(BuildingType.SolarPlant);
				break;
			case "Fusion Reactor":
				planet.setCurrentUpgrade(BuildingType.FusionReactor);
				break;
			case "Metal Storage":
				planet.setCurrentUpgrade(BuildingType.MetalStorage);
				break;
			case "Crystal Storage":
				planet.setCurrentUpgrade(BuildingType.CrystalStorage);
				break;
			case "Deuterium Tank":
				planet.setCurrentUpgrade(BuildingType.DeuteriumStorage);
				break;
			case "Robotics Factory":
				planet.setCurrentUpgrade(BuildingType.RoboticsFactory);
				break;
			case "Shipyard":
				planet.setCurrentUpgrade(BuildingType.Shipyard);
				break;
			case "Research Lab":
				planet.setCurrentUpgrade(BuildingType.ResearchLab);
				break;
			case "Alliance Depot":
				planet.setCurrentUpgrade(BuildingType.AllianceDepot);
				break;
			case "Missile Silo":
				planet.setCurrentUpgrade(BuildingType.MissileSilo);
				break;
			case "Nanite Factory":
				planet.setCurrentUpgrade(BuildingType.NaniteFactory);
				break;
			case "Terraformer":
				planet.setCurrentUpgrade(BuildingType.Terraformer);
				break;
			case "Space Dock":
				planet.setCurrentUpgrade(BuildingType.SpaceDock);
				break;
			}
			reader.close(classTag);
		}
		reader.close(planetTag);
	}

	private static String getNextWord(String content, int fromIndex) {
		StringBuilder str = new StringBuilder();
		while (fromIndex < content.length() && Character.isWhitespace(content.charAt(fromIndex))) {
			fromIndex++;
		}

		while (fromIndex < content.length() && Character.isAlphabetic(content.charAt(fromIndex))) {
			str.append(content.charAt(fromIndex));
			fromIndex++;
		}
		return str.toString();
	}

	private static int parseFirstInt(String content) {
		content = content.trim().replaceAll("\\.", "");
		int start, end;
		for (start = 0; start < content.length() && !Character.isDigit(content.charAt(start)); start++) {}
		for (end = start; end < content.length() && Character.isDigit(content.charAt(end)); end++) {}
		if (start != end) {
			return Integer.parseInt(content.substring(start, end));
		} else {
			return -1;
		}
	}

	private static void parsePlanetHead(Planet planet, HtmlNavigator reader) throws IOException {
		Tag tag = reader.descend();
		while (tag != null) {
			if (tag.matches("div")) {
				if (tag.getClasses().contains("planetname")) {
					reader.close(tag);
					planet.setName(reader.getLastContent());
				} else if (tag.getClasses().contains("planetData")) {
					parsePlanetData(planet, reader);
				}
			}
			reader.close(tag);
			tag = reader.descend();
		}
	}

	private static Moon parseMoonHead(Account account, HtmlNavigator reader) throws IOException {
		Moon moon = null;
		String moonName = null;
		Tag tag = reader.descend();
		while (tag != null) {
			if (tag.matches("div")) {
				if (tag.getClasses().contains("planetname")) {
					reader.close(tag);
					moonName = reader.getLastContent();
				} else if (tag.getClasses().contains("planetData")) {
					moon = parseMoonData(account, reader);
					if (moon != null && moonName != null) {
						moon.setName(moonName);
					}
				}
			}
			reader.close(tag);
			tag = reader.descend();
		}
		return moon;
	}

	private static void parsePlanetData(Planet planet, HtmlNavigator reader) throws IOException {
		Tag tag = reader.descend();
		while (tag != null) {
			if (tag.matches("div", "planetDataTop")) {
				if (reader.find("ul") != null) {
					Tag li = reader.find("li");
					while (li != null) {
						if (li.getClasses().contains("coords")) {
							String content = reader.getEmphasizedContent();
							int[] coords = tryParseCoords(content.trim());
							if (coords != null) {
								planet.getCoordinates().set(coords[0], coords[1], coords[2]);
							}
						} else if (li.getClasses().contains("fields")) {
							int fields = parseFields(reader.getEmphasizedContent());
							if (fields > 0) {
								planet.setBaseFields(fields);
							}
						}
						li = reader.find("li");
					}
				}
			} else if (tag.matches("div", "planetDataBottom")) {
				if (reader.find("ul") != null) {
					Tag li = reader.find("li", "fields");
					if (li != null) {
						parseTemperature(planet, reader.getEmphasizedContent());
					}
				}
			}
			reader.close(tag);
			tag = reader.descend();
		}
	}

	private static Moon parseMoonData(Account account, HtmlNavigator reader) throws IOException {
		Tag tag = reader.descend();
		Moon moon = null;
		int fields = -1;
		while (tag != null) {
			if (tag.matches("div", "planetDataTop")) {
				if (reader.find("ul") != null) {
					Tag li = reader.find("li");
					while (li != null) {
						if (li.getClasses().contains("coords")) {
							String content = reader.getEmphasizedContent();
							int[] coords = tryParseCoords(content.trim());
							if (coords != null) {
								for (Planet planet : account.getPlanets().getValues()) {
									if (planet.getCoordinates().getGalaxy() == coords[0]//
										&& planet.getCoordinates().getSystem() == coords[1]//
										&& planet.getCoordinates().getSlot() == coords[2]) {
										moon = planet.getMoon();
										break;
									}
								}
							}
						} else if (li.getClasses().contains("fields")) {
							fields = parseFields(reader.getEmphasizedContent());
						}
						li = reader.find("li");
					}
				}
			}
			reader.close(tag);
			tag = reader.descend();
		}
		if (moon != null && fields > 0) {
			moon.setFieldBonus(fields);
		}
		return moon;
	}

	private static final Pattern COORD_PATTERN = Pattern.compile("\\[(?<galaxy>\\d)\\:(?<system>\\d{1,3})\\:(?<slot>\\d{1,2})\\]");

	private static int[] tryParseCoords(String text) {
		Matcher matcher = COORD_PATTERN.matcher(text);
		if (matcher.find()) {
			return new int[] { Integer.parseInt(matcher.group("galaxy")), //
				Integer.parseInt(matcher.group("system")), Integer.parseInt(matcher.group("slot")) };
		} else {
			return null;
		}
	}

	private static int parseFields(String fieldsText) {
		int slashIdx = fieldsText.indexOf('/');
		if (slashIdx >= 0) {
			return Integer.parseInt(fieldsText.substring(slashIdx + 1).trim());
		} else {
			return -1;
		}
	}

	private static void parseTemperature(Planet planet, String tempText) {
		tempText = tempText.trim();
		int i = 0;
		if (tempText.charAt(0) == '-') {
			i++;
		}
		while (i < tempText.length() && Character.isDigit(tempText.charAt(i))) {
			i++;
		}
		if (i > 0) {
			int minTemp = Integer.parseInt(tempText.substring(0, i));
			planet.setMinimumTemperature(minTemp);
			planet.setMaximumTemperature(minTemp + 40);
		}
	}

	private static void parseItems(Planet planet, HtmlNavigator reader) throws IOException {
		int metalBonus = 0, crystalBonus = 0, deutBonus = 0;
		Tag tag = reader.descend();
		while (tag != null) {
			Tag uncommon = reader.find("div", "r_uncommon");
			while (uncommon != null) {
				Tag itemTag = reader.find("div", "item_img");
				if (itemTag != null) {
					String title = itemTag.getAttributes().get("title");
					if (title != null) {
						int level = 0;
						if (title.contains("Bronze")) {
							level = 10;
						} else if (title.contains("Silver")) {
							level = 20;
						} else {
							level = 30;
						}
						if (title.contains("Metal")) {
							metalBonus = level;
						} else if (title.contains("Crystal")) {
							crystalBonus = level;
						} else {
							deutBonus = level;
						}
					}
				}
				reader.close(uncommon);
				uncommon = reader.find("div", "r_uncommon");
			}
			reader.close(tag);
			tag = reader.descend();
		}
		planet.setMetalBonus(metalBonus).setCrystalBonus(crystalBonus).setDeuteriumBonus(deutBonus);
	}

	private static void parseBuildings(RockyBody place, String buildingClass, HtmlNavigator reader) throws IOException {
		Tag tag = reader.descend();
		while (tag != null) {
			int id;
			if (tag.getName().equals("div") && (id = getIntClass(tag)) >= 0) {
				int level = parseFirstInt(reader.getEmphasizedContent());
				if (level >= 0) {
					setBuilding(place, id, level);
				}
			}
			reader.close(tag);
			tag = reader.descend();
		}
	}

	private static void parseShipyardItems(RockyBody place, String itemClass, HtmlNavigator reader) throws IOException {
		Tag tag = reader.descend();
		while (tag != null) {
			int id;
			if (tag.getName().equals("div") && (id = getIntClass(tag)) >= 0) {
				int level = parseFirstInt(reader.getEmphasizedContent());
				if (level >= 0) {
					setShipyardItem(place, id, level);
				}
			}
			reader.close(tag);
			tag = reader.descend();
		}
	}

	private static void parseResearch(Account account, HtmlNavigator reader) throws IOException {
		Tag tag = reader.descend();
		while (tag != null) {
			int id;
			if (tag.getName().equals("div") && (id = getIntClass(tag)) >= 0) {
				int level = parseFirstInt(reader.getEmphasizedContent());
				if (level >= 0) {
					setResearch(account, id, level);
				}
			}
			reader.close(tag);
			tag = reader.descend();
		}
	}

	private static int getIntClass(Tag tag) {
		for (String clazz : tag.getClasses()) {
			int i;
			for (i = 0; i < clazz.length(); i++) {
				if (!Character.isDigit(clazz.charAt(i))) {
					break;
				}
			}
			if (i == clazz.length()) {
				return Integer.parseInt(clazz);
			}
		}
		return -1;
	}

	private static void setBuilding(RockyBody place, int buildingNumber, int level) {
		BuildingType type = null;

		switch (buildingNumber) {
		case 1:
			type = BuildingType.MetalMine;
			break;
		case 2:
			type = BuildingType.CrystalMine;
			break;
		case 3:
			type = BuildingType.DeuteriumSynthesizer;
			break;
		case 4:
			type = BuildingType.SolarPlant;
			break;
		case 12:
			type = BuildingType.FusionReactor;
			break;
		case 14:
			type = BuildingType.RoboticsFactory;
			break;
		case 15:
			type = BuildingType.NaniteFactory;
			break;
		case 21:
			type = BuildingType.Shipyard;
			break;
		case 22:
			type = BuildingType.MetalStorage;
			break;
		case 23:
			type = BuildingType.CrystalStorage;
			break;
		case 24:
			type = BuildingType.DeuteriumStorage;
			break;
		case 31:
			type = BuildingType.ResearchLab;
			break;
		case 33:
			type = BuildingType.Terraformer;
			break;
		case 34:
			type = BuildingType.AllianceDepot;
			break;
		case 36:
			type = BuildingType.SpaceDock;
			break;
		case 41:
			type = BuildingType.LunarBase;
			break;
		case 42:
			type = BuildingType.SensorPhalanx;
			break;
		case 43:
			type = BuildingType.JumpGate;
			break;
		case 44:
			type = BuildingType.MissileSilo;
			break;
		}
		if (type != null) {
			place.setBuildingLevel(type, level);
		} else {
			System.err.println("Unrecognized building ID: " + buildingNumber);
		}
	}

	private static void setShipyardItem(RockyBody place, int structureId, int count) {
		ShipyardItemType type = null;
		switch (structureId) {
		case 202:
			type = ShipyardItemType.SmallCargo;
			break;
		case 203:
			type = ShipyardItemType.LargeCargo;
			break;
		case 204:
			type = ShipyardItemType.LightFighter;
			break;
		case 205:
			type = ShipyardItemType.HeavyFighter;
			break;
		case 206:
			type = ShipyardItemType.Cruiser;
			break;
		case 207:
			type = ShipyardItemType.BattleShip;
			break;
		case 208:
			type = ShipyardItemType.ColonyShip;
			break;
		case 209:
			type = ShipyardItemType.Recycler;
			break;
		case 210:
			type = ShipyardItemType.EspionageProbe;
			break;
		case 211:
			type = ShipyardItemType.Bomber;
			break;
		case 212:
			type = ShipyardItemType.SolarSatellite;
			break;
		case 213:
			type = ShipyardItemType.Destroyer;
			break;
		case 214:
			type = ShipyardItemType.DeathStar;
			break;
		case 215:
			type = ShipyardItemType.BattleCruiser;
			break;
		case 217:
			type = ShipyardItemType.Crawler;
			break;
		case 218:
			type = ShipyardItemType.Reaper;
			break;
		case 219:
			type = ShipyardItemType.PathFinder;
			break;

		case 401:
			type = ShipyardItemType.RocketLauncher;
			break;
		case 402:
			type = ShipyardItemType.LightLaser;
			break;
		case 403:
			type = ShipyardItemType.HeavyLaser;
			break;
		case 404:
			type = ShipyardItemType.GaussCannon;
			break;
		case 405:
			type = ShipyardItemType.IonCannon;
			break;
		case 406:
			type = ShipyardItemType.PlasmaTurret;
			break;
		case 407:
			type = ShipyardItemType.SmallShield;
			break;
		case 408:
			type = ShipyardItemType.LargeShield;
			break;

		case 502:
			type = ShipyardItemType.AntiBallisticMissile;
			break;
		case 503:
			type = ShipyardItemType.InterPlanetaryMissile;
			break;
		}
		if (type != null) {
			place.setStationedShips(type, count);
		} else {
			System.err.println("Unrecognized shipyard item ID: " + structureId);
		}
	}

	private static void setResearch(Account account, int researchId, int level) {
		ResearchType type = null;
		switch (researchId) {
		case 106:
			type = ResearchType.Espionage;
			break;
		case 108:
			type = ResearchType.Computer;
			break;
		case 109:
			type = ResearchType.Weapons;
			break;
		case 110:
			type = ResearchType.Shielding;
			break;
		case 111:
			type = ResearchType.Armor;
			break;
		case 113:
			type = ResearchType.Energy;
			break;
		case 114:
			type = ResearchType.Hyperspace;
			break;
		case 115:
			type = ResearchType.Combustion;
			break;
		case 117:
			type = ResearchType.Impulse;
			break;
		case 118:
			type = ResearchType.Hyperdrive;
			break;
		case 120:
			type = ResearchType.Laser;
			break;
		case 121:
			type = ResearchType.Ion;
			break;
		case 122:
			type = ResearchType.Plasma;
			break;
		case 123:
			type = ResearchType.IntergalacticResearchNetwork;
			break;
		case 124:
			type = ResearchType.Astrophysics;
			break;
		case 199:
			type = ResearchType.Graviton;
			break;
		}
		if (type != null) {
			account.getResearch().setResearchLevel(type, level);
		} else {
			System.err.println("Unrecognized research ID: " + researchId);
		}
	}

	public static void main(String[] args) {
		try (BufferedReader reader = new BufferedReader(new FileReader(args[0]));
			Writer writer = new BufferedWriter(new FileWriter(args[1]))) {
			int indent = 0;
			String line = reader.readLine();
			while (line != null) {
				int preIndex = 0;
				int tagIdx = line.indexOf('<');
				while (tagIdx >= 0 && tagIdx < line.length() - 1) {
					char nextChar = line.charAt(tagIdx + 1);
					writer.write(line.substring(preIndex, tagIdx));
					preIndex = tagIdx;
					if (nextChar == '/') {
						writer.write('\n');
						indent = Math.max(0, indent - 1);
						indent(writer, indent);
					} else if (Character.isAlphabetic(nextChar)) {
						writer.write('\n');
						indent(writer, indent);
						StringBuilder tagName = new StringBuilder();
						tagName.append(nextChar);
						for (int i = tagIdx + 2; tagIdx < line.length() && Character.isAlphabetic(line.charAt(i)); i++) {
							tagName.append(line.charAt(i));
						}
						if (!HtmlNavigator.UNCLOSED_TAGS.contains(tagName.toString().toLowerCase())) {
							indent++;
						}
					}
					tagIdx = line.indexOf('<', tagIdx + 1);
				}
				writer.write(line.substring(preIndex));
				writer.write('\n');
				line = reader.readLine();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void indent(Writer writer, int indent) throws IOException {
		for (int i = 0; i < indent; i++) {
			writer.append('\t');
		}
	}
}
