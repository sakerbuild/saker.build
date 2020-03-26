package saker.build.ide.support.ui.wizard;

import java.util.Optional;

import saker.build.ide.support.properties.BuiltinScriptingLanguageServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.ClassPathServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.NamedClassClassPathServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.NestRepositoryFactoryServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.ServiceLoaderClassPathEnumeratorIDEProperty;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class ClassPathServiceEnumeratorSakerWizardPage extends AbstractSakerWizardPage
		implements ClassPathServiceEnumeratorWizard {

	private ClassPathServiceEnumeratorIDEProperty property;

	public ClassPathServiceEnumeratorSakerWizardPage(SakerWizardManager<SakerWizardPage> wizardManager) {
		super(wizardManager);
	}

	public void setNamedClass(String classname) {
		property = new NamedClassClassPathServiceEnumeratorIDEProperty(classname);
	}

	public void setServiceLoader(String serviceclassname) {
		property = new ServiceLoaderClassPathEnumeratorIDEProperty(serviceclassname);
	}

	public void unselect() {
		property = null;
	}

	public ClassPathServiceEnumeratorIDEProperty getProperty() {
		return property;
	}

	@Override
	public boolean canFinishWizard(WizardPageHistoryLink parent) {
		if (property == null || !isPropertyFinishable(property)) {
			return false;
		}
		SakerWizardPage np = super.getNextPage();
		return np == null || np.canFinishWizard(WizardPageHistoryLink.next(parent, this));
	}

	private static boolean isPropertyFinishable(ClassPathServiceEnumeratorIDEProperty property) {
		return property.accept(new ClassPathServiceEnumeratorIDEProperty.Visitor<Boolean, Void>() {
			@Override
			public Boolean visit(ServiceLoaderClassPathEnumeratorIDEProperty property, Void param) {
				return !ObjectUtils.isNullOrEmpty(property.getServiceClass());
			}

			@Override
			public Boolean visit(NamedClassClassPathServiceEnumeratorIDEProperty property, Void param) {
				return !ObjectUtils.isNullOrEmpty(property.getClassName());
			}

			@Override
			public Boolean visit(BuiltinScriptingLanguageServiceEnumeratorIDEProperty property, Void param) {
				return true;
			}

			@Override
			public Boolean visit(NestRepositoryFactoryServiceEnumeratorIDEProperty property, Void param) {
				return true;
			}
		}, null);
	}

	@Override
	public Optional<?> finishWizard(WizardPageHistoryLink parent) {
		SakerWizardPage np = super.getNextPage();
		if (np == null) {
			return Optional.ofNullable(getServiceEnumerator());
		}
		return np.finishWizard(WizardPageHistoryLink.next(parent, this));
	}

	@Override
	public ClassPathServiceEnumeratorIDEProperty getServiceEnumerator() {
		return property;
	}

}
