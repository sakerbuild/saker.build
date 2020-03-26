package saker.build.ide.support.ui.wizard;

import java.util.Optional;

import saker.build.ide.support.properties.ClassPathLocationIDEProperty;
import saker.build.ide.support.properties.JarClassPathLocationIDEProperty;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class ClassPathFileChooserSakerWizardPage extends AbstractSakerWizardPage implements ClassPathLocationWizard {
	private String connectionName;
	private String jarPath;

	public ClassPathFileChooserSakerWizardPage(SakerWizardManager<SakerWizardPage> wizardManager) {
		super(wizardManager);
	}

	public void setFile(String connectionName, String jarPath) {
		this.connectionName = connectionName;
		this.jarPath = jarPath;
	}

	public String getConnectionName() {
		return connectionName;
	}

	public String getJarPath() {
		return jarPath;
	}

	@Override
	public ClassPathLocationIDEProperty getClassPathLocation() {
		return new JarClassPathLocationIDEProperty(connectionName, jarPath);
	}

	@Override
	public boolean canFinishWizard(WizardPageHistoryLink parent) {
		if (ObjectUtils.isNullOrEmpty(connectionName) || ObjectUtils.isNullOrEmpty(jarPath)) {
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
