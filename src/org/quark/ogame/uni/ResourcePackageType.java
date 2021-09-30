package org.quark.ogame.uni;

public enum ResourcePackageType {
	Metal(ResourceType.Metal), Crystal(ResourceType.Crystal), Deuterium(ResourceType.Deuterium), Complete(null);

	public final ResourceType resource;

	private ResourcePackageType(ResourceType resource) {
		this.resource = resource;
	}
}
