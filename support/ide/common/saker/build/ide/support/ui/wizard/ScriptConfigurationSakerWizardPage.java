package saker.build.ide.support.ui.wizard;

import java.util.Map.Entry;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import saker.build.ide.support.SakerIDEPlugin;
import saker.build.ide.support.SakerIDESupportUtils;
import saker.build.ide.support.properties.ClassPathLocationIDEProperty;
import saker.build.ide.support.properties.ClassPathServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.ScriptConfigurationIDEProperty;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class ScriptConfigurationSakerWizardPage extends AbstractSakerWizardPage {
	private String scriptsWildcard;
	private Set<? extends Entry<String, String>> scriptOptions;

	public ScriptConfigurationSakerWizardPage(SakerWizardManager<SakerWizardPage> wizardManager) {
		super(wizardManager);
		ScriptConfigurationIDEProperty editproperty = (ScriptConfigurationIDEProperty) wizardManager
				.getConfiguration(SakerIDESupportUtils.WIZARD_CONFIGURATION_EDIT_SCRIPTCONFIG);
		if (editproperty != null) {
			setData(editproperty);
		}
	}

	public void setData(ScriptConfigurationIDEProperty editproperty) {
		if (editproperty == null) {
			setData(null, null);
		} else {
			setData(editproperty.getScriptsWildcard(), editproperty.getScriptOptions());
		}
	}

	public void setData(String scriptsWildcard, Set<? extends Entry<String, String>> scriptoptions) {
		this.scriptsWildcard = scriptsWildcard;
		this.scriptOptions = scriptoptions == null ? Collections.emptySet()
				: SakerIDEPlugin.makeImmutableEntrySet(scriptoptions);
	}

	public String getScriptsWildcard() {
		return scriptsWildcard;
	}

	public Set<? extends Entry<String, String>> getScriptOptions() {
		return scriptOptions;
	}

	public ScriptConfigurationIDEProperty getScriptConfigurationIDEProperty(WizardPageHistoryLink parent) {
		if (parent == null) {
			return null;
		}
		if (ObjectUtils.isNullOrEmpty(scriptsWildcard)) {
			return null;
		}
		ClassPathLocationIDEProperty classPathLocation = parent.forEach(page -> {
			if (page instanceof ClassPathLocationWizard) {
				ClassPathLocationIDEProperty cp = ((ClassPathLocationWizard) page).getClassPathLocation();
				if (cp != null) {
					return cp;
				}
			}
			return null;
		});
		if (classPathLocation == null) {
			return null;
		}
		ClassPathServiceEnumeratorIDEProperty serviceEnumerator = parent.forEach(page -> {
			if (page instanceof ClassPathServiceEnumeratorWizard) {
				ClassPathServiceEnumeratorIDEProperty service = ((ClassPathServiceEnumeratorWizard) page)
						.getServiceEnumerator();
				if (service != null) {
					return service;
				}
			}
			return null;
		});
		if (serviceEnumerator == null) {
			return null;
		}
		return new ScriptConfigurationIDEProperty(scriptsWildcard, scriptOptions, classPathLocation, serviceEnumerator);
	}

	@Override
	public boolean canFinishWizard(WizardPageHistoryLink parent) {
		if (ObjectUtils.isNullOrEmpty(scriptsWildcard)) {
			return false;
		}
		SakerWizardPage np = super.getNextPage();
		return np == null || np.canFinishWizard(WizardPageHistoryLink.next(parent, this));
	}

	@Override
	public Optional<?> finishWizard(WizardPageHistoryLink parent) {
		SakerWizardPage np = super.getNextPage();
		if (np == null) {
			return Optional.ofNullable(getScriptConfigurationIDEProperty(parent));
		}
		return np.finishWizard(WizardPageHistoryLink.next(parent, this));
	}
}
