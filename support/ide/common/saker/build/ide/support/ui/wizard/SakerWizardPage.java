package saker.build.ide.support.ui.wizard;

import java.util.Optional;

public interface SakerWizardPage {
	public SakerWizardPage getNextPage();

	public default Optional<?> finishWizard(WizardPageHistoryLink parent) {
		return null;
	}

	public default boolean canFinishWizard(WizardPageHistoryLink parent) {
		return false;
	}

	/**
	 * Gets a page to which the wizard should be redirected instead of this page.
	 * <p>
	 * If this method returns non-<code>null</code>, the wizard manager will display that page in place of this and this
	 * page will be effectively <i>discarded</i>.
	 */
	public default SakerWizardPage redirectPage(WizardPageHistoryLink parent) {
		return null;
	}

	public default Object getPageConfiguration(Object key) {
		return null;
	}
}
