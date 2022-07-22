/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package saker.build.ide.support;

import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;

import saker.build.daemon.DaemonLaunchParameters;
import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.ide.support.properties.BuiltinScriptingLanguageClassPathLocationIDEProperty;
import saker.build.ide.support.properties.BuiltinScriptingLanguageServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.ClassPathLocationIDEProperty;
import saker.build.ide.support.properties.ClassPathServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.DaemonConnectionIDEProperty;
import saker.build.ide.support.properties.HttpUrlJarClassPathLocationIDEProperty;
import saker.build.ide.support.properties.IDEProjectProperties;
import saker.build.ide.support.properties.JarClassPathLocationIDEProperty;
import saker.build.ide.support.properties.MountPathIDEProperty;
import saker.build.ide.support.properties.NamedClassClassPathServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.NestRepositoryClassPathLocationIDEProperty;
import saker.build.ide.support.properties.NestRepositoryFactoryServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.PropertiesValidationErrorResult;
import saker.build.ide.support.properties.ProviderMountIDEProperty;
import saker.build.ide.support.properties.RepositoryIDEProperty;
import saker.build.ide.support.properties.ScriptConfigurationIDEProperty;
import saker.build.ide.support.properties.ServiceLoaderClassPathEnumeratorIDEProperty;
import saker.build.ide.support.ui.wizard.ClassPathTypeChooserSakerWizardPage;
import saker.build.ide.support.ui.wizard.RepositoryIdentifierSakerWizardPage;
import saker.build.ide.support.ui.wizard.SakerWizardManager;
import saker.build.ide.support.ui.wizard.SakerWizardPage;
import saker.build.ide.support.ui.wizard.ScriptConfigurationSakerWizardPage;
import saker.build.ide.support.ui.wizard.ServiceEnumeratorRedirectingSakerWizardPage;
import saker.build.ide.support.ui.wizard.WizardPageHistoryLink;
import saker.build.runtime.params.NestRepositoryClassPathLocation;
import saker.build.scripting.model.FormattedTextContent;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class SakerIDESupportUtils {
	private SakerIDESupportUtils() {
		throw new UnsupportedOperationException();
	}

	public static ProviderMountIDEProperty getMountPropertyForPath(SakerPath path, IDEProjectProperties properties) {
		if (properties == null || path == null) {
			return null;
		}
		return getMountPropertyForPath(path, properties.getMounts());
	}

	public static Integer getPortValueOrNull(String value) {
		if (value == null) {
			return null;
		}
		int val;
		try {
			val = Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return null;
		}
		if (val < 0 || val > 0xFFFF) {
			return null;
		}
		return val;
	}

	public static boolean getBooleanValueOrDefault(String boolval, boolean def) {
		if (boolval == null) {
			return def;
		}
		return Boolean.parseBoolean(boolval.trim());
	}

	public static ProviderMountIDEProperty getMountPropertyForPath(SakerPath path,
			Iterable<? extends ProviderMountIDEProperty> mounts) {
		if (path == null) {
			return null;
		}
		if (ObjectUtils.isNullOrEmpty(mounts)) {
			return null;
		}
		String pathroot = path.getRoot();
		if (pathroot == null) {
			return null;
		}
		for (ProviderMountIDEProperty prop : mounts) {
			if (pathroot.equals(normalizePathRoot(prop.getRoot()))) {
				return prop;
			}
		}
		return null;
	}

	public static boolean isScriptModellingConfigurationAppliesTo(SakerPath execpath, IDEProjectProperties properties) {
		if (properties == null || execpath == null) {
			return false;
		}
		Set<? extends ScriptConfigurationIDEProperty> scriptconfigs = properties.getScriptConfigurations();
		if (ObjectUtils.isNullOrEmpty(scriptconfigs)) {
			return false;
		}

		Set<String> exclusions = properties.getScriptModellingExclusions();
		if (!ObjectUtils.isNullOrEmpty(exclusions)) {
			for (String excl : exclusions) {
				WildcardPath exclwc;
				try {
					exclwc = WildcardPath.valueOf(excl);
				} catch (IllegalArgumentException e) {
					continue;
				}
				if (exclwc.includes(execpath)) {
					return false;
				}
			}
		}

		for (ScriptConfigurationIDEProperty scprop : scriptconfigs) {
			String wcstr = scprop.getScriptsWildcard();
			if (ObjectUtils.isNullOrEmpty(wcstr)) {
				continue;
			}
			WildcardPath scriptwc;
			try {
				scriptwc = WildcardPath.valueOf(wcstr);
			} catch (IllegalArgumentException e) {
				continue;
			}
			if (!scriptwc.includes(execpath)) {
				continue;
			}
			return true;
		}
		return false;
	}

	public static SakerPath projectPathToExecutionPath(IDEProjectProperties ideprops, SakerPath projectlocalpath,
			SakerPath path) {
		if (path.isRelative()) {
			try {
				path = projectlocalpath.resolve(path);
			} catch (IllegalArgumentException e) {
				//if somewhy we fail to resolve the path. E.g. the path contains too many ".." at start
				return null;
			}
		}
		Set<? extends ProviderMountIDEProperty> mounts = ideprops.getMounts();
		if (ObjectUtils.isNullOrEmpty(mounts)) {
			return null;
		}
		for (ProviderMountIDEProperty mountprop : mounts) {
			String rootstr = mountprop.getRoot();
			String mountpathstr = mountprop.getMountPath();
			String clientname = mountprop.getMountClientName();
			//only null check for mount path, as it can be relative for project relative
			if (ObjectUtils.isNullOrEmpty(rootstr) || mountpathstr == null || ObjectUtils.isNullOrEmpty(clientname)) {
				continue;
			}
			String root;
			SakerPath mountpath;
			try {
				root = SakerPath.normalizeRoot(rootstr);
				mountpath = SakerPath.valueOf(mountpathstr);
			} catch (IllegalArgumentException e) {
				//invalid configuration, failed to parse
				continue;
			}
			if (SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE.equals(clientname)) {
				//the mount path is resolved against the project directory
				clientname = SakerIDEProject.MOUNT_ENDPOINT_LOCAL_FILESYSTEM;
				mountpath = projectlocalpath.resolve(mountpath.replaceRoot(null));
				//continue with testing local 
			}
			if (SakerIDEProject.MOUNT_ENDPOINT_LOCAL_FILESYSTEM.equals(clientname)) {
				int commonnamecount = path.getCommonNameCount(mountpath);
				if (commonnamecount >= 0) {
					return path.subPath(commonnamecount).replaceRoot(root);
				}
			}
		}
		return null;
	}

	public static SakerPath executionPathToProjectRelativePath(IDEProjectProperties properties,
			SakerPath projectsakerpath, SakerPath executionsakerpath) {
		if (executionsakerpath == null) {
			return null;
		}
		if (executionsakerpath.isRelative()) {
			SakerPath propworkdir = properties == null ? null : tryParsePath(properties.getWorkingDirectory());
			if (propworkdir == null || propworkdir.isRelative()) {
				return null;
			}
			executionsakerpath = propworkdir.resolve(executionsakerpath);
		}
		//the path to resolve is an absolute execution path

		ProviderMountIDEProperty mountprop = SakerIDESupportUtils.getMountPropertyForPath(executionsakerpath,
				properties);
		if (mountprop == null) {
			return null;
		}
		String mountclientname = mountprop.getMountClientName();
		//if mountclientname == null then we fail with null
		if (SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE.equals(mountclientname)) {
			//the mounting is project relative
			SakerPath mountedpath = tryParsePath(mountprop.getMountPath());
			if (mountedpath == null) {
				return null;
			}
			if (projectsakerpath != null) {
				SakerPath mountedfullpath = projectsakerpath.resolve(mountedpath.replaceRoot(null));
				executionsakerpath = mountedfullpath.resolve(executionsakerpath.replaceRoot(null));
				if (executionsakerpath.startsWith(projectsakerpath)) {
					return projectsakerpath.relativize(executionsakerpath);
				}
			}
			return null;
		}
		if (SakerIDEProject.MOUNT_ENDPOINT_LOCAL_FILESYSTEM.equals(mountclientname)) {
			//the mount is on the local filesystem which is where the project resides
			SakerPath mountedpath = tryParsePath(mountprop.getMountPath());
			if (mountedpath == null) {
				return null;
			}
			if (projectsakerpath != null) {
				executionsakerpath = mountedpath.resolve(executionsakerpath.replaceRoot(null));
				if (executionsakerpath.startsWith(projectsakerpath)) {
					return projectsakerpath.relativize(executionsakerpath);
				}
			}
			return null;
		}
		//the mount is made through a daemon connection, cannot determine the file system association
		return null;
	}

	public static String normalizePath(String path) {
		if (path == null) {
			return null;
		}
		try {
			return SakerPath.valueOf(path).toString();
		} catch (IllegalArgumentException e) {
			return path;
		}
	}

	public static SakerPath tryParsePath(String path) {
		if (path == null) {
			return null;
		}
		try {
			return SakerPath.valueOf(path);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public static String normalizePathRoot(String root) {
		if (root == null) {
			return null;
		}
		try {
			return SakerPath.normalizeRoot(root);
		} catch (IllegalArgumentException e) {
			return root;
		}
	}

	public static String tryNormalizePathRoot(String root) {
		if (root == null) {
			return null;
		}
		try {
			return SakerPath.normalizeRoot(root);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public static DaemonConnectionIDEProperty getConnectionPropertyWithName(
			Iterable<? extends DaemonConnectionIDEProperty> connections, String name) {
		if (ObjectUtils.isNullOrEmpty(connections) || ObjectUtils.isNullOrEmpty(name)) {
			return null;
		}
		for (DaemonConnectionIDEProperty prop : connections) {
			if (name.equals(prop.getConnectionName())) {
				return prop;
			}
		}
		return null;
	}

	public static String createValidationErrorMessage(PropertiesValidationErrorResult err) {
		try {
			return createValidationErrorMessageImpl(err);
		} catch (Exception e) {
			return "Failed to construct error message: " + e + " for: " + err;
		}
	}

	public static String classPathLocationToLabel(ClassPathLocationIDEProperty property) {
		if (property == null) {
			return null;
		}
		return property.accept(ClassPathLocationToStringVisitor.INSTANCE, null);
	}

	public static String serviceEnumeratorToLabel(ClassPathServiceEnumeratorIDEProperty property) {
		if (property == null) {
			return null;
		}
		return property.accept(ClassPathServiceEnumeratorToStringVisitor.INSTANCE, null);
	}

	public static String serviceEnumeratorToTitleLabel(ClassPathServiceEnumeratorIDEProperty property) {
		if (property == null) {
			return null;
		}
		return property.accept(ClassPathServiceEnumeratorTitleVisitor.INSTANCE, null);
	}

	public static final String WIZARD_CONFIGURATION_EDIT_CLASSPATH = "saker.wizard.config.edit.classpath";
	public static final String WIZARD_CONFIGURATION_EDIT_SERVICE = "saker.wizard.config.edit.service";
	public static final String WIZARD_CONFIGURATION_EDIT_SCRIPTCONFIG = "saker.wizard.config.edit.scriptconfig";
	public static final String WIZARD_CONFIGURATION_EDIT_REPOSITORYID = "saker.wizard.config.edit.repositoryid";

	public static final String WIZARDPAGE_CONFIGURATION_EDIT_INITIAL_CLASSPATH = "saker.wizardpage.config.edit.init.classpath";
	public static final String WIZARDPAGE_CONFIGURATION_EDIT_INITIAL_SERVICE = "saker.wizardpage.config.edit.init.service";
	public static final String WIZARDPAGE_CONFIGURATION_EDIT_INITIAL_SCRIPTCONFIG = "saker.wizardpage.config.edit.init.scriptconfig";
	public static final String WIZARDPAGE_CONFIGURATION_EDIT_INITIAL_REPOSITORYID = "saker.wizardpage.config.edit.init.repositoryid";

	public static void editScriptConfigurationWithWizardManager(SakerWizardManager<?> manager,
			ScriptConfigurationIDEProperty property) {
		if (property == null) {
			return;
		}
		manager.setConfiguration(WIZARD_CONFIGURATION_EDIT_CLASSPATH, property.getClassPathLocation());
		manager.setConfiguration(WIZARD_CONFIGURATION_EDIT_SERVICE, property.getServiceEnumerator());
		manager.setConfiguration(WIZARD_CONFIGURATION_EDIT_SCRIPTCONFIG, property);
	}

	public static void editRepositoryConfigurationWithWizardManager(SakerWizardManager<?> manager,
			RepositoryIDEProperty property) {
		if (property == null) {
			return;
		}
		manager.setConfiguration(WIZARD_CONFIGURATION_EDIT_CLASSPATH, property.getClassPathLocation());
		manager.setConfiguration(WIZARD_CONFIGURATION_EDIT_SERVICE, property.getServiceEnumerator());
		manager.setConfiguration(WIZARD_CONFIGURATION_EDIT_REPOSITORYID, property.getRepositoryIdentifier());
	}

	public static SakerWizardPage createScriptConfigurationWizardSteps(
			SakerWizardManager<? super SakerWizardPage> manager) {
		ClassPathTypeChooserSakerWizardPage typechooser = manager
				.getWizardPage(ClassPathTypeChooserSakerWizardPage.class);
		ServiceEnumeratorRedirectingSakerWizardPage servicenumeratorpage = manager
				.getWizardPage(ServiceEnumeratorRedirectingSakerWizardPage.class);
		ScriptConfigurationSakerWizardPage scriptconfigpage = manager
				.getWizardPage(ScriptConfigurationSakerWizardPage.class);

		typechooser.setNextPage(servicenumeratorpage);
		servicenumeratorpage.setNextPage(scriptconfigpage);

		typechooser.setPageConfiguration(WIZARDPAGE_CONFIGURATION_EDIT_INITIAL_CLASSPATH, true);
		servicenumeratorpage.setPageConfiguration(WIZARDPAGE_CONFIGURATION_EDIT_INITIAL_SERVICE, true);
		scriptconfigpage.setPageConfiguration(WIZARDPAGE_CONFIGURATION_EDIT_INITIAL_SCRIPTCONFIG, true);

		return typechooser;
	}

	public static SakerWizardPage createRepositoryConfigurationWizardSteps(
			SakerWizardManager<? super SakerWizardPage> manager) {
		ClassPathTypeChooserSakerWizardPage typechooser = manager
				.getWizardPage(ClassPathTypeChooserSakerWizardPage.class);
		ServiceEnumeratorRedirectingSakerWizardPage servicenumeratorpage = manager
				.getWizardPage(ServiceEnumeratorRedirectingSakerWizardPage.class);
		RepositoryIdentifierSakerWizardPage identifierpage = manager
				.getWizardPage(RepositoryIdentifierSakerWizardPage.class);

		typechooser.setNextPage(servicenumeratorpage);
		servicenumeratorpage.setNextPage(identifierpage);

		typechooser.setPageConfiguration(WIZARDPAGE_CONFIGURATION_EDIT_INITIAL_CLASSPATH, true);
		servicenumeratorpage.setPageConfiguration(WIZARDPAGE_CONFIGURATION_EDIT_INITIAL_SERVICE, true);
		identifierpage.setPageConfiguration(WIZARDPAGE_CONFIGURATION_EDIT_INITIAL_REPOSITORYID, true);

		return typechooser;
	}

	public static String resolveInformationTitle(String title, String subtitle) {
		if (!ObjectUtils.isNullOrEmpty(title)) {
			return title;
		}
		if (!ObjectUtils.isNullOrEmpty(subtitle)) {
			return subtitle;
		}
		return "";
	}

	public static String resolveInformationSubTitle(String title, String subtitle) {
		if (!ObjectUtils.isNullOrEmpty(title)) {
			//there's a title, so we can just return the subtitle accordingly
			return ObjectUtils.nullDefault(subtitle, "");
		}
		//return empty, as the subtitle is automatically promoted to the title and is not used as the subtitle
		return "";
	}

	public static Entry<String, String> resolveTitlesAsEntry(String title, String subtitle) {
		return ImmutableUtils.makeImmutableMapEntry(resolveInformationTitle(title, subtitle),
				resolveInformationSubTitle(title, subtitle));
	}

	public static boolean isNullOrEmpty(FormattedTextContent text) {
		if (text == null) {
			return true;
		}
		Set<String> formats = text.getAvailableFormats();
		if (ObjectUtils.isNullOrEmpty(formats)) {
			return true;
		}
		for (String f : formats) {
			if (!ObjectUtils.isNullOrEmpty(text.getFormattedText(f))) {
				return false;
			}
		}
		return true;
	}

	public static <T> T iterateOverWizardPages(SakerWizardPage page,
			Function<? super SakerWizardPage, ? extends T> consumer) {
		WizardPageHistoryLink historylink = null;
		while (page != null) {
			T res = consumer.apply(page);
			if (res != null) {
				return res;
			}
			SakerWizardPage np = page.getNextPage();
			if (np == null) {
				break;
			}
			historylink = WizardPageHistoryLink.next(historylink, page);
			while (true) {
				SakerWizardPage redirected = np.redirectPage(historylink);
				if (redirected == null) {
					break;
				}
				np = redirected;
			}
			page = np;
		}
		return null;
	}

	public static SakerWizardPage findEditorInitialPageWithPriorities(SakerWizardPage startpage,
			String... initialpageconfigids) {
		if (ObjectUtils.isNullOrEmpty(initialpageconfigids)) {
			return null;
		}
		SakerWizardPage[] result = new SakerWizardPage[initialpageconfigids.length];
		SakerWizardPage found = iterateOverWizardPages(startpage, page -> {
			for (int i = 0; i < initialpageconfigids.length; i++) {
				String confid = initialpageconfigids[i];
				if (confid == null) {
					continue;
				}
				if (page.getPageConfiguration(confid) != null) {
					if (i == 0) {
						return page;
					}
					result[i] = page;
				}
			}
			return null;
		});
		if (found != null) {
			return found;
		}
		//we can start from 1 as if we would've found the first, then it would've been returned already
		for (int i = 1; i < result.length; i++) {
			SakerWizardPage rp = result[i];
			if (rp != null) {
				return rp;
			}
		}
		return null;
	}

	private static class ClassPathLocationToStringVisitor
			implements ClassPathLocationIDEProperty.Visitor<String, Void> {
		public static final ClassPathLocationToStringVisitor INSTANCE = new ClassPathLocationToStringVisitor();

		@Override
		public String visit(JarClassPathLocationIDEProperty property, Void param) {
			String connname = property.getConnectionName();
			if (!ObjectUtils.isNullOrEmpty(connname)) {
				if (SakerIDEProject.MOUNT_ENDPOINT_LOCAL_FILESYSTEM.equals(connname)
						|| SakerIDEProject.MOUNT_ENDPOINT_PROJECT_RELATIVE.equals(connname)) {
					return ObjectUtils.nullDefault(normalizePath(property.getJarPath()), "");
				}
				return connname + ":/" + normalizePath(property.getJarPath());
			}
			return ObjectUtils.nullDefault(normalizePath(property.getJarPath()), "");
		}

		@Override
		public String visit(HttpUrlJarClassPathLocationIDEProperty property, Void param) {
			return ObjectUtils.nullDefault(property.getUrl(), "");
		}

		@Override
		public String visit(BuiltinScriptingLanguageClassPathLocationIDEProperty property, Void param) {
			return "SakerScript";
		}

		@Override
		public String visit(NestRepositoryClassPathLocationIDEProperty property, Void param) {
			String ver = property.getVersion();
			if (!ObjectUtils.isNullOrEmpty(ver)) {
				return "Nest repository v" + ver;
			}
			return "Nest repository v" + NestRepositoryClassPathLocation.DEFAULT_VERSION + " (default)";
		}
	}

	private static class ClassPathServiceEnumeratorTitleVisitor
			implements ClassPathServiceEnumeratorIDEProperty.Visitor<String, Void> {
		public static final ClassPathServiceEnumeratorTitleVisitor INSTANCE = new ClassPathServiceEnumeratorTitleVisitor();

		@Override
		public String visit(ServiceLoaderClassPathEnumeratorIDEProperty property, Void param) {
			return "Service";
		}

		@Override
		public String visit(NamedClassClassPathServiceEnumeratorIDEProperty property, Void param) {
			return "Class name";
		}

		@Override
		public String visit(BuiltinScriptingLanguageServiceEnumeratorIDEProperty property, Void param) {
			return "Service relation";
		}

		@Override
		public String visit(NestRepositoryFactoryServiceEnumeratorIDEProperty property, Void param) {
			return "Service relation";
		}
	}

	private static class ClassPathServiceEnumeratorToStringVisitor
			implements ClassPathServiceEnumeratorIDEProperty.Visitor<String, Void> {
		public static final ClassPathServiceEnumeratorToStringVisitor INSTANCE = new ClassPathServiceEnumeratorToStringVisitor();

		@Override
		public String visit(ServiceLoaderClassPathEnumeratorIDEProperty property, Void param) {
			return property.getServiceClass();
		}

		@Override
		public String visit(NamedClassClassPathServiceEnumeratorIDEProperty property, Void param) {
			return property.getClassName();
		}

		@Override
		public String visit(BuiltinScriptingLanguageServiceEnumeratorIDEProperty property, Void param) {
			return "SakerScript";
		}

		@Override
		public String visit(NestRepositoryFactoryServiceEnumeratorIDEProperty property, Void param) {
			return "Nest repository";
		}
	}

	private static String createValidationErrorMessageImpl(PropertiesValidationErrorResult err) {
		String type = err.errorType;
		switch (type) {
			case SakerIDEProject.NS_DAEMON_CONNECTION + SakerIDEProject.C_ADDRESS + SakerIDEProject.E_MISSING: {
				DaemonConnectionIDEProperty property = (DaemonConnectionIDEProperty) err.relatedSubject;
				String cname = property.getConnectionName();
				return "Daemon connection address is missing." + (cname == null ? "" : " (" + cname + ")");
			}
			case SakerIDEProject.NS_DAEMON_CONNECTION + SakerIDEProject.C_NAME + SakerIDEProject.E_MISSING: {
				DaemonConnectionIDEProperty property = (DaemonConnectionIDEProperty) err.relatedSubject;
				String address = property.getNetAddress();
				return "Daemon connection name is missing." + (address == null ? "" : " (" + address + ")");
			}
			case SakerIDEProject.NS_DAEMON_CONNECTION + SakerIDEProject.C_NAME + SakerIDEProject.E_RESERVED: {
				DaemonConnectionIDEProperty property = (DaemonConnectionIDEProperty) err.relatedSubject;
				return "Daemon connection name is reserved: " + property.getConnectionName();
			}
			case SakerIDEProject.NS_DAEMON_CONNECTION + SakerIDEProject.C_NAME + SakerIDEProject.E_DUPLICATE: {
				DaemonConnectionIDEProperty property = (DaemonConnectionIDEProperty) err.relatedSubject;
				return "Duplicate daemon connection name: " + property.getConnectionName();
			}

			case SakerIDEProject.NS_PROVIDER_MOUNT + SakerIDEProject.C_ROOT + SakerIDEProject.E_MISSING: {
				return "Missing mount root.";
			}
			case SakerIDEProject.NS_PROVIDER_MOUNT + SakerIDEProject.C_ROOT + SakerIDEProject.E_DUPLICATE: {
				ProviderMountIDEProperty property = (ProviderMountIDEProperty) err.relatedSubject;
				return "Duplicate mounted root: " + normalizePathRoot(property.getRoot());
			}
			case SakerIDEProject.NS_PROVIDER_MOUNT + SakerIDEProject.C_ROOT + SakerIDEProject.E_FORMAT: {
				ProviderMountIDEProperty property = (ProviderMountIDEProperty) err.relatedSubject;
				return "Invalid mount root format: " + normalizePathRoot(property.getRoot());
			}
			case SakerIDEProject.NS_PROVIDER_MOUNT + SakerIDEProject.C_CLIENT + SakerIDEProject.E_MISSING: {
				return "Missing mount file system endpoint.";
			}
			case SakerIDEProject.NS_PROVIDER_MOUNT + SakerIDEProject.C_PATH + SakerIDEProject.E_MISSING: {
				return "Missing mounted path.";
			}
			case SakerIDEProject.NS_PROVIDER_MOUNT + SakerIDEProject.C_PATH + SakerIDEProject.E_RELATIVE: {
				ProviderMountIDEProperty property = (ProviderMountIDEProperty) err.relatedSubject;
				return "Mounted path must be absolute: " + normalizePath(property.getMountPath());
			}
			case SakerIDEProject.NS_PROVIDER_MOUNT + SakerIDEProject.C_PATH + SakerIDEProject.E_INVALID_ROOT: {
				ProviderMountIDEProperty property = (ProviderMountIDEProperty) err.relatedSubject;
				return "Mounted path root is invalid: " + normalizePath(property.getMountPath());
			}
			case SakerIDEProject.NS_PROVIDER_MOUNT + SakerIDEProject.C_PATH + SakerIDEProject.E_FORMAT: {
				ProviderMountIDEProperty property = (ProviderMountIDEProperty) err.relatedSubject;
				return "Invalid mounted path format: " + normalizePath(property.getMountPath());
			}

			case SakerIDEProject.NS_BUILD_TRACE_OUT + SakerIDEProject.C_CLIENT + SakerIDEProject.E_MISSING: {
				return "Build trace output: Missing file system endpoint.";
			}
			case SakerIDEProject.NS_BUILD_TRACE_OUT + SakerIDEProject.C_PATH + SakerIDEProject.E_MISSING: {
				return "Build trace output: Missing path.";
			}
			case SakerIDEProject.NS_BUILD_TRACE_OUT + SakerIDEProject.C_PATH + SakerIDEProject.E_RELATIVE: {
				MountPathIDEProperty property = (MountPathIDEProperty) err.relatedSubject;
				return "Build trace output: Output path must be absolute: " + normalizePath(property.getMountPath());
			}
			case SakerIDEProject.NS_BUILD_TRACE_OUT + SakerIDEProject.C_PATH + SakerIDEProject.E_INVALID_ROOT: {
				MountPathIDEProperty property = (MountPathIDEProperty) err.relatedSubject;
				return "Build trace output: Output path root is invalid: " + normalizePath(property.getMountPath());
			}
			case SakerIDEProject.NS_BUILD_TRACE_OUT + SakerIDEProject.C_PATH + SakerIDEProject.E_FORMAT: {
				MountPathIDEProperty property = (MountPathIDEProperty) err.relatedSubject;
				return "Build trace output: Invalid path format: " + normalizePath(property.getMountPath());
			}

			case SakerIDEProject.NS_EXECUTION_DAEMON_NAME + SakerIDEProject.E_MISSING_DAEMON: {
				return "Daemon connection not found for execution daemon name: " + err.relatedSubject;
			}
			case SakerIDEProject.NS_USER_PARAMETERS + SakerIDEProject.E_INVALID_KEY: {
				return "Invalid user parameter key: "
						+ (err.relatedSubject == null ? "null" : "\"" + err.relatedSubject + "\"");
			}
			case SakerIDEProject.NS_USER_PARAMETERS + SakerIDEProject.E_DUPLICATE_KEY: {
				return "Duplicate user parameter key: " + "\"" + err.relatedSubject + "\"";
			}

			case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_WILDCARD + SakerIDEProject.E_MISSING: {
				return "Missing wildcard for script configuration.";
			}
			case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_WILDCARD + SakerIDEProject.E_DUPLICATE: {
				ScriptConfigurationIDEProperty property = (ScriptConfigurationIDEProperty) err.relatedSubject;
				return "Duplicate wildcard for script configuration: " + property.getScriptsWildcard();
			}
			case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_WILDCARD + SakerIDEProject.E_FORMAT: {
				ScriptConfigurationIDEProperty property = (ScriptConfigurationIDEProperty) err.relatedSubject;
				return "Invalid wildcard format for script configuration: " + property.getScriptsWildcard();
			}
			case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_OPTIONS + SakerIDEProject.E_INVALID_KEY: {
				String key = (String) ((Object[]) err.relatedSubject)[1];
				return "Invalid script configuration option key: " + (key == null ? "null" : "\"" + key + "\"");
			}
			case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_OPTIONS
					+ SakerIDEProject.E_DUPLICATE_KEY: {
				String key = (String) ((Object[]) err.relatedSubject)[1];
				return "Duplicate script configuration option key: " + (key == null ? "null" : "\"" + key + "\"");
			}
			case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_CLASSPATH + SakerIDEProject.E_MISSING: {
				return "Missing script configuration class path.";
			}

			case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_CLASSPATH + SakerIDEProject.C_JAR
					+ SakerIDEProject.C_CONNECTION + SakerIDEProject.E_MISSING_DAEMON: {
				return "Script configuration JAR class path daemon connection is missing.";
			}
			case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_CLASSPATH + SakerIDEProject.C_JAR
					+ SakerIDEProject.C_PATH + SakerIDEProject.E_MISSING: {
				return "Script configuration JAR file path is missing.";
			}
			case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_CLASSPATH + SakerIDEProject.C_JAR
					+ SakerIDEProject.C_PATH + SakerIDEProject.E_FORMAT: {
				ScriptConfigurationIDEProperty property = (ScriptConfigurationIDEProperty) err.relatedSubject;
				JarClassPathLocationIDEProperty cplocation = (JarClassPathLocationIDEProperty) property
						.getClassPathLocation();
				return "Invalid script configuration JAR file path format: " + normalizePath(cplocation.getJarPath());
			}
			case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_CLASSPATH + SakerIDEProject.C_HTTP_URL_JAR
					+ SakerIDEProject.C_URL + SakerIDEProject.E_MISSING: {
				return "Script configuration class path  HTTP/HTTPS URL is missing.";
			}
			case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_CLASSPATH + SakerIDEProject.C_HTTP_URL_JAR
					+ SakerIDEProject.C_URL + SakerIDEProject.E_PROTOCOL: {
				ScriptConfigurationIDEProperty property = (ScriptConfigurationIDEProperty) err.relatedSubject;
				HttpUrlJarClassPathLocationIDEProperty cplocation = (HttpUrlJarClassPathLocationIDEProperty) property
						.getClassPathLocation();
				return "Invalid script configuration class path HTTP/HTTPS URL protocol: " + cplocation.getUrl();
			}
			case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_CLASSPATH + SakerIDEProject.C_HTTP_URL_JAR
					+ SakerIDEProject.C_URL + SakerIDEProject.E_FORMAT: {
				ScriptConfigurationIDEProperty property = (ScriptConfigurationIDEProperty) err.relatedSubject;
				HttpUrlJarClassPathLocationIDEProperty cplocation = (HttpUrlJarClassPathLocationIDEProperty) property
						.getClassPathLocation();
				return "Invalid script configuration class path  HTTP/HTTPS URL format: " + cplocation.getUrl();
			}
			//the following can't happen
//			case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_CLASSPATH
//					+ SakerIDEProject.C_BUILTIN_SCRIPTING + SakerIDEProject.E_ILLEGAL: {
//				break;
//			}
			case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_CLASSPATH
					+ SakerIDEProject.C_NEST_REPOSITORY + SakerIDEProject.E_ILLEGAL: {
				return "Invalid class path for script configuration.";
			}

			case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_SERVICE + SakerIDEProject.E_MISSING: {
				return "Script language implementation loader is missing.";
			}
			case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_SERVICE + SakerIDEProject.C_SERVICE_LOADER
					+ SakerIDEProject.C_CLASS + SakerIDEProject.E_MISSING: {
				return "Script language service loader class is missing.";
			}
			case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_SERVICE + SakerIDEProject.C_NAMED_CLASS
					+ SakerIDEProject.C_CLASS + SakerIDEProject.E_MISSING: {
				return "Script language implementation class name is missing.";
			}
			//the following can't happen
//			case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_SERVICE
//					+ SakerIDEProject.C_BUILTIN_SCRIPTING + SakerIDEProject.E_ILLEGAL: {
//				break;
//			}
			case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_SERVICE + SakerIDEProject.C_NEST_REPOSITORY
					+ SakerIDEProject.E_ILLEGAL: {
				return "Invalid service configuration for script configuration.";
			}
			case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_SERVICE + SakerIDEProject.C_NEST_REPOSITORY
					+ SakerIDEProject.E_VERSION_FORMAT: {
				return "Invalid version number format for saker.nest repository classpath.";
			}
			case SakerIDEProject.NS_SCRIPT_CONFIGURATION + SakerIDEProject.C_CLASSPATH + SakerIDEProject.C_SERVICE
					+ SakerIDEProject.E_INVALID_COMBINATION: {
				return "Invalid class path and service combination for script configuration.";
			}

			case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_IDENTIFIER
					+ SakerIDEProject.E_DUPLICATE: {
				RepositoryIDEProperty property = (RepositoryIDEProperty) err.relatedSubject;
				return "Duplicate repository identifier: " + property.getRepositoryIdentifier();
			}

			case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_CLASSPATH + SakerIDEProject.C_JAR
					+ SakerIDEProject.C_CONNECTION + SakerIDEProject.E_MISSING_DAEMON: {
				return "Repository configuration JAR class path daemon connection is missing.";
			}
			case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_CLASSPATH + SakerIDEProject.C_JAR
					+ SakerIDEProject.C_PATH + SakerIDEProject.E_MISSING: {
				return "Repository configuration JAR file path is missing.";
			}
			case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_CLASSPATH + SakerIDEProject.C_JAR
					+ SakerIDEProject.C_PATH + SakerIDEProject.E_FORMAT: {
				RepositoryIDEProperty property = (RepositoryIDEProperty) err.relatedSubject;
				JarClassPathLocationIDEProperty cplocation = (JarClassPathLocationIDEProperty) property
						.getClassPathLocation();
				return "Invalid repository configuration JAR file path format: "
						+ normalizePath(cplocation.getJarPath());
			}
			case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_CLASSPATH
					+ SakerIDEProject.C_HTTP_URL_JAR + SakerIDEProject.C_URL + SakerIDEProject.E_MISSING: {
				return "Repository configuration class path HTTP/HTTPS URL is missing.";
			}
			case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_CLASSPATH
					+ SakerIDEProject.C_HTTP_URL_JAR + SakerIDEProject.C_URL + SakerIDEProject.E_PROTOCOL: {
				RepositoryIDEProperty property = (RepositoryIDEProperty) err.relatedSubject;
				HttpUrlJarClassPathLocationIDEProperty cplocation = (HttpUrlJarClassPathLocationIDEProperty) property
						.getClassPathLocation();
				return "Invalid repository configuration class path HTTP/HTTPS URL protocol: " + cplocation.getUrl();
			}
			case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_CLASSPATH
					+ SakerIDEProject.C_HTTP_URL_JAR + SakerIDEProject.C_URL + SakerIDEProject.E_FORMAT: {
				RepositoryIDEProperty property = (RepositoryIDEProperty) err.relatedSubject;
				HttpUrlJarClassPathLocationIDEProperty cplocation = (HttpUrlJarClassPathLocationIDEProperty) property
						.getClassPathLocation();
				return "Invalid repository configuration class path  HTTP/HTTPS URL format: " + cplocation.getUrl();
			}
			case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_CLASSPATH
					+ SakerIDEProject.C_BUILTIN_SCRIPTING + SakerIDEProject.E_ILLEGAL: {
				return "Invalid class path for repository configuration.";
			}
			//the following can't happen
//			case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_CLASSPATH
//					+ SakerIDEProject.C_NEST_REPOSITORY + SakerIDEProject.E_ILLEGAL: {
//				break;
//			}

			case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_SERVICE + SakerIDEProject.E_MISSING: {
				return "Repository implementation loader is missing.";
			}
			case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_SERVICE
					+ SakerIDEProject.C_SERVICE_LOADER + SakerIDEProject.C_CLASS + SakerIDEProject.E_MISSING: {
				return "Repository service loader class is missing.";
			}
			case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_SERVICE + SakerIDEProject.C_NAMED_CLASS
					+ SakerIDEProject.C_CLASS + SakerIDEProject.E_MISSING: {
				return "Repository implementation class name is missing.";
			}
			case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_SERVICE
					+ SakerIDEProject.C_BUILTIN_SCRIPTING + SakerIDEProject.E_ILLEGAL: {
				return "Invalid service configuration for repository configuration.";
			}
			//the following can't happen
//			case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_SERVICE
//					+ SakerIDEProject.C_NEST_REPOSITORY + SakerIDEProject.E_ILLEGAL: {
//				break;
//			}
			case SakerIDEProject.NS_REPOSITORY_CONFIGURATION + SakerIDEProject.C_CLASSPATH + SakerIDEProject.C_SERVICE
					+ SakerIDEProject.E_INVALID_COMBINATION: {
				return "Invalid class path and service combination for repository configuration.";
			}

			case SakerIDEProject.NS_MIRROR_DIRECTORY + SakerIDEProject.E_FORMAT: {
				return "Invalid mirror directory format: " + err.relatedSubject;
			}
			case SakerIDEProject.NS_MIRROR_DIRECTORY + SakerIDEProject.E_RELATIVE: {
				return "Mirror directory must be absolute: " + err.relatedSubject;
			}

			case SakerIDEProject.NS_WORKING_DIRECTORY + SakerIDEProject.E_MISSING: {
				return "Missing working directory configuration.";
			}
			case SakerIDEProject.NS_WORKING_DIRECTORY + SakerIDEProject.E_FORMAT: {
				return "Invalid working directory format: " + err.relatedSubject;
			}
			case SakerIDEProject.NS_WORKING_DIRECTORY + SakerIDEProject.E_RELATIVE: {
				return "Working directory must be absolute: " + err.relatedSubject;
			}
			case SakerIDEProject.NS_WORKING_DIRECTORY + SakerIDEProject.E_ROOT_NOT_FOUND: {
				return "Working directory root not found: " + err.relatedSubject;
			}

			case SakerIDEProject.NS_BUILD_DIRECTORY + SakerIDEProject.E_FORMAT: {
				return "Invalid build directory format: " + err.relatedSubject;
			}
			case SakerIDEProject.NS_BUILD_DIRECTORY + SakerIDEProject.E_ROOT_NOT_FOUND: {
				return "Build directory root not found: " + err.relatedSubject;
			}

			case SakerIDEProject.NS_SCRIPT_MODELLING_EXCLUSION + SakerIDEProject.E_FORMAT: {
				return "Invalid script modelling exclusion format: " + err.relatedSubject;
			}
			case SakerIDEProject.NS_SCRIPT_MODELLING_EXCLUSION + SakerIDEProject.E_DUPLICATE: {
				return "Duplicate script modelling exclusion wildcard: " + err.relatedSubject;
			}

			default: {
				StringBuilder sb = new StringBuilder();
				sb.append("Unrecognized error: ");
				sb.append(type);
				Object subject = err.relatedSubject;
				if (subject != null) {
					sb.append(": ");
					sb.append(subject);
				}
				return sb.toString();
			}
		}
	}

	public static DaemonLaunchParameters cleanParametersForFailedSSL(DaemonLaunchParameters params) {
		if (!params.isActsAsServer() && params.getPort() == null) {
			return params;
		}
		return DaemonLaunchParameters.builder(params).setActsAsServer(false).setPort(null).build();
	}
}
