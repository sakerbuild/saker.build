package testing.saker.idesupport;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import saker.build.ide.support.IDEPersistenceUtils;
import saker.build.ide.support.persist.StructuredObjectInput;
import saker.build.ide.support.persist.StructuredObjectOutput;
import saker.build.ide.support.persist.XMLStructuredReader;
import saker.build.ide.support.persist.XMLStructuredWriter;
import saker.build.ide.support.properties.BuiltinScriptingLanguageClassPathLocationIDEProperty;
import saker.build.ide.support.properties.BuiltinScriptingLanguageServiceEnumeratorIDEProperty;
import saker.build.ide.support.properties.DaemonConnectionIDEProperty;
import saker.build.ide.support.properties.HttpUrlJarClassPathLocationIDEProperty;
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
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
public class IDESerializationTest extends SakerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		testSerial(SimpleIDEProjectProperties.getDefaultsInstance());

		//Note: the following are bogus values, they are just filled in random manner to test the serialization
		//		of the values and their equality
		SimpleIDEProjectProperties.Builder builder = SimpleIDEProjectProperties.builder();
		builder.setRepositories(ImmutableUtils.makeImmutableHashSet(new RepositoryIDEProperty[] {

				new RepositoryIDEProperty(BuiltinScriptingLanguageClassPathLocationIDEProperty.INSTANCE, "t1",
						BuiltinScriptingLanguageServiceEnumeratorIDEProperty.INSTANCE),

				new RepositoryIDEProperty(new JarClassPathLocationIDEProperty("project", "abc.jar"), "t2",
						new NamedClassClassPathServiceEnumeratorIDEProperty("test.ClassName")),

				new RepositoryIDEProperty(new JarClassPathLocationIDEProperty("project", "abc.jar"), "t3",
						NestRepositoryFactoryServiceEnumeratorIDEProperty.INSTANCE),

				new RepositoryIDEProperty(new JarClassPathLocationIDEProperty("project", "abc.jar"), "t4",
						new ServiceLoaderClassPathEnumeratorIDEProperty("test.ClassName2")),

				new RepositoryIDEProperty(new HttpUrlJarClassPathLocationIDEProperty("http://example.com/test.jar"),
						"t5", new NamedClassClassPathServiceEnumeratorIDEProperty("test.ClassName")),

				new RepositoryIDEProperty(new NestRepositoryClassPathLocationIDEProperty("0.1.2.3"), "t6",
						BuiltinScriptingLanguageServiceEnumeratorIDEProperty.INSTANCE),

		}));

		TreeMap<String, String> userparamsmap = new TreeMap<>();
		userparamsmap.put("p1", "v1");
		userparamsmap.put("p2", "v2");
		userparamsmap.put("p3", "");
		userparamsmap.put("", "v4");
		builder.setUserParameters(userparamsmap.entrySet());

		builder.setConnections(ImmutableUtils.makeImmutableHashSet(new DaemonConnectionIDEProperty[] {

				new DaemonConnectionIDEProperty("netadd", "connname", true),

				new DaemonConnectionIDEProperty("netadd2", "connname2", false),

		}));

		builder.setMounts(ImmutableUtils.makeImmutableHashSet(new ProviderMountIDEProperty[] {

				new ProviderMountIDEProperty("mountroot", new MountPathIDEProperty("clientname", "the/path")),

				new ProviderMountIDEProperty("mountroot2", new MountPathIDEProperty("clientname2", "the/path2")),

		}));

		builder.setScriptConfigurations(ImmutableUtils.makeImmutableHashSet(new ScriptConfigurationIDEProperty[] {

				new ScriptConfigurationIDEProperty("**/*.ext1", userparamsmap.entrySet(),
						BuiltinScriptingLanguageClassPathLocationIDEProperty.INSTANCE,
						BuiltinScriptingLanguageServiceEnumeratorIDEProperty.INSTANCE),

				new ScriptConfigurationIDEProperty("**/*.ext2", userparamsmap.entrySet(),
						new JarClassPathLocationIDEProperty("project", "abc.jar"),
						new NamedClassClassPathServiceEnumeratorIDEProperty("test.ClassName")),

				new ScriptConfigurationIDEProperty("**/*.ext3", userparamsmap.entrySet(),
						new HttpUrlJarClassPathLocationIDEProperty("http://example.com/test.jar"),
						new NamedClassClassPathServiceEnumeratorIDEProperty("test.ClassName2")),

		}));

		builder.setWorkingDirectory("wd:/some/directory");
		builder.setBuildDirectory("build");
		builder.setMirrorDirectory("/mirror/dir");
		builder.setExecutionDaemonConnectionName("connname");
		builder.setScriptModellingExclusions(
				ImmutableUtils.makeImmutableNavigableSet(new String[] { "abc/*.ext", "other/*.ext" }));

		builder.setParameterizedBuildTargets(
				ImmutableUtils.makeImmutableHashSet(new ParameterizedBuildTargetIDEProperty[] {

						new ParameterizedBuildTargetIDEProperty("script/path.ext", "build"),

						new ParameterizedBuildTargetIDEProperty("script/path.ext", "build2"),

						new ParameterizedBuildTargetIDEProperty("script/path.ext", "build3", userparamsmap),

				}));

		builder.setRequireTaskIDEConfiguration(true);
		builder.setBuildTraceOutput(new MountPathIDEProperty("mountClientName", "mountpath"));
		builder.setBuildTraceEmbedArtifacts(true);
		builder.setUseClientsAsClusters(true);

		testSerial(builder.build());
	}

	private static void testSerial(IDEProjectProperties props) throws IOException {
		UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream();
		try (XMLStructuredWriter writer = new XMLStructuredWriter(baos);
				StructuredObjectOutput objwriter = writer.writeObject("root")) {
			IDEPersistenceUtils.writeIDEProjectProperties(objwriter, props);
		}

		UnsyncByteArrayInputStream bais = new UnsyncByteArrayInputStream(baos.toByteArrayRegion());

		XMLStructuredReader reader = new XMLStructuredReader(bais);
		try (StructuredObjectInput objreader = reader.readObject("root")) {
			IDEProjectProperties readback = IDEPersistenceUtils.readIDEProjectProperties(objreader);
			assertEquals(props, readback, () -> baos.toString());
		}
	}

}
