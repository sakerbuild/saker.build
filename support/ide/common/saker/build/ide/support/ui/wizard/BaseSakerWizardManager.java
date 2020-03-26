package saker.build.ide.support.ui.wizard;

import java.util.HashMap;
import java.util.Map;

public class BaseSakerWizardManager<P extends SakerWizardPage> implements SakerWizardManager<P> {
	private Map<Class<? extends P>, P> pages = new HashMap<>();
	private Map<Object, Object> configurations = new HashMap<>();

	@Override
	public Object getConfiguration(Object key) {
		return configurations.get(key);
	}

	@Override
	public void setConfiguration(Object key, Object value) {
		configurations.put(key, value);
	}

	@Override
	public <T extends P> T getWizardPage(Class<T> pageclass) {
		P present = pages.get(pageclass);
		if (present != null) {
			return pageclass.cast(present);
		}
		try {
			T result = pageclass.getConstructor(SakerWizardManager.class).newInstance(this);
			pages.put(pageclass, result);
			return result;
		} catch (NoSuchMethodException e) {
			try {
				T result = pageclass.getConstructor().newInstance();
				pages.put(pageclass, result);
				return result;
			} catch (Exception e2) {
				e2.addSuppressed(e);
				throw new UnsupportedOperationException("Failed to instantiate wizard page: " + pageclass, e2);
			}
		} catch (Exception e) {
			throw new UnsupportedOperationException("Failed to instantiate wizard page: " + pageclass, e);
		}
	}
}
