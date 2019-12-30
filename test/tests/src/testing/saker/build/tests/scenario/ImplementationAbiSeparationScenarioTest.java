package testing.saker.build.tests.scenario;

import java.io.Externalizable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.charset.StandardCharsets;

import saker.build.file.ByteArraySakerFile;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.identifier.TaskIdentifier;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.LiteralTaskFactory;
import testing.saker.build.tests.StringTaskIdentifier;
import testing.saker.build.tests.tasks.factories.ChildTaskStarterTaskFactory;

@SakerTest
public class ImplementationAbiSeparationScenarioTest extends CollectingMetricEnvironmentTestCase {
	//this test is for testing the implementational possibility of abi and implementation separation related compilation
	// the scenario assumes that two dependent java project (A and B) are compiled. B depends on A.
	// B only uses the binary interface (ABI) of A, therefore when all of the sources of A has been parsed
	// B could start to compile.
	// instead, in common scenarios, the compilation of B requires the compilation of A to completely finish.
	// this scenario tests if the shortcutting scenario can be implemented in the build system
	// where the compilation of B starts right after the ABI result of A is available
	//see also: https://artemzin.com/blog/fundamental-design-issues-of-gradle-build-system/
	//       Design Issue: Modules are not Built Against ABI

	//the implementation in this test can be obviously reified, but it servers as a proof nonetheless

	private static class CompilerOutput implements Externalizable {
		private static final long serialVersionUID = 1L;

		private TaskIdentifier backendTask;
		private TaskIdentifier abiResultTask;

		/**
		 * For {@link Externalizable}.
		 */
		public CompilerOutput() {
		}

		public CompilerOutput(TaskIdentifier backendTask, TaskIdentifier abiResultTask) {
			this.backendTask = backendTask;
			this.abiResultTask = abiResultTask;
		}

		public TaskIdentifier getBackendTaskId() {
			return backendTask;
		}

		public TaskIdentifier getAbiResultTaskId() {
			return abiResultTask;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(backendTask);
			out.writeObject(abiResultTask);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			backendTask = (TaskIdentifier) in.readObject();
			abiResultTask = (TaskIdentifier) in.readObject();
		}
	}

	private static class BackendOutput implements Externalizable {
		private static final long serialVersionUID = 1L;

		private SakerPath outputPath;

		/**
		 * For {@link Externalizable}.
		 */
		public BackendOutput() {
		}

		public BackendOutput(SakerPath outputPath) {
			this.outputPath = outputPath;
		}

		public SakerPath getOutputPath() {
			return outputPath;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(outputPath);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			outputPath = (SakerPath) in.readObject();
		}

	}

