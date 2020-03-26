package saker.build.ide.support.ui.wizard;

public interface SakerWizardManager<P extends SakerWizardPage> {
	public <T extends P> T getWizardPage(Class<T> pageclass);

	public default Object getConfiguration(Object key) {
		return null;
	}

	public default void setConfiguration(Object key, Object value) {
		//no-op as default
	}
}
