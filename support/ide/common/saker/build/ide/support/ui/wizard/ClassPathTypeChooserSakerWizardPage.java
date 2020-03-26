package saker.build.ide.support.ui.wizard;

import java.util.Optional;

import saker.build.ide.support.SakerIDESupportUtils;
import saker.build.ide.support.properties.BuiltinScriptingLanguageClassPathLocationIDEProperty;
import saker.build.ide.support.properties.BuiltinScriptingLanguageServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.ClassPathLocationIDEProperty;
import saker.build.ide.support.properties.ClassPathServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.HttpUrlJarClassPathLocationIDEProperty;
import saker.build.ide.support.properties.JarClassPathLocationIDEProperty;
import saker.build.ide.support.properties.NestRepositoryClassPathLocationIDEProperty;
import saker.build.ide.support.properties.NestRepositoryFactoryServiceEnumeratorIDEProperty;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class ClassPathTypeChooserSakerWizardPage extends AbstractSakerWizardPage
		implements ClassPathLocationWizard, ClassPathServiceEnumeratorWizard {

	public static final int SELECTED_NONE = 0;
	public static final int SELECTED_JAVA_ARCHIVE = 1;
	public static final int SELECTED_NETWORK_ARCHIVE = 2;
	public static final int SELECTED_NEST_REPOSITORY = 3;
	public static final int SELECTED_SAKERSCRIPT = 4;

	public static final String LABEL_JAVA_ARCHIVE = "Java Archive";
	public static final String LABEL_NETWORK_ARCHIVE_HTTP = "Network archive (HTTP)";
	public static final String LABEL_NEST_REPOSITORY_CLASS_PATH = "Nest repository class path";
	public static final String LABEL_SAKER_SCRIPT_CLASS_PATH = "SakerScript class path";

	private Optional<AbstractSakerWizardPage> continuation;

	private ClassPathLocationIDEProperty property;
	private ClassPathServiceEnumeratorIDEProperty serviceEnumerator;

	private int selected = SELECTED_NONE;

	public ClassPathTypeChooserSakerWizardPage(SakerWizardManager<SakerWizardPage> wizardManager) {
		super(wizardManager);
		ClassPathLocationIDEProperty editcp = (ClassPathLocationIDEProperty) wizardManager
				.getConfiguration(SakerIDESupportUtils.WIZARD_CONFIGURATION_EDIT_CLASSPATH);
		if (editcp != null) {
			editcp.accept(new ClassPathLocationIDEProperty.Visitor<Void, Void>() {
				@Override
				public Void visit(JarClassPathLocationIDEProperty property, Void param) {
					selectJavaArchive();
					ClassPathFileChooserSakerWizardPage next = (ClassPathFileChooserSakerWizardPage) continuation.get();
					next.setFile(property.getConnectionName(), property.getJarPath());
					return null;
				}

				@Override
				public Void visit(HttpUrlJarClassPathLocationIDEProperty property, Void param) {
					selectNetworkArchive();
					ClassPathNetworkArchiveSakerWizardPage next = (ClassPathNetworkArchiveSakerWizardPage) continuation
							.get();
					next.setUrl(property.getUrl());
					return null;
				}

				@Override
				public Void visit(BuiltinScriptingLanguageClassPathLocationIDEProperty property, Void param) {
					selectSakerScript();
					return null;
				}

				@Override
				public Void visit(NestRepositoryClassPathLocationIDEProperty property, Void param) {
					selectNestRepository();
					NestRepositoryVersionSakerWizardPage next = (NestRepositoryVersionSakerWizardPage) continuation
							.get();
					next.setVersion(property.getVersion());
					return null;
				}
			}, null);
		}
	}

	@Override
	public SakerWizardPage getNextPage() {
		if (continuation == null) {
			//nothing is selected yet
			return null;
		}
		AbstractSakerWizardPage np = continuation.orElse(null);
		if (np == null) {
			return super.getNextPage();
		}
		//set the next page
		np.setNextPage(super.getNextPage());
		return np;
	}

	public void unselect() {
		continuation = null;
		property = null;
		serviceEnumerator = null;
		selected = SELECTED_NONE;
	}

	public void selectJavaArchive() {
		ClassPathFileChooserSakerWizardPage cont = wizardManager
				.getWizardPage(ClassPathFileChooserSakerWizardPage.class);
		continuation = Optional.of(cont);
		property = null;
		serviceEnumerator = null;
		selected = SELECTED_JAVA_ARCHIVE;
	}

	public void selectNetworkArchive() {
		ClassPathNetworkArchiveSakerWizardPage cont = wizardManager
				.getWizardPage(ClassPathNetworkArchiveSakerWizardPage.class);
		continuation = Optional.of(cont);
		property = null;
		serviceEnumerator = null;
		selected = SELECTED_NETWORK_ARCHIVE;
	}

	public void selectNestRepository() {
		NestRepositoryVersionSakerWizardPage cont = wizardManager
				.getWizardPage(NestRepositoryVersionSakerWizardPage.class);
		continuation = Optional.of(cont);
		property = null;
		serviceEnumerator = new NestRepositoryFactoryServiceEnumeratorIDEProperty();
		selected = SELECTED_NEST_REPOSITORY;
	}

	public void selectSakerScript() {
		continuation = Optional.empty();
		property = BuiltinScriptingLanguageClassPathLocationIDEProperty.INSTANCE;
		serviceEnumerator = BuiltinScriptingLanguageServiceEnumeratorIDEProperty.INSTANCE;
		selected = SELECTED_SAKERSCRIPT;
	}

	@Override
	public ClassPathLocationIDEProperty getClassPathLocation() {
		return property;
	}

	public int getSelected() {
		return selected;
	}

	@Override
	public boolean canFinishWizard(WizardPageHistoryLink parent) {
		if (selected == SELECTED_NONE) {
			return false;
		}
		SakerWizardPage np = super.getNextPage();
		SakerWizardPage contpage = ObjectUtils.getOptional(continuation);
		WizardPageHistoryLink nextlink = WizardPageHistoryLink.next(parent, this);
		if (contpage != null) {
			return contpage.canFinishWizard(nextlink);
		}
		return np == null || np.canFinishWizard(nextlink);
	}

	@Override
	public Optional<?> finishWizard(WizardPageHistoryLink parent) {
		SakerWizardPage np = super.getNextPage();
		if (np == null) {
			return Optional.ofNullable(getClassPathLocation());
		}
		WizardPageHistoryLink nextlink = WizardPageHistoryLink.next(parent, this);
		SakerWizardPage contpage = ObjectUtils.getOptional(continuation);
		if (contpage != null) {
			return contpage.finishWizard(nextlink);
		}
		return np.finishWizard(nextlink);
	}

	@Override
	public ClassPathServiceEnumeratorIDEProperty getServiceEnumerator() {
		return serviceEnumerator;
	}

}
