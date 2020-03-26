package saker.build.ide.support.ui.wizard;

import java.util.HashMap;
import java.util.Map;

public class AbstractSakerWizardPage implements SakerWizardPage {
	protected final SakerWizardManager<SakerWizardPage> wizardManager;
	protected SakerWizardPage nextPage;

	protected Map<Object, Object> configurations = new HashMap<>();

	public AbstractSakerWizardPage(SakerWizardManager<SakerWizardPage> wizardManager) {
		this.wizardManager = wizardManager;
	}

	@Override
	public SakerWizardPage getNextPage() {
		return nextPage;
	}

	public void setNextPage(SakerWizardPage nextPage) {
		this.nextPage = nextPage;
	}

	@Override
	public Object getPageConfiguration(Object key) {
		return configurations.get(key);
	}

	public void setPageConfiguration(Object key, Object value) {
		configurations.put(key, value);
	}
}
