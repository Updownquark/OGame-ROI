package org.quark.ogame.uni.ui;

import org.observe.SettableValue;
import org.observe.config.ObservableConfig;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.quark.ogame.uni.Account;
import org.quark.ogame.uni.OGameRuleSet;

public class ConstructionPanel {
	private final ObservableConfig theConfig;
	private final SettableValue<OGameRuleSet> theSelectedRuleSet;
	private final SettableValue<Account> theSelectedAccount;

	public ConstructionPanel(ObservableConfig config, SettableValue<OGameRuleSet> selectedRuleSet, SettableValue<Account> selectedAccount) {
		theConfig = config;
		theSelectedRuleSet = selectedRuleSet;
		theSelectedAccount = selectedAccount;
	}

	public void addPanel(PanelPopulator<?, ?> constructionPanel) {

		// TODO Auto-generated method stub
	}

}
