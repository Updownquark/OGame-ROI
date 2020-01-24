package org.quark.ogame.uni;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class EmpireViewReader {
	public static int readEmpireView(Account account, Reader reader, Supplier<Planet> createPlanet) throws IOException {
		int planetIdx = 0;
		while (find("div", "planet", reader)) {
			if (planetIdx == account.getPlanets().getValues().size()) {
				createPlanet.get();
			}
			parsePlanet(account, //
				account.getPlanets().getValues().get(planetIdx), reader);
			planetIdx++;
		}
		return planetIdx;
	}

	public static void readEmpireMoonView(Account account, Reader reader) throws IOException {
		int planetIdx = 0;
		while (planetIdx < account.getPlanets().getValues().size() && find("div", "planet", reader)) {
			parsePlanet(account, //
				account.getPlanets().getValues().get(planetIdx).getMoon(), reader);
			planetIdx++;
		}
	}

	private static void parsePlanet(Account account, RockyBody place, Reader reader) throws IOException {
		Tag tag = getNextTag(reader, null);
		while (tag != null) {
			if (tag.name.equals("div")) {
				if (tag.classes.contains("planethead")) {
					parsePlanetHead(place, reader);
				} else if (place instanceof Planet && tag.classes.contains("items")) {
					parseItems((Planet) place, reader);
				} else if (tag.classes.contains("supply")) {
					parseBuildings(place, "supply", reader);
				} else if (tag.classes.contains("station")) {
					parseBuildings(place, "station", reader);
				} else if (tag.classes.contains("defence")) {
					parseShipyardItems(place, "defense", reader);
				} else if (tag.classes.contains("research")) {
					parseResearch(account, reader);
				} else if (tag.classes.contains("ships")) {
					parseShipyardItems(place, "ships", reader);
				} else {
					findEndTag(reader);
				}
			} else {
				findEndTag(reader);
			}
			tag = getNextTag(reader, null);
		}
	}

	enum ContentType {
		tag, clazz, attr;
	}

	private static boolean find(String tagName, String className, Reader reader) throws IOException {
		StringBuilder tagNameTemp = new StringBuilder();
		String[] attrName = new String[1];
		int r = reader.read();
		boolean found = false;
		while (r >= 0) {
			if (!found && r == '<') {
				r = reader.read();
				if (r != '/') {
					while (r >= 0 && Character.isAlphabetic(r)) {
						tagNameTemp.append((char) r);
						r = reader.read();
					}
					if (tagNameTemp.toString().toLowerCase().equals(tagName)) {
						String clazz = getAttribute(reader, attrName);
						while (clazz != null && !attrName[0].toLowerCase().equals("class")) {
							clazz = getAttribute(reader, attrName);
						}
						if (clazz != null) {
							String[] clazzSplit = clazz.split(" ");
							for (String c : clazzSplit) {
								if (c.toLowerCase().equals(className)) {
									found = true;
									break;
								}
							}
						}
					}
					tagNameTemp.setLength(0);
				}
			} else if (found && r == '>') {
				break;
			}
			r = reader.read();
		}
		return found;
	}

	private static Tag getNextTag(Reader reader, StringBuilder currentTagContent) throws IOException {
		StringBuilder tagNameTemp = new StringBuilder();
		String[] attrName = new String[1];
		int r = reader.read();
		while (r >= 0) {
			if (r == '<') {
				r = reader.read();
				if (r == '/') {
					while (r != '>') {
						r=reader.read();
					}
					return null;
				}
				while (r >= 0 && Character.isAlphabetic(r)) {
					tagNameTemp.append((char) r);
					r = reader.read();
				}
				Map<String, String> attributes = null;
				Set<String> classes = null;
				if (r != '>') {
					String attr = getAttribute(reader, attrName);
					while (attr != null) {
						if (attrName[0].toLowerCase().equals("class")) {
							classes = new HashSet<>();
							for (String c : attr.split(" ")) {
								if (c.length() > 0) {
									classes.add(c.toLowerCase());
								}
							}
						} else {
							if (attributes == null) {
								attributes = new LinkedHashMap<>();
							}
							attributes.put(attrName[0].toLowerCase(), attr);
						}
						attr = getAttribute(reader, attrName);
					}
				}
				return new Tag(tagNameTemp.toString(), //
					classes == null ? Collections.emptySet() : classes, //
					attributes == null ? Collections.emptyMap() : attributes);
			} else if (currentTagContent != null) {
				currentTagContent.append((char) r);
			}
			r = reader.read();
		}
		return null;
	}

	private static String getAttribute(Reader reader, String[] attrName) throws IOException {
		int r = reader.read();
		while (r >= 0 && r != '>' && !Character.isAlphabetic(r)) {
			r = reader.read();
		}
		if (Character.isAlphabetic(r)) {
			StringBuilder str = new StringBuilder();
			do {
				str.append((char) r);
				r = reader.read();
			} while (r >= 0 && Character.isAlphabetic(r));
			while (Character.isWhitespace((char) r)) {
				r = reader.read();
			}
			if (r != '=') {
				return null;
			}
			r = reader.read();
			while (Character.isWhitespace((char) r)) {
				r = reader.read();
			}
			if (r != '"') {
				return null;
			}
			r = reader.read();
			attrName[0] = str.toString();
			str.setLength(0);
			while (r >= 0 && r != '"') {
				str.append((char) r);
				r = reader.read();
			}
			return str.toString();
		} else {
			return null;
		}
	}

	private static Set<String> NON_CLOSING_TAGS = new HashSet<>(Arrays.asList("img"));

	private static void findEndTag(Reader reader) throws IOException {
		StringBuilder tagName = new StringBuilder();
		int depth = 0;
		int r = reader.read();
		while (r >= 0) {
			if (r == '<') {
				if ((r = reader.read()) == '/') {
					while (r != '>') {
						r = reader.read();
					}
					if (depth == 0) {
						return;
					} else {
						depth--;
					}
				} else { // New tag
					while (Character.isAlphabetic(r)) {
						tagName.append((char) r);
						r = reader.read();
					}
					if (!NON_CLOSING_TAGS.contains(tagName.toString().toLowerCase())) {
						depth++;
					}
					tagName.setLength(0);
					while (r >= 0 && r != '>') {
						r = reader.read();
					}
				}
			}
			r = reader.read();
		}
	}

	private static String getTagContent(Reader reader) throws IOException {
		StringBuilder content = new StringBuilder();
		int r = reader.read();
		while (r >= 0 && r != '<') {
			content.append((char) r);
			r = reader.read();
		}
		while (r >= 0 && r != '>') {
			r = reader.read();
		}
		return content.toString();
	}

	private static String getEmphasizedContent(Reader reader) throws IOException {
		StringBuilder content = new StringBuilder();
		Tag tag = getNextTag(reader, content);
		int depth = 0;
		while (tag != null) {
			if (!NON_CLOSING_TAGS.contains(tag.name)) {
				depth++;
			}
			content.setLength(0);
			tag = getNextTag(reader, content);
		}
		while (depth > 0) {
			findEndTag(reader);
			depth--;
		}
		return content.toString();
	}

	private static int parseFirstInt(String content) {
		int start, end;
		for (start = 0; start < content.length() && !Character.isDigit(content.charAt(start)); start++) {}
		for (end = start; end < content.length() && Character.isDigit(content.charAt(end)); end++) {}
		if (start != end) {
			return Integer.parseInt(content.substring(start, end));
		} else {
			return -1;
		}
	}

	private static void parsePlanetHead(RockyBody place, Reader reader) throws IOException {
		Tag tag = getNextTag(reader, null);
		while (tag != null) {
			if (tag.name.equals("div")) {
				if (tag.classes.contains("planetname")) {
					place.setName(getTagContent(reader).trim());
				} else if (place instanceof Planet && tag.classes.contains("planetdata")) {
					parsePlanetData((Planet) place, reader);
				} else {
					findEndTag(reader);
				}
			} else {
				findEndTag(reader);
			}
			tag = getNextTag(reader, null);
		}
	}

	private static void parsePlanetData(Planet planet, Reader reader) throws IOException {
		Tag tag = getNextTag(reader, null);
		while (tag != null) {
			if (tag.name.equals("div")) {
				if (tag.classes.contains("planetdatatop")) {
					tag = getNextTag(reader, null);
					if (tag == null) {//
					} else if (tag.name.equals("ul")) {
						tag = getNextTag(reader, null);
						while (tag != null) {
							if (tag.name.equals("li") && tag.classes.contains("fields")) {
								parseFields(planet, getTagContent(reader));
							} else {
								findEndTag(reader);
							}
							tag = getNextTag(reader, null);
						}
						findEndTag(reader);
					} else {
						findEndTag(reader);
					}
				} else if (tag.classes.contains("planetdatabottom")) {
					tag = getNextTag(reader, null);
					if (tag == null) {//
					} else if (tag.name.equals("ul")) {
						tag = getNextTag(reader, null);
						while (tag != null) {
							if (tag.name.equals("li") && tag.classes.contains("fields")) {
								parseTemperature(planet, getTagContent(reader));
							} else {
								findEndTag(reader);
							}
							tag = getNextTag(reader, null);
						}
						findEndTag(reader);
					} else {
						findEndTag(reader);
					}
				} else {
					findEndTag(reader);
				}
			} else {
				findEndTag(reader);
			}
			tag = getNextTag(reader, null);
		}
	}

	private static void parseFields(Planet planet, String fieldsText) {
		int slashIdx = fieldsText.indexOf('/');
		if (slashIdx >= 0) {
			planet.setBaseFields(Integer.parseInt(fieldsText.substring(slashIdx + 1).trim()));
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

	private static void parseItems(Planet planet, Reader reader) throws IOException {
		int metalBonus = 0, crystalBonus = 0, deutBonus = 0;
		Tag tag = getNextTag(reader, null);
		while (tag != null) {
			int depth = 0;
			while (tag != null && !tag.classes.contains("item_img")) {
				depth++;
				tag = getNextTag(reader, null);
			}
			if (tag != null) {
				String title = tag.attributes.get("title");
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
			depth--;
			while (depth > 0) {
				findEndTag(reader);
				depth--;
			}
			tag = getNextTag(reader, null);
		}
		planet.setMetalBonus(metalBonus).setCrystalBonus(crystalBonus).setDeuteriumBonus(deutBonus);
	}

	private static void parseBuildings(RockyBody place, String buildingClass, Reader reader) throws IOException {
		Tag tag = getNextTag(reader, null);
		while (tag != null) {
			if (tag.name.equals("div")) {
				int buildingNumber = tag.getIntClass();
				String content = getEmphasizedContent(reader);
				int level = parseFirstInt(content);
				if (level >= 0) {
					setBuilding(place, buildingNumber, level);
				}
			}
			tag = getNextTag(reader, null);
		}
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

	private static void parseShipyardItems(RockyBody place, String itemClass, Reader reader) throws IOException {
		Tag tag = getNextTag(reader, null);
		while (tag != null) {
			int defNumber = tag.getIntClass();
			String content = getEmphasizedContent(reader);
			int level = parseFirstInt(content);
			setShipyardItem(place, defNumber, level);
			tag = getNextTag(reader, null);
		}
	}

	private static void parseResearch(Account account, Reader reader) throws IOException {
		Tag tag = getNextTag(reader, null);
		while (tag != null) {
			if (tag.name.equals("div")) {
				int researchId = tag.getIntClass();
				String content = getEmphasizedContent(reader);
				int level = parseFirstInt(content);
				setResearch(account, researchId, level);
			}
			tag = getNextTag(reader, null);
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

	private static class Tag {
		final String name;
		final Set<String> classes;
		final Map<String, String> attributes;

		Tag(String name, Set<String> classes, Map<String, String> attributes) {
			this.name = name;
			this.classes = classes;
			this.attributes = attributes;
		}

		int getIntClass() {
			int number = -1;
			for (String clazz : classes) {
				if (Character.isDigit(clazz.charAt(0))) {
					number = Integer.parseInt(clazz);
				}
			}
			return number;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append('<').append(name);
			if (!classes.isEmpty()) {
				str.append(" class=\"");
				boolean first = true;
				for (String c : classes) {
					if (first) {
						first = false;
					} else {
						str.append(' ');
					}
					str.append(c);
				}
				str.append('"');
			}
			if (!attributes.isEmpty()) {
				for (Map.Entry<String, String> attr : attributes.entrySet()) {
					str.append(' ').append(attr.getKey()).append("=\"").append(attr.getValue()).append('"');
				}
			}
			str.append('>');
			return str.toString();
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
