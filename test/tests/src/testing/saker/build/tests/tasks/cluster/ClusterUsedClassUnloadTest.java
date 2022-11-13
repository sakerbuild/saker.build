package testing.saker.build.tests.tasks.cluster;

import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.sun.management.HotSpotDiagnosticMXBean;

import saker.build.daemon.LocalDaemonEnvironment;
import saker.build.file.path.SakerPath;
import saker.build.file.path.SimpleProviderHolderPathKey;
import saker.build.file.path.WildcardPath;
import saker.build.runtime.classpath.JarFileClassPathLocation;
import saker.build.runtime.classpath.NamedClassPathServiceEnumerator;
import saker.build.runtime.params.ExecutionScriptConfiguration;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptOptionsConfig;
import saker.build.runtime.params.ExecutionScriptConfiguration.ScriptProviderLocation;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.rmi.connection.RMIVariables;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.build.tests.TestClusterNameExecutionEnvironmentSelector;
import testing.saker.build.tests.tasks.factories.StringTaskFactory;
import testing.saker.build.tests.tasks.repo.RepositoryTestUtils;
import testing.saker.build.tests.tasks.script.customlang.CustomBuildTargetTaskFactory;
import testing.saker.build.tests.tasks.script.customlang.CustomScriptAccessProvider;
import testing.saker.build.tests.tasks.script.customlang.CustomScriptLanguageTest;
import testing.saker.build.tests.tasks.script.customlang.CustomTargetConfiguration;
import testing.saker.build.tests.tasks.script.customlang.CustomTargetConfigurationReader;

/**
 * Tests that the custom classes that were used for a build can be unloaded after the build finishes.
 * <p>
 * This is to check that the cluster environments don't keep an unnecessary reference to the build classes, so they
 * don't leak.
 * <p>
 * Before this improvement, this was caused by the {@link RMIVariables} keeping references to the used classes.
 */
@SakerTest
public class ClusterUsedClassUnloadTest extends CollectingMetricEnvironmentTestCase {
	//test code similar to ClusterCustomScriptLanguageTest

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath jarpath = PATH_WORKING_DIRECTORY
				.resolve(CustomScriptLanguageTest.createScriptLangJarName(getClass()));
		RepositoryTestUtils.exportJarWithClasses(files, jarpath, CustomBuildTargetTaskFactory.class,
				CustomScriptAccessProvider.class, CustomTargetConfiguration.class,
				CustomTargetConfigurationReader.class, StringTaskFactory.class,
				TestClusterNameExecutionEnvironmentSelector.class);

		JarFileClassPathLocation jarclasspathlocation = new JarFileClassPathLocation(
				new SimpleProviderHolderPathKey(files, jarpath));

		ExecutionScriptConfiguration.Builder builder = ExecutionScriptConfiguration.builder();
		builder.addConfig(WildcardPath.valueOf("**/*.customlang"),
				new ScriptOptionsConfig(null, new ScriptProviderLocation(jarclasspathlocation,
						new NamedClassPathServiceEnumerator<>(CustomScriptAccessProvider.class.getName()))));
		builder.addConfig(WildcardPath.valueOf("**/*.build"),
				new ScriptOptionsConfig(null, ScriptProviderLocation.getBuiltin()));
		ExecutionScriptConfiguration scriptconfig = builder.build();
		parameters.setScriptConfiguration(scriptconfig);

		TaskIdentifier remotetaskid = TaskIdentifier.builder(String.class.getName()).field("val", "builtin.remote.task")
				.build();
		runScriptTask("build");
		WeakReference<Class<?>> taskfactoryclassweakref = new WeakReference<>(
				getMetric().getRunTaskIdFactories().get(remotetaskid).getClass());
		assertMap(getMetric().getRunTaskIdResults()).contains(remotetaskid, "hello_remote");

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());
		assertEmpty(getMetric().getRunTaskIdFactories());

		environment.clearCachedDatasWaitExecutions();
		for (LocalDaemonEnvironment daemonenv : clusterEnvironments.values()) {
			daemonenv.getSakerEnvironment().clearCachedDatasWaitExecutions();
		}
		clearMetric();

		try {
			for (int i = 0; i <= 15; ++i) {
				System.gc();
				System.runFinalization();
				if (taskfactoryclassweakref.get() == null) {
					//success, the class was garbage collected
					break;
				}
				if (i % 3 == 0) {
					try {
						//attempt to allocate a lot of memory, to cause soft references to be cleared
						List<byte[]> allocated = new ArrayList<>();
						while (true) {
							allocated.add(new byte[32 * 1024 * 1024]);
						}
					} catch (OutOfMemoryError e) {
						//this is expected and desired
						System.err.println("[" + i + "] " + e);
					}
					if (taskfactoryclassweakref.get() == null) {
						//success, the class was garbage collected
						break;
					}
				}

				Thread.sleep(50);
			}
			assertNull(taskfactoryclassweakref.get(), "class wasn't garbage collected");
		} catch (Exception | AssertionError e) {
			//just a heap dump to help discovering the remaining references during dev
			dumpHeap();
			throw e;
		}
	}

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		// use a private environment
		// don't use a project cache, as we don't want the references to be cached by the test
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations())
				.setClusterNames(ImmutableUtils.singletonSet(ClusterBuildTestCase.DEFAULT_CLUSTER_NAME))
				.setEnvironmentStorageDirectory(null).setUseProject(false).build();
	}

	private void dumpHeap() {
		try {
			String tempdir = System.getProperty("java.io.tmpdir");
			if (!ObjectUtils.isNullOrEmpty(tempdir)) {
				HotSpotDiagnosticMXBean mxBean = ManagementFactory.newPlatformMXBeanProxy(
						ManagementFactory.getPlatformMBeanServer(), "com.sun.management:type=HotSpotDiagnostic",
						HotSpotDiagnosticMXBean.class);
				String dumppath = Paths.get(tempdir)
						.resolve(getClass().getSimpleName() + "_heap_" + UUID.randomUUID().toString() + ".hprof")
						.toAbsolutePath().normalize().toString();
				System.out.println("Dumping heap to: " + dumppath);
				mxBean.dumpHeap(dumppath, true);
				System.out.println("Dumped heap to: " + dumppath);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
