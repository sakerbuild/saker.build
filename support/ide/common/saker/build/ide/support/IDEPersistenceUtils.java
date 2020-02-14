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

import saker.build.exception.InvalidPathFormatException;
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
import saker.build.ide.support.properties.ProviderMountIDEProperty;
import saker.build.ide.support.properties.RepositoryIDEProperty;
import saker.build.ide.support.properties.ScriptConfigurationIDEProperty;
import saker.build.ide.support.properties.ServiceLoaderClassPathEnumeratorIDEProperty;
import saker.build.ide.support.properties.SimpleIDEProjectProperties;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class IDEPersistenceUtils {
//XXX make persisting data exception proof
	private IDEPersistenceUtils() {
		throw new UnsupportedOperationException();
	}

	public static void writeIDEPluginProperties(StructuredObjectOutput out, IDEPluginProperties props)
			throws IOException {
		out.writeField("version", 1);
		String storagedir = props.getStorageDirectory();
		if (storagedir != null) {
			out.writeField("storage_directory", storagedir);
		}
		Set<? extends Entry<String, String>> userparams = props.getUserParameters();
		writeStringMap(out, userparams, "user_parameters");
	}

	public static IDEPluginProperties readIDEPluginProperties(StructuredObjectInput input) throws IOException {
		SimpleIDEPluginProperties.Builder result = SimpleIDEPluginProperties.builder();

		Integer version = input.readInt("version");
		//version is unhandled for now, as there's currently only one format. if new formats are introduced, it will
		// be checked and the values interpreted accordingly

		String strdir = input.readString("storage_directory");
		if (strdir != null) {
			try {
				result.setStorageDirectory(strdir);
			} catch (InvalidPathFormatException e) {
				//if the path is invalid
			}
		}
		try (StructuredArrayObjectInput array = input.readArray("user_parameters")) {
			if (array != null) {
				Set<Entry<String, String>> map = readStringMap(array);
				result.setUserParameters(map);
			}
		}

		return result.build();
	}

	public static void writeIDEConfiguration(StructuredObjectOutput out, IDEConfiguration config) throws IOException {
		out.writeField("type", config.getType());
		out.writeField("identifier", config.getIdentifier());

		try (StructuredObjectOutput fout = out.writeObject("fields")) {
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
			out.write("I" + ((Integer) val).intValue());
		} else if (val instanceof Float || val instanceof Double) {
			out.write("F" + ((Float) val).floatValue());
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
			out.writeField("I" + fn, ((Integer) val).intValue());
		} else if (val instanceof Float || val instanceof Double) {
			out.writeField("F" + fn, ((Float) val).floatValue());
		} else {
			//just write the string value
			out.writeField("S" + fn, val.toString());
		}
	}

	public static SimpleIDEConfiguration readIDEConfiguration(StructuredObjectInput in) throws IOException {
		String type = in.readString("type");
		String id = in.readString("identifier");
		NavigableMap<String, Object> fields = new TreeMap<>();
		try (StructuredObjectInput fin = in.readObject("fields")) {
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
		objout.writeField("version", 1);
		if (workdir != null) {
			objout.writeField("working_dir", workdir);
		}
		if (builddir != null) {
			objout.writeField("build_dir", builddir);
		}
		if (mirrordir != null) {
			objout.writeField("mirror_dir", mirrordir);
		}
		if (execonnectionname != null) {
			objout.writeField("execution_daemon", execonnectionname);
		}
		if (buildtraceout != null) {
			String btcname = buildtraceout.getMountClientName();
			String btpath = buildtraceout.getMountPath();
			if (btcname != null) {
				objout.writeField("build_trace_out_client", btcname);
			}
			if (btpath != null) {
				objout.writeField("build_trace_out_path", btpath);
			}
		}
		objout.writeField("require_ide_config", props.isRequireTaskIDEConfiguration());
		Set<? extends RepositoryIDEProperty> repositories = props.getRepositories();
		if (repositories != null) {
			try (StructuredArrayObjectOutput repoarray = objout.writeArray("repositories")) {
				for (RepositoryIDEProperty repo : repositories) {
					try (StructuredObjectOutput repoobj = repoarray.writeObject()) {
						String repoid = repo.getRepositoryIdentifier();
						ClassPathLocationIDEProperty location = repo.getClassPathLocation();
						ClassPathServiceEnumeratorIDEProperty enumerator = repo.getServiceEnumerator();
						if (repoid != null) {
							repoobj.writeField("repo_id", repoid);
						}
						writeClassPathProperty(repoobj, location, "classpath");
						writeServiceEnumeratorProperty(repoobj, enumerator, "service");
					}
				}
			}
		}
		Set<? extends Entry<String, String>> userparams = props.getUserParameters();
		writeStringMap(objout, userparams, "user_parameters");
		Set<? extends DaemonConnectionIDEProperty> connections = props.getConnections();
		if (connections != null) {
			try (StructuredArrayObjectOutput arraywriter = objout.writeArray("connections")) {
				for (DaemonConnectionIDEProperty conn : connections) {
					try (StructuredObjectOutput entryobj = arraywriter.writeObject()) {
						String address = conn.getNetAddress();
						String connectionname = conn.getConnectionName();
						if (address != null) {
							entryobj.writeField("address", address);
						}
						if (connectionname != null) {
							entryobj.writeField("name", connectionname);
						}
						entryobj.writeField("use_as_cluster", conn.isUseAsCluster());
					}
				}
			}
		}
		Set<? extends ProviderMountIDEProperty> mounts = props.getMounts();
		if (mounts != null) {
			try (StructuredArrayObjectOutput arraywriter = objout.writeArray("mounts")) {
				for (ProviderMountIDEProperty mount : mounts) {
					try (StructuredObjectOutput entryobj = arraywriter.writeObject()) {
						String root = mount.getRoot();
						MountPathIDEProperty mountpath = mount.getMountPathProperty();
						if (root != null) {
							entryobj.writeField("root", root);
						}
						if (mountpath != null) {
							String clientname = mountpath.getMountClientName();
							String mountpathstr = mountpath.getMountPath();
							if (clientname != null) {
								entryobj.writeField("client", clientname);
							}
							if (mountpathstr != null) {
								entryobj.writeField("path", mountpathstr);
							}
						}
					}
				}
			}
		}
		Set<? extends ScriptConfigurationIDEProperty> scriptconfigs = props.getScriptConfigurations();
		if (scriptconfigs != null) {
			try (StructuredArrayObjectOutput arraywriter = objout.writeArray("script_configs")) {
				for (ScriptConfigurationIDEProperty sc : scriptconfigs) {
					try (StructuredObjectOutput scobj = arraywriter.writeObject()) {
						ClassPathLocationIDEProperty cplocation = sc.getClassPathLocation();
						Set<? extends Entry<String, String>> scriptoptions = sc.getScriptOptions();
						String scriptwildcard = sc.getScriptsWildcard();
						ClassPathServiceEnumeratorIDEProperty scriptserviceenumerator = sc.getServiceEnumerator();
						if (scriptwildcard != null) {
							scobj.writeField("wildcard", scriptwildcard);
						}
						writeStringMap(scobj, scriptoptions, "options");
						writeClassPathProperty(scobj, cplocation, "classpath");
						writeServiceEnumeratorProperty(scobj, scriptserviceenumerator, "service");
					}
				}
			}
		}
		Set<String> scriptmodellingexclusions = props.getScriptModellingExclusions();
		if (scriptmodellingexclusions != null) {
			try (StructuredArrayObjectOutput arraywriter = objout.writeArray("script_modelling_exclusions")) {
				for (String wildcard : scriptmodellingexclusions) {
					arraywriter.write(wildcard);
				}
			}
		}
	}

	public static IDEProjectProperties readIDEProjectProperties(StructuredObjectInput input) throws IOException {
		SimpleIDEProjectProperties.Builder result = SimpleIDEProjectProperties.builder();

		Integer version = input.readInt("version");
		//version is unhandled for now, as there's currently only one format. if new formats are introduced, it will
		// be checked and the values interpreted accordingly

		String workdirstr = input.readString("working_dir");
		if (workdirstr != null) {
			result.setWorkingDirectory(workdirstr);
		}
		result.setBuildDirectory(input.readString("build_dir"));
		result.setMirrorDirectory(input.readString("mirror_dir"));
		result.setExecutionDaemonConnectionName(input.readString("execution_daemon"));
		try {
			result.setRequireTaskIDEConfiguration(
					ObjectUtils.defaultize(input.readBoolean("require_ide_config"), true));
		} catch (DataFormatException ignored) {
		}
		result.setBuildTraceOutput(MountPathIDEProperty.create(input.readString("build_trace_out_client"),
				input.readString("build_trace_out_path")));

		try (StructuredArrayObjectInput array = input.readArray("repositories")) {
			if (array != null) {
				int len = array.length();
				Set<RepositoryIDEProperty> repositories = new LinkedHashSet<>();
				for (int i = 0; i < len; i++) {
					try (StructuredObjectInput obj = array.readObject()) {
						String repoid = obj.readString("repo_id");
						ClassPathLocationIDEProperty cplocation = readClassPathProperty(obj, "classpath");
						ClassPathServiceEnumeratorIDEProperty serviceenumerator = readServiceEnumeratorProperty(obj,
								"service");
						if (serviceenumerator == null && cplocation == null && ObjectUtils.isNullOrEmpty(repoid)) {
							continue;
						}
						repositories.add(new RepositoryIDEProperty(cplocation, repoid, serviceenumerator));
					}
				}
				result.setRepositories(repositories);
			}
		}
		try (StructuredArrayObjectInput array = input.readArray("user_parameters")) {
			if (array != null) {
				Set<Entry<String, String>> map = readStringMap(array);
				result.setUserParameters(map);
			}
		}
		try (StructuredArrayObjectInput array = input.readArray("connections")) {
			if (array != null) {
				Set<DaemonConnectionIDEProperty> connections = new LinkedHashSet<>();
				int len = array.length();
				for (int i = 0; i < len; i++) {
					try (StructuredObjectInput obj = array.readObject()) {
						String address = obj.readString("address");
						String name = obj.readString("name");
						if (address == null && name == null) {
							continue;
						}
						Boolean userascluster = obj.readBoolean("use_as_cluster");
						connections.add(new DaemonConnectionIDEProperty(address, name,
								ObjectUtils.defaultize(userascluster, false)));
					}
				}
				result.setConnections(connections);
			}
		}
		try (StructuredArrayObjectInput array = input.readArray("mounts")) {
			if (array != null) {
				Set<ProviderMountIDEProperty> mounts = new LinkedHashSet<>();
				int len = array.length();
				for (int i = 0; i < len; i++) {
					try (StructuredObjectInput obj = array.readObject()) {
						String root = obj.readString("root");
						String client = obj.readString("client");
						String path = obj.readString("path");
						if (root == null && client == null && path == null) {
							continue;
						}
						mounts.add(new ProviderMountIDEProperty(root, MountPathIDEProperty.create(client, path)));
					}
				}
				result.setMounts(mounts);
			}
		}
		try (StructuredArrayObjectInput array = input.readArray("script_configs")) {
			if (array != null) {
				Set<ScriptConfigurationIDEProperty> scriptconfigs = new LinkedHashSet<>();
				int len = array.length();
				for (int i = 0; i < len; i++) {
					try (StructuredObjectInput optobj = array.readObject()) {
						Set<Entry<String, String>> options = Collections.emptySet();
						ClassPathLocationIDEProperty classpath = readClassPathProperty(optobj, "classpath");
						try (StructuredArrayObjectInput optionsarray = optobj.readArray("options")) {
							if (optionsarray != null) {
								options = readStringMap(optionsarray);
							}
						}
						String wildcard = optobj.readString("wildcard");
						ClassPathServiceEnumeratorIDEProperty serviceenumerator = readServiceEnumeratorProperty(optobj,
								"service");
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
		try (StructuredArrayObjectInput array = input.readArray("script_modelling_exclusions")) {
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
				String type = cp.readString("type");
				if (type != null) {
					switch (type) {
						case "jar": {
							String path = cp.readString("path");
							String client = cp.readString("connection");
							cplocation = new JarClassPathLocationIDEProperty(client, path);
							break;
						}
						case "http-url": {
							String urlstr = cp.readString("url");
							cplocation = new HttpUrlJarClassPathLocationIDEProperty(urlstr);
							break;
						}
						case "builtin-scripting-classpath": {
							cplocation = new BuiltinScriptingLanguageClassPathLocationIDEProperty();
							break;
						}
						case "nest-repository-classpath": {
							String version = cp.readString("version");
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
				String type = c.readString("type");
				if (type != null) {
					switch (type) {
						case "serviceloader": {
							String serviceclassname = c.readString("service_class");
							serviceenumerator = new ServiceLoaderClassPathEnumeratorIDEProperty(serviceclassname);
							break;
						}
						case "class-name": {
							String serviceclassname = c.readString("class_name");
							serviceenumerator = new NamedClassClassPathServiceEnumeratorIDEProperty(serviceclassname);
							break;
						}
						case "builtin-scripting-service": {
							return new BuiltinScriptingLanguageServiceEnumeratorIDEProperty();
						}
						case "nest-repository-service": {
							return new NestRepositoryFactoryServiceEnumeratorIDEProperty();
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
						entryobj.writeField("key", key);
					}
					if (val != null) {
						entryobj.writeField("val", val);
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
				String key = obj.readString("key");
				String value = obj.readString("val");
				if (key == null && value == null) {
					continue;
				}
				map.add(ImmutableUtils.makeImmutableMapEntry(key, value));
			}
		}
		return map;
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
				cpobj.writeField("type", "serviceloader");
				String serviceclass = property.getServiceClass();
				if (!ObjectUtils.isNullOrEmpty(serviceclass)) {
					cpobj.writeField("service_class", serviceclass);
				}
			} catch (IOException e) {
				throw ObjectUtils.sneakyThrow(e);
			}
			return null;
		}

		@Override
		public Void visit(NamedClassClassPathServiceEnumeratorIDEProperty property, Void param) {
			try {
				cpobj.writeField("type", "class-name");
				String classname = property.getClassName();
				if (!ObjectUtils.isNullOrEmpty(classname)) {
					cpobj.writeField("class_name", classname);
				}
			} catch (IOException e) {
				throw ObjectUtils.sneakyThrow(e);
			}
			return null;
		}

		@Override
		public Void visit(BuiltinScriptingLanguageServiceEnumeratorIDEProperty property, Void param) {
			try {
				cpobj.writeField("type", "builtin-scripting-service");
			} catch (IOException e) {
				throw ObjectUtils.sneakyThrow(e);
			}
			return null;
		}

		@Override
		public Void visit(NestRepositoryFactoryServiceEnumeratorIDEProperty property, Void param) {
			try {
				cpobj.writeField("type", "nest-repository-service");
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
				cpobj.writeField("type", "jar");
				String jarpath = property.getJarPath();
				if (!ObjectUtils.isNullOrEmpty(jarpath)) {
					cpobj.writeField("path", jarpath);
				}
				String connname = property.getConnectionName();
				if (!ObjectUtils.isNullOrEmpty(connname)) {
					cpobj.writeField("connection", connname);
				}
			} catch (IOException e) {
				throw ObjectUtils.sneakyThrow(e);
			}
			return null;
		}

		@Override
		public Void visit(HttpUrlJarClassPathLocationIDEProperty property, Void param) {
			try {
				cpobj.writeField("type", "http-url");
				String url = property.getUrl();
				if (!ObjectUtils.isNullOrEmpty(url)) {
					cpobj.writeField("url", url);
				}
			} catch (IOException e) {
				throw ObjectUtils.sneakyThrow(e);
			}
			return null;
		}

		@Override
		public Void visit(BuiltinScriptingLanguageClassPathLocationIDEProperty property, Void param) {
			try {
				cpobj.writeField("type", "builtin-scripting-classpath");
			} catch (DuplicateObjectFieldException | IOException e) {
				throw ObjectUtils.sneakyThrow(e);
			}
			return null;
		}

		@Override
		public Void visit(NestRepositoryClassPathLocationIDEProperty property, Void param) {
			try {
				cpobj.writeField("type", "nest-repository-classpath");
				String ver = property.getVersion();
				if (ver != null) {
					cpobj.writeField("version", ver);
				}
			} catch (DuplicateObjectFieldException | IOException e) {
				throw ObjectUtils.sneakyThrow(e);
			}
			return null;
		}
	}

}
