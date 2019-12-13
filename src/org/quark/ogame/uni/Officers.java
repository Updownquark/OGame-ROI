package org.quark.ogame.uni;

public interface Officers {
	boolean isCommander();
	void setCommander(boolean commander);

	boolean isCommandingStaff();
	void setCommandingStaff(boolean commandingStaff);

	boolean isAdmiral();
	void setAdmiral(boolean admiral);

	boolean isEngineer();
	void setEngineer(boolean engineer);

	boolean isGeologist();
	void setGeologist(boolean geologist);

	boolean isTechnocrat();
	void setTechnocrat(boolean technocrat);
}
