package saker.build.ide.support.ui.wizard;

import java.util.Optional;

import saker.build.ide.support.properties.ClassPathLocationIDEProperty;
import saker.build.ide.support.properties.HttpUrlJarClassPathLocationIDEProperty;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class ClassPathNetworkArchiveSakerWizardPage extends AbstractSakerWizardPage implements ClassPathLocationWizard {

	private String url;

	public ClassPathNetworkArchiveSakerWizardPage(SakerWizardManager<SakerWizardPage> wizardManager) {
		super(wizardManager);
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getUrl() {
		return url;
	}

	@Override
	public ClassPathLocationIDEProperty getClassPathLocation() {
		return new HttpUrlJarClassPathLocationIDEProperty(url);
	}

	@Override
	public boolean canFinishWizard(WizardPageHistoryLink parent) {
		if (ObjectUtils.isNullOrEmpty(url)) {
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
