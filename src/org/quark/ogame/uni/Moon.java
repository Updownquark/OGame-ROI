package org.quark.ogame.uni;

public interface Moon extends RockyBody {
	// Facilities
	int getLunarBase();
	void setLunaryBase(int lunarBase);

	int getSensorPhalanx();
	void setSensorPhalanx(int sensorPhalanx);

	int getJumpGate();
	void setJumpGate(int jumpGate);

	@Override
	default int getTotalFields() {
		return 1 + getLunarBase() * 3;
	}

	@Override
	default int getUsedFields() {
		return RockyBody.super.getUsedFields()//
			+ getLunarBase() + getSensorPhalanx() + getJumpGate();
	}
}
