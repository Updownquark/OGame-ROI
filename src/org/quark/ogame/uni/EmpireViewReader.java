package org.quark.ogame.uni;

import java.io.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.qommons.io.HtmlNavigator;
import org.qommons.io.HtmlNavigator.Tag;

public class EmpireViewReader {
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
			if (tag == null)
				continue;
			Moon moon = parseMoonHead(account, nav);
			if (moon != null) {
				parsePlanet(account, moon, nav);
				int totalFields = moon.getFieldBonus();
				moon.setFieldBonus(0);
				moon.setFieldBonus(Math.min(0, totalFields - rules.economy().getFields(moon)));
			}
		}
	}

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
			type = ShipyardItemType.LargeSheild;
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
		Set<String> noContentTags = new HashSet<>(Arrays.asList("img", "meta"));
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
						if (!noContentTags.contains(tagName.toString().toLowerCase())) {
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
