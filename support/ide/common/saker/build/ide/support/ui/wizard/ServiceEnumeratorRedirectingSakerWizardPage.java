package saker.build.ide.support.ui.wizard;

import java.util.Optional;

import saker.build.ide.support.SakerIDESupportUtils;
import saker.build.ide.support.properties.BuiltinScriptingLanguageServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.ClassPathServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.NamedClassClassPathServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.NestRepositoryFactoryServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.ServiceLoaderClassPathEnumeratorIDEProperty;

public class ServiceEnumeratorRedirectingSakerWizardPage extends AbstractSakerWizardPage {
	public ServiceEnumeratorRedirectingSakerWizardPage(SakerWizardManager<SakerWizardPage> wizardManager) {
		super(wizardManager);
		ClassPathServiceEnumeratorIDEProperty editproperty = (ClassPathServiceEnumeratorIDEProperty) wizardManager
				.getConfiguration(SakerIDESupportUtils.WIZARD_CONFIGURATION_EDIT_SERVICE);
		if (editproperty != null) {
			editproperty.accept(new ClassPathServiceEnumeratorIDEProperty.Visitor<Void, Void>() {
				@Override
				public Void visit(ServiceLoaderClassPathEnumeratorIDEProperty property, Void param) {
					ClassPathServiceEnumeratorSakerWizardPage next = wizardManager
							.getWizardPage(ClassPathServiceEnumeratorSakerWizardPage.class);
					next.setServiceLoader(property.getServiceClass());
					return null;
				}

				@Override
				public Void visit(NamedClassClassPathServiceEnumeratorIDEProperty property, Void param) {
					ClassPathServiceEnumeratorSakerWizardPage next = wizardManager
							.getWizardPage(ClassPathServiceEnumeratorSakerWizardPage.class);
					next.setNamedClass(property.getClassName());
					return null;
				}

				@Override
				public Void visit(BuiltinScriptingLanguageServiceEnumeratorIDEProperty property, Void param) {
					return null;
				}

				@Override
				public Void visit(NestRepositoryFactoryServiceEnumeratorIDEProperty property, Void param) {
					return null;
				}
			}, null);
		}
	}

	@Override
	public SakerWizardPage redirectPage(WizardPageHistoryLink parent) {
		if (hasServiceEnumeratorParent(parent)) {
			return getNextPage();
		}
		AbstractSakerWizardPage redirected = wizardManager
				.getWizardPage(ClassPathServiceEnumeratorSakerWizardPage.class);
		redirected.setNextPage(getNextPage());
		redirected.configurations.putAll(this.configurations);
		return redirected;
	}

	private static boolean hasServiceEnumeratorParent(WizardPageHistoryLink parent) {
		if (parent == null) {
			return false;
		}
		return Boolean.TRUE.equals(parent.forEach(page -> {
			if (page instanceof ClassPathServiceEnumeratorWizard) {
				if (((ClassPathServiceEnumeratorWizard) page).getServiceEnumerator() != null) {
					return true;
				}
			}
			return null;
		}));
	}

	@Override
	public boolean canFinishWizard(WizardPageHistoryLink parent) {
		SakerWizardPage redirected = redirectPage(parent);
		if (redirected != null) {
			return redirected.canFinishWizard(parent);
		}
		SakerWizardPage np = getNextPage();
		return np == null || np.canFinishWizard(parent);
	}

	@Override
	public Optional<?> finishWizard(WizardPageHistoryLink parent) {
		SakerWizardPage redirected = redirectPage(parent);
		if (redirected != null) {
			return redirected.finishWizard(parent);
		}
		SakerWizardPage np = getNextPage();
		if (np != null) {
			return np.finishWizard(parent);
		}
		return Optional.empty();
	}
}