	private static class CompilerBackendTaskFactory
			implements TaskFactory<BackendOutput>, Task<BackendOutput>, Externalizable {
		private static final long serialVersionUID = 1L;

		private TaskIdentifier dependency;
		private SakerPath inputFile;
		private boolean produceAbiTask;

		/**
		 * For {@link Externalizable}.
		 */
		public CompilerBackendTaskFactory() {
		}

		public CompilerBackendTaskFactory(TaskIdentifier dependency, SakerPath inputFile, boolean produceAbiTask) {
			this.dependency = dependency;
			this.inputFile = inputFile;
			this.produceAbiTask = produceAbiTask;
		}

		@Override
		public BackendOutput run(TaskContext taskcontext) throws Exception {
			SakerFile file = taskcontext.getTaskUtilities().resolveAtPath(inputFile);
			if (file == null) {
				throw new FileNotFoundException(inputFile.toString());
			}
			CompilerOutput dependencyoutput = null;
			if (dependency != null) {
				dependencyoutput = (CompilerOutput) taskcontext.getTaskResult(dependency);
			}
			taskcontext.getTaskUtilities().reportInputFileDependency(null, file);

			String[] inputlines = file.getContent().split("\n");
			int input = Integer.parseInt(inputlines[0]);
			int inputsecond = Integer.parseInt(inputlines[1]);
			SakerDirectory builddir = taskcontext.getTaskBuildDirectory();
			String data = "";
			//this is the ABI
			int abivalue = input + input;
			if (dependencyoutput != null) {
				TaskIdentifier abitaskid = dependencyoutput.getAbiResultTaskId();
				if (abitaskid != null) {
					abivalue += (Integer) taskcontext.getTaskResult(abitaskid);
				} else {
					BackendOutput backendoutput = (BackendOutput) taskcontext
							.getTaskResult(dependencyoutput.getBackendTaskId());
					SakerFile depfilepath = taskcontext.getTaskUtilities().resolveAtPath(backendoutput.getOutputPath());
					taskcontext.getTaskUtilities().reportInputFileDependency(null, depfilepath);
					abivalue += Integer.parseInt(depfilepath.getContent().split("\n")[0]);
				}
			}
			String thistaskidname = ((StringTaskIdentifier) taskcontext.getTaskId()).getName();
			if (produceAbiTask) {
				taskcontext.startTask(strTaskId(thistaskidname + "-abi"), new LiteralTaskFactory(abivalue), null);
			}
			data += abivalue;
			data += '\n';
			//this is the actual compilation result
			data += (input * input + inputsecond);
			data += '\n';

			ByteArraySakerFile outputfile = new ByteArraySakerFile(thistaskidname + "-output.txt",
					data.getBytes(StandardCharsets.UTF_8));
			builddir.add(outputfile);
			taskcontext.getTaskUtilities().reportOutputFileDependency(null, outputfile);
			outputfile.synchronize();
			return new BackendOutput(outputfile.getSakerPath());
		}

		@Override
		public Task<? extends BackendOutput> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(dependency);
			out.writeObject(inputFile);
			out.writeBoolean(produceAbiTask);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			dependency = (TaskIdentifier) in.readObject();
			inputFile = (SakerPath) in.readObject();
			produceAbiTask = in.readBoolean();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((dependency == null) ? 0 : dependency.hashCode());
			result = prime * result + ((inputFile == null) ? 0 : inputFile.hashCode());
			result = prime * result + (produceAbiTask ? 1231 : 1237);
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			CompilerBackendTaskFactory other = (CompilerBackendTaskFactory) obj;
			if (dependency == null) {
				if (other.dependency != null)
					return false;
			} else if (!dependency.equals(other.dependency))
				return false;
			if (inputFile == null) {
				if (other.inputFile != null)
					return false;
			} else if (!inputFile.equals(other.inputFile))
				return false;
			if (produceAbiTask != other.produceAbiTask)
				return false;
			return true;
		}

	}

	private static class CompilerTaskFactory
			implements TaskFactory<CompilerOutput>, Task<CompilerOutput>, Externalizable {
		private static final long serialVersionUID = 1L;

		private TaskIdentifier dependency;
		private SakerPath inputFile;
		private boolean produceAbiTask;

		/**
		 * For {@link Externalizable}.
		 */
		public CompilerTaskFactory() {
		}

		public CompilerTaskFactory(TaskIdentifier dependency, SakerPath inputFile, boolean produceAbiTask) {
			this.dependency = dependency;
			this.inputFile = inputFile;
			this.produceAbiTask = produceAbiTask;
		}

		@Override
		public CompilerOutput run(TaskContext taskcontext) throws Exception {
			String thistaskidname = ((StringTaskIdentifier) taskcontext.getTaskId()).getName();
			CompilerBackendTaskFactory betask = new CompilerBackendTaskFactory(dependency, inputFile, produceAbiTask);
			TaskIdentifier backendtaskid = strTaskId(thistaskidname + "-x");
			taskcontext.startTask(backendtaskid, betask, null);
			return new CompilerOutput(backendtaskid, produceAbiTask ? strTaskId(thistaskidname + "-x-abi") : null);
		}

		@Override
		public Task<? extends CompilerOutput> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(dependency);
			out.writeObject(inputFile);
			out.writeBoolean(produceAbiTask);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			dependency = (TaskIdentifier) in.readObject();
			inputFile = (SakerPath) in.readObject();
			produceAbiTask = in.readBoolean();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((dependency == null) ? 0 : dependency.hashCode());
			result = prime * result + ((inputFile == null) ? 0 : inputFile.hashCode());
			result = prime * result + (produceAbiTask ? 1231 : 1237);
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			CompilerTaskFactory other = (CompilerTaskFactory) obj;
			if (dependency == null) {
				if (other.dependency != null)
					return false;
			} else if (!dependency.equals(other.dependency))
				return false;
			if (inputFile == null) {
				if (other.inputFile != null)
					return false;
			} else if (!inputFile.equals(other.inputFile))
				return false;
			if (produceAbiTask != other.produceAbiTask)
				return false;
			return true;
		}

	}

	@Override
	protected void runTestImpl() throws Throwable {
		testWithoutABI();
		testWithABI();
	}

	private void testWithABI() throws IOException, Throwable, AssertionError {
		String maintaskname = "mainabi";

		SakerPath ctxtpath = PATH_WORKING_DIRECTORY.resolve("c.txt");
		SakerPath dtxtpath = PATH_WORKING_DIRECTORY.resolve("d.txt");
		files.putFile(ctxtpath, "10\n1");
		files.putFile(dtxtpath, "30\n3");

		ChildTaskStarterTaskFactory main = new ChildTaskStarterTaskFactory()
				.add(strTaskId("compileC"), new CompilerTaskFactory(null, ctxtpath, true))
				.add(strTaskId("compileD"), new CompilerTaskFactory(strTaskId("compileC"), dtxtpath, true));

		runTask(maintaskname, main);
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("compileC-x-output.txt")).toString(), "20\n101\n");
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("compileD-x-output.txt")).toString(), "80\n903\n");

		runTask(maintaskname, main);
		assertEmpty(getMetric().getRunTaskIdFactories());

		files.putFile(dtxtpath, "30\n4");
		runTask(maintaskname, main);
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("compileD-x-output.txt")).toString(), "80\n904\n");
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), setOf(strTaskId("compileD-x")));

		files.putFile(dtxtpath, "31\n4");
		runTask(maintaskname, main);
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("compileD-x-output.txt")).toString(), "82\n965\n");
		assertEquals(getMetric().getRunTaskIdFactories().keySet(),
				setOf(strTaskId("compileD-x"), strTaskId("compileD-x-abi")));

		//reset back
		files.putFile(ctxtpath, "10\n1");
		files.putFile(dtxtpath, "30\n3");
		runTask(maintaskname, main);
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("compileC-x-output.txt")).toString(), "20\n101\n");
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("compileD-x-output.txt")).toString(), "80\n903\n");

		//compileC-x is not rerun, as the ABI of compileC is not changed
		files.putFile(ctxtpath, "10\n2");
		runTask(maintaskname, main);
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("compileC-x-output.txt")).toString(), "20\n102\n");
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("compileD-x-output.txt")).toString(), "80\n903\n");
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), setOf(strTaskId("compileC-x")));

		files.putFile(ctxtpath, "11\n2");
		runTask(maintaskname, main);
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("compileC-x-output.txt")).toString(), "22\n123\n");
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("compileD-x-output.txt")).toString(), "82\n903\n");
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), setOf(strTaskId("compileC-x"),
				strTaskId("compileC-x-abi"), strTaskId("compileD-x"), strTaskId("compileD-x-abi")));
	}

	private void testWithoutABI() throws IOException, Throwable, AssertionError {
		String maintaskname = "main";

		SakerPath atxtpath = PATH_WORKING_DIRECTORY.resolve("a.txt");
		SakerPath btxtpath = PATH_WORKING_DIRECTORY.resolve("b.txt");
		files.putFile(atxtpath, "10\n1");
		files.putFile(btxtpath, "30\n3");

		ChildTaskStarterTaskFactory main = new ChildTaskStarterTaskFactory()
				.add(strTaskId("compileA"), new CompilerTaskFactory(null, atxtpath, false))
				.add(strTaskId("compileB"), new CompilerTaskFactory(strTaskId("compileA"), btxtpath, false));

		runTask(maintaskname, main);
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("compileA-x-output.txt")).toString(), "20\n101\n");
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("compileB-x-output.txt")).toString(), "80\n903\n");

		runTask(maintaskname, main);
		assertEmpty(getMetric().getRunTaskIdFactories());

		files.putFile(btxtpath, "30\n4");
		runTask(maintaskname, main);
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("compileB-x-output.txt")).toString(), "80\n904\n");
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), setOf(strTaskId("compileB-x")));

		files.putFile(btxtpath, "31\n4");
		runTask(maintaskname, main);
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("compileB-x-output.txt")).toString(), "82\n965\n");
		assertEquals(getMetric().getRunTaskIdFactories().keySet(), setOf(strTaskId("compileB-x")));

		//reset back
		files.putFile(atxtpath, "10\n1");
		files.putFile(btxtpath, "30\n3");
		runTask(maintaskname, main);
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("compileA-x-output.txt")).toString(), "20\n101\n");
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("compileB-x-output.txt")).toString(), "80\n903\n");

		files.putFile(atxtpath, "10\n2");
		runTask(maintaskname, main);
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("compileA-x-output.txt")).toString(), "20\n102\n");
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("compileB-x-output.txt")).toString(), "80\n903\n");
		assertEquals(getMetric().getRunTaskIdFactories().keySet(),
				setOf(strTaskId("compileB-x"), strTaskId("compileA-x")));

		files.putFile(atxtpath, "11\n2");
		runTask(maintaskname, main);
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("compileA-x-output.txt")).toString(), "22\n123\n");
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("compileB-x-output.txt")).toString(), "82\n903\n");
		assertEquals(getMetric().getRunTaskIdFactories().keySet(),
				setOf(strTaskId("compileB-x"), strTaskId("compileA-x")));

		//reset back
		files.putFile(atxtpath, "10\n1");
		files.putFile(btxtpath, "30\n3");
		runTask(maintaskname, main);
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("compileA-x-output.txt")).toString(), "20\n101\n");
		assertEquals(files.getAllBytes(PATH_BUILD_DIRECTORY.resolve("compileB-x-output.txt")).toString(), "80\n903\n");
	}
}
