package org.quark.ogame.uni.ui;

import org.qommons.TimeUtils;
import org.qommons.TimeUtils.DurationComponentType;

enum ProductionDisplayType {
	None(null),
	Hourly(TimeUtils.DurationComponentType.Hour),
	Daily(TimeUtils.DurationComponentType.Day),
	Weekly(TimeUtils.DurationComponentType.Week),
	Monthly(TimeUtils.DurationComponentType.Month),
	Yearly(TimeUtils.DurationComponentType.Year);

	public final TimeUtils.DurationComponentType type;

	private ProductionDisplayType(DurationComponentType type) {
		this.type = type;
	}
}