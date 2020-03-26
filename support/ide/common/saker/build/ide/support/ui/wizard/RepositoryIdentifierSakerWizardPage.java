package saker.build.ide.support.ui.wizard;

import java.util.Optional;

import saker.build.ide.support.SakerIDESupportUtils;
import saker.build.ide.support.properties.ClassPathLocationIDEProperty;
import saker.build.ide.support.properties.ClassPathServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.RepositoryIDEProperty;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class RepositoryIdentifierSakerWizardPage extends AbstractSakerWizardPage {
	private Optional<String> identifier;

	public RepositoryIdentifierSakerWizardPage(SakerWizardManager<SakerWizardPage> wizardManager) {
		super(wizardManager);
		String repoid = (String) wizardManager
				.getConfiguration(SakerIDESupportUtils.WIZARD_CONFIGURATION_EDIT_REPOSITORYID);
		if (repoid != null) {
			setIdentifier(repoid);
		}
	}

	public void setIdentifier(String identifier) {
		this.identifier = Optional.ofNullable(identifier);
	}

	public String getIdentifier() {
		return ObjectUtils.getOptional(identifier);
	}

	@Override
	public boolean canFinishWizard(WizardPageHistoryLink parent) {
		if (identifier == null) {
			return false;
		}
		SakerWizardPage np = super.getNextPage();
		return np == null || np.canFinishWizard(WizardPageHistoryLink.next(parent, this));
	}

	@Override
	public Optional<?> finishWizard(WizardPageHistoryLink parent) {
		SakerWizardPage np = super.getNextPage();
		if (np == null) {
			return Optional.ofNullable(getRepositoryProperty(parent));
		}
		return np.finishWizard(WizardPageHistoryLink.next(parent, this));
	}

	public RepositoryIDEProperty getRepositoryProperty(WizardPageHistoryLink parent) {
		if (parent == null) {
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
		return new RepositoryIDEProperty(classPathLocation, ObjectUtils.getOptional(identifier), serviceEnumerator);
	}
}
