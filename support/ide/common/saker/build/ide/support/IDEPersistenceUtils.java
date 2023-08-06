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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import saker.build.ide.configuration.IDEConfiguration;
import saker.build.ide.configuration.SimpleIDEConfiguration;
import saker.build.ide.support.persist.DataFormatException;
import saker.build.ide.support.persist.DuplicateObjectFieldException;
import saker.build.ide.support.persist.StructuredArrayObjectInput;
import saker.build.ide.support.persist.StructuredArrayObjectOutput;
import saker.build.ide.support.persist.StructuredDataType;
import saker.build.ide.support.persist.StructuredObjectInput;
import saker.build.ide.support.persist.StructuredObjectOutput;
import saker.build.ide.support.properties.BuiltinScriptingLanguageClassPathLocationIDEProperty;
import saker.build.ide.support.properties.BuiltinScriptingLanguageServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.ClassPathLocationIDEProperty;
import saker.build.ide.support.properties.ClassPathServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.DaemonConnectionIDEProperty;
import saker.build.ide.support.properties.HttpUrlJarClassPathLocationIDEProperty;
import saker.build.ide.support.properties.IDEPluginProperties;
import saker.build.ide.support.properties.IDEProjectProperties;
import saker.build.ide.support.properties.JarClassPathLocationIDEProperty;
import saker.build.ide.support.properties.MountPathIDEProperty;
import saker.build.ide.support.properties.NamedClassClassPathServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.NestRepositoryClassPathLocationIDEProperty;
import saker.build.ide.support.properties.NestRepositoryFactoryServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.ParameterizedBuildTargetIDEProperty;
import saker.build.ide.support.properties.ProviderMountIDEProperty;
import saker.build.ide.support.properties.RepositoryIDEProperty;
import saker.build.ide.support.properties.ScriptConfigurationIDEProperty;
import saker.build.ide.support.properties.ServiceLoaderClassPathEnumeratorIDEProperty;
import saker.build.ide.support.properties.SimpleIDEProjectProperties;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class IDEPersistenceUtils {
	private static final String SP_NEST_REPOSITORY_SERVICE = "nest-repository-service";
	private static final String SP_BUILTIN_SCRIPTING_SERVICE = "builtin-scripting-service";
	private static final String SP_CLASS_NAME = "class-name";
	private static final String SP_SERVICELOADER = "serviceloader";
	private static final String CP_NEST_REPOSITORY_CLASSPATH = "nest-repository-classpath";
	private static final String CP_BUILTIN_SCRIPTING_CLASSPATH = "builtin-scripting-classpath";
	private static final String CP_HTTP_URL = "http-url";
	private static final String CP_JAR = "jar";
	private static final String F_VAL = "val";
	private static final String F_KEY = "key";
	private static final String F_CLASS_NAME = "class_name";
	private static final String F_SERVICE_CLASS = "service_class";
	private static final String F_URL = "url";
	private static final String F_CONNECTION = "connection";
	private static final String F_SCRIPT_MODELLING_EXCLUSIONS = "script_modelling_exclusions";
	private static final String F_PARAMETERIZED_BUILD_TARGETS = "parameterized_build_targets";
	private static final String F_OPTIONS = "options";
	private static final String F_WILDCARD = "wildcard";
	private static final String F_SCRIPT_CONFIGS = "script_configs";
	private static final String F_PATH = "path";
	private static final String F_SCRIPT_PATH = "script_path";
	private static final String F_TARGET_NAME = "target_name";
	private static final String F_CLIENT = "client";
	private static final String F_ROOT = "root";
	private static final String F_MOUNTS = "mounts";
	private static final String F_USE_AS_CLUSTER = "use_as_cluster";
	private static final String F_NAME = "name";
	private static final String F_VALUE = "value";
	private static final String F_ADDRESS = "address";
	private static final String F_CONNECTIONS = "connections";
	private static final String F_SERVICE = "service";
	private static final String F_CLASSPATH = "classpath";
	private static final String F_REPO_ID = "repo_id";
	private static final String F_REPOSITORIES = "repositories";
	private static final String F_REQUIRE_IDE_CONFIG = "require_ide_config";
	private static final String F_BUILD_TRACE_OUT_PATH = "build_trace_out_path";
	private static final String F_BUILD_TRACE_OUT_CLIENT = "build_trace_out_client";
	private static final String F_BUILD_TRACE_EMBED_ARTIFACTS = "build_trace_embed_artifacts";
	private static final String F_EXECUTION_DAEMON = "execution_daemon";
	private static final String F_MIRROR_DIR = "mirror_dir";
	private static final String F_BUILD_DIR = "build_dir";
	private static final String F_WORKING_DIR = "working_dir";
	private static final String F_FIELDS = "fields";
	private static final String F_IDENTIFIER = "identifier";
	private static final String F_TYPE = "type";
	private static final String F_VERSION = "version";
	private static final String F_ACTS_AS_SERVER = "acts_as_server";
	private static final String F_KEY_STORE_PATH = "key_store_path";
	private static final String F_PORT = "port";
	private static final String F_EXCEPTION_FORMAT = "exception_format";
	private static final String F_USER_PARAMETERS = "user_parameters";
	private static final String F_PARAMETERS = "parameters";
	private static final String F_STORAGE_DIRECTORY = "storage_directory";
	private static final String F_USE_CLIENTS_AS_CLUSTERS = "use_clients_as_clusters";

	//XXX make persisting data exception proof
	private IDEPersistenceUtils() {
		throw new UnsupportedOperationException();
	}

	public static void writeIDEPluginProperties(StructuredObjectOutput out, IDEPluginProperties props)
			throws IOException {
		out.writeField(F_VERSION, 1);
		writeStringIfNotNull(out, F_STORAGE_DIRECTORY, props.getStorageDirectory());

		Set<? extends Entry<String, String>> userparams = props.getUserParameters();
		writeStringMap(out, userparams, F_USER_PARAMETERS);

		writeStringIfNotNull(out, F_EXCEPTION_FORMAT, props.getExceptionFormat());
		writeStringIfNotNull(out, F_PORT, props.getPort());
		writeStringIfNotNull(out, F_ACTS_AS_SERVER, props.getActsAsServer());
		writeStringIfNotNull(out, F_KEY_STORE_PATH, props.getKeyStorePath());
	}

	public static IDEPluginProperties readIDEPluginProperties(StructuredObjectInput input) throws IOException {
		SimpleIDEPluginProperties.Builder result = SimpleIDEPluginProperties.builder();

		Integer version = input.readInt(F_VERSION);
		//version is unhandled for now, as there's currently only one format. if new formats are introduced, it will
		// be checked and the values interpreted accordingly

		result.setStorageDirectory(input.readString(F_STORAGE_DIRECTORY));

		try (StructuredArrayObjectInput array = input.readArray(F_USER_PARAMETERS)) {
			if (array != null) {
				Set<Entry<String, String>> map = readStringMap(array);
				result.setUserParameters(map);
			}
		}

		result.setExceptionFormat(input.readString(F_EXCEPTION_FORMAT));
		result.setPort(input.readString(F_PORT));
		result.setActsAsServer(input.readString(F_ACTS_AS_SERVER));
		result.setKeyStorePath(input.readString(F_KEY_STORE_PATH));

		return result.build();
	}

	public static void writeIDEConfiguration(StructuredObjectOutput out, IDEConfiguration config) throws IOException {
		out.writeField(F_TYPE, config.getType());
		out.writeField(F_IDENTIFIER, config.getIdentifier());

		try (StructuredObjectOutput fout = out.writeObject(F_FIELDS)) {
			for (String fn : config.getFieldNames()) {
				writeIDEConfigurationObject(fout, fn, config.getField(fn));
			}
		}
	}

	private static void writeIDEConfigurationObject(StructuredArrayObjectOutput out, Object val) throws IOException {
		if (val == null) {
			return;
		}
		if (val instanceof Collection) {
			try (StructuredArrayObjectOutput aout = out.writeArray()) {
				for (Object o : (Collection<?>) val) {
					writeIDEConfigurationObject(aout, o);
				}
			}
		} else if (val instanceof Map) {
			try (StructuredObjectOutput oout = out.writeObject()) {
				for (Entry<?, ?> entry : ((Map<?, ?>) val).entrySet()) {
					writeIDEConfigurationObject(oout, Objects.toString(entry.getKey()), entry.getValue());
				}
			}
//		} else if (val instanceof Boolean) {
//			out.write("Z" + ((Boolean) val).booleanValue());
//		} else if (val instanceof Character) {
//			out.write("C" + ((Character) val).charValue());
//		} else if (val instanceof Byte) {
//			out.write("B" + ((Byte) val).byteValue());
//		} else if (val instanceof Short) {
//			out.write("S" + ((Short) val).shortValue());
//		} else if (val instanceof Integer) {
//			out.write("I" + ((Integer) val).intValue());
//		} else if (val instanceof Long) {
//			out.write("J" + ((Long) val).longValue());
//		} else if (val instanceof Float) {
//			out.write("F" + ((Float) val).floatValue());
//		} else if (val instanceof Double) {
//			out.write("D" + ((Double) val).doubleValue());
//		} else {
//			//just write the string value
//			out.write("L" + val.toString());
//		}
		} else if (val instanceof Boolean) {
			out.write("B" + ((Boolean) val).booleanValue());
		} else if (val instanceof Character) {
			out.write("C" + ((Character) val).charValue());
		} else if (val instanceof Byte || val instanceof Short || val instanceof Integer || val instanceof Long) {
			out.write("I" + ((Number) val).longValue());
		} else if (val instanceof Float || val instanceof Double) {
			out.write("F" + ((Number) val).doubleValue());
		} else {
			//just write the string value
			out.write("S" + val.toString());
		}
	}

	private static void writeIDEConfigurationObject(StructuredObjectOutput out, String fn, Object val)
			throws IOException {
		if (val == null) {
			return;
		}
		if (val instanceof Collection) {
			try (StructuredArrayObjectOutput aout = out.writeArray("A" + fn)) {
				for (Object o : (Collection<?>) val) {
					writeIDEConfigurationObject(aout, o);
				}
			}
		} else if (val instanceof Map) {
			try (StructuredObjectOutput oout = out.writeObject("O" + fn)) {
				for (Entry<?, ?> entry : ((Map<?, ?>) val).entrySet()) {
					writeIDEConfigurationObject(oout, Objects.toString(entry.getKey()), entry.getValue());
				}
			}
		} else if (val instanceof Boolean) {
			out.writeField("B" + fn, ((Boolean) val).booleanValue());
		} else if (val instanceof Character) {
			out.writeField("C" + fn, ((Character) val).charValue());
		} else if (val instanceof Byte || val instanceof Short || val instanceof Integer || val instanceof Long) {
			out.writeField("I" + fn, ((Number) val).longValue());
		} else if (val instanceof Float || val instanceof Double) {
			out.writeField("F" + fn, ((Number) val).doubleValue());
		} else {
			//just write the string value
			out.writeField("S" + fn, val.toString());
		}
	}

	public static SimpleIDEConfiguration readIDEConfiguration(StructuredObjectInput in) throws IOException {
		String type = in.readString(F_TYPE);
		String id = in.readString(F_IDENTIFIER);
		NavigableMap<String, Object> fields = new TreeMap<>();
		try (StructuredObjectInput fin = in.readObject(F_FIELDS)) {
			if (fin != null) {
				readIDEConfigurationMap(fin, fields);
			}
		}
		return new SimpleIDEConfiguration(type, id, fields);
	}

	private static void readIDEConfigurationMap(StructuredObjectInput in, Map<String, Object> fields)
			throws IOException {
		for (String fn : in.getFields()) {
			if (fn.isEmpty()) {
				continue;
			}
			char t = fn.charAt(0);
			String realfname = fn.substring(1);
			switch (t) {
				case 'A': {
					try (StructuredArrayObjectInput ain = in.readArray(fn)) {
						Collection<Object> acoll = new ArrayList<>();
						readIDEConfigurationCollection(ain, acoll);
						fields.put(realfname, acoll);
					}
					break;
				}
				case 'O': {
					try (StructuredObjectInput oin = in.readObject(fn)) {
						Map<String, Object> ofields = new TreeMap<>();
						readIDEConfigurationMap(oin, ofields);
						fields.put(realfname, ofields);
					}
					break;
				}
				case 'B': {
					fields.put(realfname, in.readBoolean(fn));
					break;
				}
				case 'C': {
					fields.put(realfname, in.readChar(fn));
					break;
				}
				case 'I': {
					fields.put(realfname, in.readLong(fn));
					break;
				}
				case 'F': {
					fields.put(realfname, in.readDouble(fn));
					break;
				}
				case 'S': {
					fields.put(realfname, in.readString(fn));
					break;
				}
				default: {
					break;
				}
			}
		}
	}

	private static void readIDEConfigurationCollection(StructuredArrayObjectInput in, Collection<Object> coll)
			throws IOException {
		int len = in.length();
		for (int i = 0; i < len; i++) {
			try {
				StructuredDataType nexttype = in.getNextDataType();
				switch (nexttype) {
					case ARRAY: {
						try (StructuredArrayObjectInput ain = in.readArray()) {
							Collection<Object> acoll = new ArrayList<>();
							readIDEConfigurationCollection(ain, acoll);
							coll.add(acoll);
						}
						break;
					}
					case OBJECT: {
						try (StructuredObjectInput oin = in.readObject()) {
							Map<String, Object> fields = new TreeMap<>();
							readIDEConfigurationMap(oin, fields);
							coll.add(fields);
						}
						break;
					}
					case LITERAL: {
						String s = in.readString();
						if (s.isEmpty()) {
							break;
						}
						char t = s.charAt(0);
						switch (t) {
							case 'B': {
								coll.add(Boolean.valueOf(s.substring(1)));
								break;
							}
							case 'C': {
								if (s.length() > 1) {
									coll.add(Character.valueOf(s.charAt(1)));
								}
								break;
							}
							case 'I': {
								coll.add(Long.valueOf(s.substring(1)));
								break;
							}
							case 'F': {
								coll.add(Double.valueOf(s.substring(1)));
								break;
							}
							case 'S': {
								coll.add(s.substring(1));
								break;
							}
							default: {
								break;
							}
						}
						break;
					}
					default: {
						break;
					}
				}
			} catch (RuntimeException e) {
				//if any parsed data is invalid
			}
		}
	}

	public static void writeIDEProjectProperties(StructuredObjectOutput objout, IDEProjectProperties props)
			throws IOException {
		String workdir = props.getWorkingDirectory();
		String builddir = props.getBuildDirectory();
		String mirrordir = props.getMirrorDirectory();
		String execonnectionname = props.getExecutionDaemonConnectionName();
		MountPathIDEProperty buildtraceout = props.getBuildTraceOutput();
		objout.writeField(F_VERSION, 1);
		writeStringIfNotNull(objout, F_WORKING_DIR, workdir);
		writeStringIfNotNull(objout, F_BUILD_DIR, builddir);
		writeStringIfNotNull(objout, F_MIRROR_DIR, mirrordir);
		writeStringIfNotNull(objout, F_EXECUTION_DAEMON, execonnectionname);
		writeStringIfNotNull(objout, F_BUILD_TRACE_EMBED_ARTIFACTS, props.getBuildTraceEmbedArtifacts());
		if (buildtraceout != null) {
			String btcname = buildtraceout.getMountClientName();
			String btpath = buildtraceout.getMountPath();
			if (!ObjectUtils.isNullOrEmpty(btcname)) {
				objout.writeField(F_BUILD_TRACE_OUT_CLIENT, btcname);
			}
			if (!ObjectUtils.isNullOrEmpty(btpath)) {
				objout.writeField(F_BUILD_TRACE_OUT_PATH, btpath);
			}
		}
		writeStringIfNotNull(objout, F_REQUIRE_IDE_CONFIG, props.getRequireTaskIDEConfiguration());
		writeStringIfNotNull(objout, F_USE_CLIENTS_AS_CLUSTERS, props.getUseClientsAsClusters());
		Set<? extends RepositoryIDEProperty> repositories = props.getRepositories();
		if (!ObjectUtils.isNullOrEmpty(repositories)) {
			try (StructuredArrayObjectOutput repoarray = objout.writeArray(F_REPOSITORIES)) {
				for (RepositoryIDEProperty repo : repositories) {
					try (StructuredObjectOutput repoobj = repoarray.writeObject()) {
						String repoid = repo.getRepositoryIdentifier();
						ClassPathLocationIDEProperty location = repo.getClassPathLocation();
						ClassPathServiceEnumeratorIDEProperty enumerator = repo.getServiceEnumerator();
						if (repoid != null) {
							repoobj.writeField(F_REPO_ID, repoid);
						}
						writeClassPathProperty(repoobj, location, F_CLASSPATH);
						writeServiceEnumeratorProperty(repoobj, enumerator, F_SERVICE);
					}
				}
			}
		}
		Set<? extends Entry<String, String>> userparams = props.getUserParameters();
		if (!ObjectUtils.isNullOrEmpty(userparams)) {
			writeStringMap(objout, userparams, F_USER_PARAMETERS);
		}
		Set<? extends DaemonConnectionIDEProperty> connections = props.getConnections();
		if (!ObjectUtils.isNullOrEmpty(connections)) {
			try (StructuredArrayObjectOutput arraywriter = objout.writeArray(F_CONNECTIONS)) {
				for (DaemonConnectionIDEProperty conn : connections) {
					try (StructuredObjectOutput entryobj = arraywriter.writeObject()) {
						String address = conn.getNetAddress();
						String connectionname = conn.getConnectionName();
						writeStringIfNotNull(entryobj, F_ADDRESS, address);
						writeStringIfNotNull(entryobj, F_NAME, connectionname);
						entryobj.writeField(F_USE_AS_CLUSTER, conn.isUseAsCluster());
					}
				}
			}
		}
		Set<? extends ProviderMountIDEProperty> mounts = props.getMounts();
		if (!ObjectUtils.isNullOrEmpty(mounts)) {
			try (StructuredArrayObjectOutput arraywriter = objout.writeArray(F_MOUNTS)) {
				for (ProviderMountIDEProperty mount : mounts) {
					try (StructuredObjectOutput entryobj = arraywriter.writeObject()) {
						String root = mount.getRoot();
						MountPathIDEProperty mountpath = mount.getMountPathProperty();
						writeStringIfNotNull(entryobj, F_ROOT, root);
						if (mountpath != null) {
							String clientname = mountpath.getMountClientName();
							String mountpathstr = mountpath.getMountPath();
							writeStringIfNotNull(entryobj, F_CLIENT, clientname);
							writeStringIfNotNull(entryobj, F_PATH, mountpathstr);
						}
					}
				}
			}
		}
		Set<? extends ScriptConfigurationIDEProperty> scriptconfigs = props.getScriptConfigurations();
		if (!ObjectUtils.isNullOrEmpty(scriptconfigs)) {
			try (StructuredArrayObjectOutput arraywriter = objout.writeArray(F_SCRIPT_CONFIGS)) {
				for (ScriptConfigurationIDEProperty sc : scriptconfigs) {
					try (StructuredObjectOutput scobj = arraywriter.writeObject()) {
						ClassPathLocationIDEProperty cplocation = sc.getClassPathLocation();
						Set<? extends Entry<String, String>> scriptoptions = sc.getScriptOptions();
						String scriptwildcard = sc.getScriptsWildcard();
						ClassPathServiceEnumeratorIDEProperty scriptserviceenumerator = sc.getServiceEnumerator();
						writeStringIfNotNull(scobj, F_WILDCARD, scriptwildcard);
						writeStringMap(scobj, scriptoptions, F_OPTIONS);
						writeClassPathProperty(scobj, cplocation, F_CLASSPATH);
						writeServiceEnumeratorProperty(scobj, scriptserviceenumerator, F_SERVICE);
					}
				}
			}
		}
		Set<String> scriptmodellingexclusions = props.getScriptModellingExclusions();
		if (!ObjectUtils.isNullOrEmpty(scriptmodellingexclusions)) {
			try (StructuredArrayObjectOutput arraywriter = objout.writeArray(F_SCRIPT_MODELLING_EXCLUSIONS)) {
				for (String wildcard : scriptmodellingexclusions) {
					arraywriter.write(wildcard);
				}
			}
		}
		Set<? extends ParameterizedBuildTargetIDEProperty> parambuildtargets = props.getParameterizedBuildTargets();
		if (!ObjectUtils.isNullOrEmpty(parambuildtargets)) {
			try (StructuredArrayObjectOutput arraywriter = objout.writeArray(F_PARAMETERIZED_BUILD_TARGETS)) {
				for (ParameterizedBuildTargetIDEProperty parambuildtarget : parambuildtargets) {
					try (StructuredObjectOutput buildtargetparamobj = arraywriter.writeObject()) {
						writeStringIfNotNull(buildtargetparamobj, F_SCRIPT_PATH, parambuildtarget.getScriptPath());
						writeStringIfNotNull(buildtargetparamobj, F_TARGET_NAME, parambuildtarget.getTargetName());
						Map<String, String> paramentries = parambuildtarget.getBuildTargetParameters();
						if (!ObjectUtils.isNullOrEmpty(paramentries)) {
							try (StructuredArrayObjectOutput paramarrayobj = buildtargetparamobj
									.writeArray(F_PARAMETERS)) {
								for (Entry<String, String> entry : paramentries.entrySet()) {
									try (StructuredObjectOutput entryobj = paramarrayobj.writeObject()) {
										entryobj.writeField(F_NAME, entry.getKey());
										entryobj.writeField(F_VALUE, entry.getValue());
									}
								}
							}
						}
					}
				}
			}
		}
	}

	public static IDEProjectProperties readIDEProjectProperties(StructuredObjectInput input) throws IOException {
		SimpleIDEProjectProperties.Builder result = SimpleIDEProjectProperties.builder();

		Integer version = input.readInt(F_VERSION);
		//version is unhandled for now, as there's currently only one format. if new formats are introduced, it will
		// be checked and the values interpreted accordingly

		String workdirstr = input.readString(F_WORKING_DIR);
		if (workdirstr != null) {
			result.setWorkingDirectory(workdirstr);
		}
		result.setBuildDirectory(input.readString(F_BUILD_DIR));
		result.setMirrorDirectory(input.readString(F_MIRROR_DIR));
		result.setExecutionDaemonConnectionName(input.readString(F_EXECUTION_DAEMON));
		result.setRequireTaskIDEConfiguration(input.readString(F_REQUIRE_IDE_CONFIG));
		result.setUseClientsAsClusters(input.readString(F_USE_CLIENTS_AS_CLUSTERS));

		result.setBuildTraceOutput(MountPathIDEProperty.create(input.readString(F_BUILD_TRACE_OUT_CLIENT),
				input.readString(F_BUILD_TRACE_OUT_PATH)));
		result.setBuildTraceEmbedArtifacts(input.readString(F_BUILD_TRACE_EMBED_ARTIFACTS));

		try (StructuredArrayObjectInput array = input.readArray(F_REPOSITORIES)) {
			if (array != null) {
				int len = array.length();
				Set<RepositoryIDEProperty> repositories = new LinkedHashSet<>();
				for (int i = 0; i < len; i++) {
					try (StructuredObjectInput obj = array.readObject()) {
						String repoid = obj.readString(F_REPO_ID);
						ClassPathLocationIDEProperty cplocation = readClassPathProperty(obj, F_CLASSPATH);
						ClassPathServiceEnumeratorIDEProperty serviceenumerator = readServiceEnumeratorProperty(obj,
								F_SERVICE);
						if (serviceenumerator == null && cplocation == null && ObjectUtils.isNullOrEmpty(repoid)) {
							continue;
						}
						repositories.add(new RepositoryIDEProperty(cplocation, repoid, serviceenumerator));
					}
				}
				result.setRepositories(repositories);
			}
		}
		try (StructuredArrayObjectInput array = input.readArray(F_USER_PARAMETERS)) {
			if (array != null) {
				Set<Entry<String, String>> map = readStringMap(array);
				result.setUserParameters(map);
			}
		}
		try (StructuredArrayObjectInput array = input.readArray(F_CONNECTIONS)) {
			if (array != null) {
				Set<DaemonConnectionIDEProperty> connections = new LinkedHashSet<>();
				int len = array.length();
				for (int i = 0; i < len; i++) {
					try (StructuredObjectInput obj = array.readObject()) {
						String address = obj.readString(F_ADDRESS);
						String name = obj.readString(F_NAME);
						if (address == null && name == null) {
							continue;
						}
						Boolean userascluster = obj.readBoolean(F_USE_AS_CLUSTER);
						connections.add(new DaemonConnectionIDEProperty(address, name,
								ObjectUtils.defaultize(userascluster, false)));
					}
				}
				result.setConnections(connections);
			}
		}
		try (StructuredArrayObjectInput array = input.readArray(F_MOUNTS)) {
			if (array != null) {
				Set<ProviderMountIDEProperty> mounts = new LinkedHashSet<>();
				int len = array.length();
				for (int i = 0; i < len; i++) {
					try (StructuredObjectInput obj = array.readObject()) {
						String root = obj.readString(F_ROOT);
						String client = obj.readString(F_CLIENT);
						String path = obj.readString(F_PATH);
						if (root == null && client == null && path == null) {
							continue;
						}
						mounts.add(new ProviderMountIDEProperty(root, MountPathIDEProperty.create(client, path)));
					}
				}
				result.setMounts(mounts);
			}
		}
		try (StructuredArrayObjectInput array = input.readArray(F_SCRIPT_CONFIGS)) {
			if (array != null) {
				Set<ScriptConfigurationIDEProperty> scriptconfigs = new LinkedHashSet<>();
				int len = array.length();
				for (int i = 0; i < len; i++) {
					try (StructuredObjectInput optobj = array.readObject()) {
						Set<Entry<String, String>> options = Collections.emptySet();
						ClassPathLocationIDEProperty classpath = readClassPathProperty(optobj, F_CLASSPATH);
						try (StructuredArrayObjectInput optionsarray = optobj.readArray(F_OPTIONS)) {
							if (optionsarray != null) {
								options = readStringMap(optionsarray);
							}
						}
						String wildcard = optobj.readString(F_WILDCARD);
						ClassPathServiceEnumeratorIDEProperty serviceenumerator = readServiceEnumeratorProperty(optobj,
								F_SERVICE);
						if (options.isEmpty() && classpath == null && wildcard == null && serviceenumerator == null) {
							continue;
						}
						scriptconfigs.add(
								new ScriptConfigurationIDEProperty(wildcard, options, classpath, serviceenumerator));
					}
				}
				result.setScriptConfigurations(scriptconfigs);
			}
		}
		try (StructuredArrayObjectInput array = input.readArray(F_SCRIPT_MODELLING_EXCLUSIONS)) {
			if (array != null) {
				Set<String> scriptmodellingexclusions = new TreeSet<>();
				int len = array.length();
				for (int i = 0; i < len; i++) {
					try {
						String wcstr = array.readString();
						if (wcstr == null) {
							continue;
						}
						scriptmodellingexclusions.add(wcstr);
					} catch (DataFormatException | IllegalArgumentException e) {
					}
				}
				result.setScriptModellingExclusions(scriptmodellingexclusions);
			}
		}
		try (StructuredArrayObjectInput array = input.readArray(F_PARAMETERIZED_BUILD_TARGETS)) {
			if (array != null) {
				Set<ParameterizedBuildTargetIDEProperty> parambuildtargets = new LinkedHashSet<>();
				int len = array.length();
				for (int i = 0; i < len; i++) {
					try (StructuredObjectInput buildtargetobj = array.readObject()) {
						if (buildtargetobj == null) {
							continue;
						}
						String scriptpath = buildtargetobj.readString(F_SCRIPT_PATH);
						String targetname = buildtargetobj.readString(F_TARGET_NAME);
						NavigableMap<String, String> buildtargetparams = new TreeMap<>();
						try (StructuredArrayObjectInput paramsarray = buildtargetobj.readArray(F_PARAMETERS)) {
							if (paramsarray != null) {
								int paramslen = paramsarray.length();
								for (int j = 0; j < paramslen; j++) {
									try (StructuredObjectInput entryobj = paramsarray.readObject()) {
										if (entryobj == null) {
											continue;
										}
										String paramname = entryobj.readString(F_NAME);
										String paramvalue = entryobj.readString(F_VALUE);
										buildtargetparams.put(paramname, paramvalue);
									}
								}
							}
						}
						parambuildtargets.add(
								new ParameterizedBuildTargetIDEProperty(scriptpath, targetname, buildtargetparams));
					} catch (DataFormatException | IllegalArgumentException e) {
					}
				}
				result.setParameterizedBuildTargets(parambuildtargets);
			}
		}
		return result.build();
	}

	private static void writeClassPathProperty(StructuredObjectOutput objout, ClassPathLocationIDEProperty location,
			String name) throws IOException {
		if (location == null) {
			return;
		}
		try (StructuredObjectOutput cpobj = objout.writeObject(name)) {
			location.accept(new ClassPathLocationObjectStructuredSerializer(cpobj), null);
		}
	}

	private static ClassPathLocationIDEProperty readClassPathProperty(StructuredObjectInput obj, String name)
			throws IOException {
		ClassPathLocationIDEProperty cplocation = null;
		try (StructuredObjectInput cp = obj.readObject(name)) {
			if (cp != null) {
				String type = cp.readString(F_TYPE);
				if (type != null) {
					switch (type) {
						case CP_JAR: {
							String path = cp.readString(F_PATH);
							String client = cp.readString(F_CONNECTION);
							cplocation = new JarClassPathLocationIDEProperty(client, path);
							break;
						}
						case CP_HTTP_URL: {
							String urlstr = cp.readString(F_URL);
							cplocation = new HttpUrlJarClassPathLocationIDEProperty(urlstr);
							break;
						}
						case CP_BUILTIN_SCRIPTING_CLASSPATH: {
							cplocation = BuiltinScriptingLanguageClassPathLocationIDEProperty.INSTANCE;
							break;
						}
						case CP_NEST_REPOSITORY_CLASSPATH: {
							String version = cp.readString(F_VERSION);
							if (version != null) {
								cplocation = new NestRepositoryClassPathLocationIDEProperty(version);
							} else {
								cplocation = new NestRepositoryClassPathLocationIDEProperty();
							}
							break;
						}
						default: {
							break;
						}
					}
				}
			}
		}
		return cplocation;
	}

	private static void writeServiceEnumeratorProperty(StructuredObjectOutput objout,
			ClassPathServiceEnumeratorIDEProperty enumerator, String name) throws IOException {
		if (enumerator == null) {
			return;
		}
		try (StructuredObjectOutput cpobj = objout.writeObject(name)) {
			enumerator.accept(new ServiceEnumeratorObjectStructuredSerializer(cpobj), null);
		}
	}

	private static ClassPathServiceEnumeratorIDEProperty readServiceEnumeratorProperty(StructuredObjectInput obj,
			String name) throws IOException {
		ClassPathServiceEnumeratorIDEProperty serviceenumerator = null;
		try (StructuredObjectInput c = obj.readObject(name)) {
			if (c != null) {
				String type = c.readString(F_TYPE);
				if (type != null) {
					switch (type) {
						case SP_SERVICELOADER: {
							String serviceclassname = c.readString(F_SERVICE_CLASS);
							serviceenumerator = new ServiceLoaderClassPathEnumeratorIDEProperty(serviceclassname);
							break;
						}
						case SP_CLASS_NAME: {
							String serviceclassname = c.readString(F_CLASS_NAME);
							serviceenumerator = new NamedClassClassPathServiceEnumeratorIDEProperty(serviceclassname);
							break;
						}
						case SP_BUILTIN_SCRIPTING_SERVICE: {
							return BuiltinScriptingLanguageServiceEnumeratorIDEProperty.INSTANCE;
						}
						case SP_NEST_REPOSITORY_SERVICE: {
							return NestRepositoryFactoryServiceEnumeratorIDEProperty.INSTANCE;
						}
						default: {
							break;
						}
					}
				}
			}
		}
		return serviceenumerator;
	}

	private static void writeStringMap(StructuredObjectOutput objout, Set<? extends Entry<String, String>> entries,
			String mapfieldname) throws IOException {
		if (entries == null) {
			return;
		}
		try (StructuredArrayObjectOutput arraywriter = objout.writeArray(mapfieldname)) {
			for (Entry<String, String> entry : entries) {
				try (StructuredObjectOutput entryobj = arraywriter.writeObject()) {
					String key = entry.getKey();
					String val = entry.getValue();
					if (key != null) {
						entryobj.writeField(F_KEY, key);
					}
					if (val != null) {
						entryobj.writeField(F_VAL, val);
					}
				}
			}
		}
	}

	private static Set<Entry<String, String>> readStringMap(StructuredArrayObjectInput array) throws IOException {
		int len = array.length();
		Set<Entry<String, String>> map = new LinkedHashSet<>();
		for (int i = 0; i < len; i++) {
			try (StructuredObjectInput obj = array.readObject()) {
				String key = obj.readString(F_KEY);
				String value = obj.readString(F_VAL);
				if (key == null && value == null) {
					continue;
				}
				map.add(ImmutableUtils.makeImmutableMapEntry(key, value));
			}
		}
		return map;
	}

	private static void writeStringIfNotNull(StructuredObjectOutput output, String fieldname, String value)
			throws DuplicateObjectFieldException, IOException {
		if (value == null) {
			return;
		}
		output.writeField(fieldname, value);
	}

	private static final class ServiceEnumeratorObjectStructuredSerializer
			implements ClassPathServiceEnumeratorIDEProperty.Visitor<Void, Void> {
		private final StructuredObjectOutput cpobj;

		private ServiceEnumeratorObjectStructuredSerializer(StructuredObjectOutput cpobj) {
			this.cpobj = cpobj;
		}

		@Override
		public Void visit(ServiceLoaderClassPathEnumeratorIDEProperty property, Void param) {
			try {
				cpobj.writeField(F_TYPE, SP_SERVICELOADER);
				String serviceclass = property.getServiceClass();
				if (!ObjectUtils.isNullOrEmpty(serviceclass)) {
					cpobj.writeField(F_SERVICE_CLASS, serviceclass);
				}
			} catch (IOException e) {
				throw ObjectUtils.sneakyThrow(e);
			}
			return null;
		}

		@Override
		public Void visit(NamedClassClassPathServiceEnumeratorIDEProperty property, Void param) {
			try {
				cpobj.writeField(F_TYPE, SP_CLASS_NAME);
				String classname = property.getClassName();
				if (!ObjectUtils.isNullOrEmpty(classname)) {
					cpobj.writeField(F_CLASS_NAME, classname);
				}
			} catch (IOException e) {
				throw ObjectUtils.sneakyThrow(e);
			}
			return null;
		}

		@Override
		public Void visit(BuiltinScriptingLanguageServiceEnumeratorIDEProperty property, Void param) {
			try {
				cpobj.writeField(F_TYPE, SP_BUILTIN_SCRIPTING_SERVICE);
			} catch (IOException e) {
				throw ObjectUtils.sneakyThrow(e);
			}
			return null;
		}

		@Override
		public Void visit(NestRepositoryFactoryServiceEnumeratorIDEProperty property, Void param) {
			try {
				cpobj.writeField(F_TYPE, SP_NEST_REPOSITORY_SERVICE);
			} catch (IOException e) {
				throw ObjectUtils.sneakyThrow(e);
			}
			return null;
		}
	}

	private static final class ClassPathLocationObjectStructuredSerializer
			implements ClassPathLocationIDEProperty.Visitor<Void, Void> {
		private final StructuredObjectOutput cpobj;

		private ClassPathLocationObjectStructuredSerializer(StructuredObjectOutput cpobj) {
			this.cpobj = cpobj;
		}

		@Override
		public Void visit(JarClassPathLocationIDEProperty property, Void param) {
			try {
				cpobj.writeField(F_TYPE, CP_JAR);
				String jarpath = property.getJarPath();
				if (!ObjectUtils.isNullOrEmpty(jarpath)) {
					cpobj.writeField(F_PATH, jarpath);
				}
				String connname = property.getConnectionName();
				if (!ObjectUtils.isNullOrEmpty(connname)) {
					cpobj.writeField(F_CONNECTION, connname);
				}
			} catch (IOException e) {
				throw ObjectUtils.sneakyThrow(e);
			}
			return null;
		}

		@Override
		public Void visit(HttpUrlJarClassPathLocationIDEProperty property, Void param) {
			try {
				cpobj.writeField(F_TYPE, CP_HTTP_URL);
				String url = property.getUrl();
				if (!ObjectUtils.isNullOrEmpty(url)) {
					cpobj.writeField(F_URL, url);
				}
			} catch (IOException e) {
				throw ObjectUtils.sneakyThrow(e);
			}
			return null;
		}

		@Override
		public Void visit(BuiltinScriptingLanguageClassPathLocationIDEProperty property, Void param) {
			try {
				cpobj.writeField(F_TYPE, CP_BUILTIN_SCRIPTING_CLASSPATH);
			} catch (DuplicateObjectFieldException | IOException e) {
				throw ObjectUtils.sneakyThrow(e);
			}
			return null;
		}

		@Override
		public Void visit(NestRepositoryClassPathLocationIDEProperty property, Void param) {
			try {
				cpobj.writeField(F_TYPE, CP_NEST_REPOSITORY_CLASSPATH);
				String ver = property.getVersion();
				if (ver != null) {
					cpobj.writeField(F_VERSION, ver);
				}
			} catch (DuplicateObjectFieldException | IOException e) {
				throw ObjectUtils.sneakyThrow(e);
			}
			return null;
		}
	}

}
