package saker.build.ide.support.ui.wizard;

import java.util.function.Function;

public class WizardPageHistoryLink {
	private final WizardPageHistoryLink parent;
	private final SakerWizardPage page;

	public WizardPageHistoryLink(WizardPageHistoryLink parent, SakerWizardPage page) {
		this.parent = parent;
		this.page = page;
	}

	public WizardPageHistoryLink(SakerWizardPage page) {
		this.parent = null;
		this.page = page;
	}

	public WizardPageHistoryLink getParent() {
		return parent;
	}

	public SakerWizardPage getPage() {
		return page;
	}

	public static WizardPageHistoryLink next(WizardPageHistoryLink parent, SakerWizardPage page) {
		return new WizardPageHistoryLink(parent, page);
	}

	public <E> E forEach(Function<? super SakerWizardPage, ? extends E> consumer) {
		WizardPageHistoryLink link = this;
		do {
			E res = consumer.apply(link.page);
			if (res != null) {
				return res;
			}
			link = link.parent;
		} while (link != null);
		return null;
	}

	@Override
	public String toString() {
		return "WizardPageHistoryLink[" + page + "]";
	}
}
