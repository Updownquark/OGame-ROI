package org.quark.ogame.uni;

public interface OGameRuleSet {
	long getProduction(Planet planet, ResourceType resourceType);
	long getStorage(Planet planet, ResourceType resourceType);

	int getFields(Planet planet);
	int getFields(Moon moon);
}
