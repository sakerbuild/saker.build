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
package testing.saker.build.tests.tasks.file;

import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.UUID;

import saker.build.file.path.SakerPath;
import saker.build.runtime.execution.ExecutionParametersImpl;
import saker.build.runtime.params.ExecutionPathConfiguration;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.MemoryFileProvider;
import testing.saker.build.tests.tasks.factories.SequentialChildTaskStarterTaskFactory;
import testing.saker.build.tests.tasks.file.CopySynchronizePosixPermissionFileTaskTest.CopyPosixSynchronizerTask;
import testing.saker.build.tests.tasks.file.SimplePosixPermissionFileTaskTest.PosixFileSynchronizerTask;

@SakerTest
public class CopyNonSupportedPosixSynchronizePosixPermissionFileTaskTest extends CollectingMetricEnvironmentTestCase {

	private static final String MYROOT = "myroot:";

	@Override
	protected MemoryFileProvider createMemoryFileProvider(Set<String> roots, UUID filesuuid) {
		//don't support posix for the base mounts
		MemoryFileProvider result = super.createMemoryFileProvider(roots, filesuuid);
		result.setPosixPermissionsSupported(false);
		return result;
	}

	@Override
	protected void runTestImpl() throws Throwable {
		SakerPath outpath = SakerPath.valueOf(MYROOT).resolve("out.txt");
		SakerPath filepath = PATH_BUILD_DIRECTORY.resolve("file.txt");
		SakerPath outpath_s_with_p = outpath.resolveSibling(outpath.getFileName() + "_s_with_p");
		SakerPath outpath_s_without_p = outpath.resolveSibling(outpath.getFileName() + "_s_without_p");
		SakerPath outpath_delegate = outpath.resolveSibling(outpath.getFileName() + "_delegate");

		MemoryFileProvider myfp = (MemoryFileProvider) parameters.getPathConfiguration().getFileProvider(outpath);

		SequentialChildTaskStarterTaskFactory main = new SequentialChildTaskStarterTaskFactory()
				.add(strTaskId("1"), new PosixFileSynchronizerTask("123", setOf(PosixFilePermission.GROUP_EXECUTE)))
				.add(strTaskId("2"), new CopyPosixSynchronizerTask(1).setOutputPath(outpath));
		runTask("main", main);
		assertEquals(files.getPosixFilePermissions(filepath), null);
		assertEquals(myfp.getPosixFilePermissions(outpath), setOf(PosixFilePermission.GROUP_EXECUTE));
		assertEquals(myfp.getPosixFilePermissions(outpath_s_with_p), setOf(PosixFilePermission.GROUP_EXECUTE));
		assertEquals(myfp.getPosixFilePermissions(outpath_s_without_p),
				MemoryFileProvider.FILE_DEFAULT_POSIX_PERMISSIONS);
		assertEquals(myfp.getPosixFilePermissions(outpath_delegate), setOf(PosixFilePermission.GROUP_EXECUTE));

		runTask("main", main);
		assertEmpty(getMetric().getRunTaskIdFactories());

		files.setPosixFilePermissions(filepath, setOf(PosixFilePermission.OTHERS_READ));
		runTask("main", main);
		assertEquals(files.getPosixFilePermissions(filepath), null);
		assertEquals(myfp.getPosixFilePermissions(outpath), setOf(PosixFilePermission.GROUP_EXECUTE));
		assertEquals(myfp.getPosixFilePermissions(outpath_s_with_p), setOf(PosixFilePermission.GROUP_EXECUTE));
		assertEquals(myfp.getPosixFilePermissions(outpath_s_without_p),
				MemoryFileProvider.FILE_DEFAULT_POSIX_PERMISSIONS);
		assertEquals(myfp.getPosixFilePermissions(outpath_delegate), setOf(PosixFilePermission.GROUP_EXECUTE));

		main = new SequentialChildTaskStarterTaskFactory()
				.add(strTaskId("1"), new PosixFileSynchronizerTask("123", setOf(PosixFilePermission.OWNER_WRITE)))
				.add(strTaskId("2"), new CopyPosixSynchronizerTask(1).setOutputPath(outpath));
		runTask("main", main);
		assertEquals(files.getPosixFilePermissions(filepath), null);
		assertEquals(myfp.getPosixFilePermissions(outpath), setOf(PosixFilePermission.OWNER_WRITE));
		assertEquals(myfp.getPosixFilePermissions(outpath_s_with_p), setOf(PosixFilePermission.OWNER_WRITE));
		assertEquals(myfp.getPosixFilePermissions(outpath_s_without_p),
				MemoryFileProvider.FILE_DEFAULT_POSIX_PERMISSIONS);
		assertEquals(myfp.getPosixFilePermissions(outpath_delegate), setOf(PosixFilePermission.OWNER_WRITE));

		runTask("main", main);
		assertEmpty(getMetric().getRunTaskIdFactories());

		//only the copy task should re-run.
		//check that the posix permissions are retrieved from the content database and reapplied correctly
		//without the original task re-running
		main = new SequentialChildTaskStarterTaskFactory()
				.add(strTaskId("1"), new PosixFileSynchronizerTask("123", setOf(PosixFilePermission.OWNER_WRITE)))
				.add(strTaskId("2"), new CopyPosixSynchronizerTask(2).setOutputPath(outpath));
		myfp.setPosixFilePermissions(outpath, setOf());
		runTask("main", main);
		assertEquals(files.getPosixFilePermissions(filepath), null);
		assertEquals(myfp.getPosixFilePermissions(outpath), setOf(PosixFilePermission.OWNER_WRITE));
		assertEquals(myfp.getPosixFilePermissions(outpath_s_with_p), setOf(PosixFilePermission.OWNER_WRITE));
		assertEquals(myfp.getPosixFilePermissions(outpath_s_without_p),
				MemoryFileProvider.FILE_DEFAULT_POSIX_PERMISSIONS);
		assertEquals(myfp.getPosixFilePermissions(outpath_delegate), setOf(PosixFilePermission.OWNER_WRITE));
		
		myfp.setPosixFilePermissions(outpath_delegate, setOf());
		runTask("main", main);
		assertEquals(myfp.getPosixFilePermissions(outpath_delegate), setOf(PosixFilePermission.OWNER_WRITE));
	}

	@Override
	protected void setupParameters(ExecutionParametersImpl params) {
		ExecutionPathConfiguration.Builder pcbuilder = ExecutionPathConfiguration.builder(params.getPathConfiguration(),
				params.getPathConfiguration().getWorkingDirectory());
		pcbuilder.addRootProvider(MYROOT, new MemoryFileProvider(setOf(MYROOT), UUID.randomUUID()));
		params.setPathConfiguration(pcbuilder.build());
		super.setupParameters(params);
	}
}
