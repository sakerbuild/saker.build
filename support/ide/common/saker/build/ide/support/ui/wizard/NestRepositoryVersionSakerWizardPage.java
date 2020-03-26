package saker.build.ide.support.ui.wizard;

import java.util.Optional;

import saker.build.ide.support.properties.ClassPathLocationIDEProperty;
import saker.build.ide.support.properties.NestRepositoryClassPathLocationIDEProperty;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class NestRepositoryVersionSakerWizardPage extends AbstractSakerWizardPage implements ClassPathLocationWizard {
	private Optional<String> version;

	public NestRepositoryVersionSakerWizardPage(SakerWizardManager<SakerWizardPage> wizardManager) {
		super(wizardManager);
	}

	public void setVersion(String version) {
		this.version = Optional.ofNullable(version);
	}

	public String getVersion() {
		return ObjectUtils.getOptional(version);
	}

	@Override
	public ClassPathLocationIDEProperty getClassPathLocation() {
		if (version == null) {
			return null;
		}
		return new NestRepositoryClassPathLocationIDEProperty(getVersion());
	}

	@Override
	public boolean canFinishWizard(WizardPageHistoryLink parent) {
		if (version == null) {
			return false;
		}
		SakerWizardPage np = super.getNextPage();
		return np == null || np.canFinishWizard(WizardPageHistoryLink.next(parent, this));
	}

	@Override
	public Optional<?> finishWizard(WizardPageHistoryLink parent) {
		SakerWizardPage np = super.getNextPage();
		if (np == null) {
			return Optional.ofNullable(getClassPathLocation());
		}
		return np.finishWizard(WizardPageHistoryLink.next(parent, this));
	}
}
