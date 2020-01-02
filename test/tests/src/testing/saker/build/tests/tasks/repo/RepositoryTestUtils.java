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
package testing.saker.build.tests.tasks.repo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerFileProvider;
import saker.build.runtime.repository.SakerRepositoryFactory;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import testing.saker.build.tests.tasks.repo.testrepo.RemoteDispatchableTestTask;
import testing.saker.build.tests.tasks.repo.testrepo.TestRepo;
import testing.saker.build.tests.tasks.repo.testrepo.TestRepoFactory;
import testing.saker.build.tests.tasks.repo.testrepo.TestRepoFileOutputTaskFactory;
import testing.saker.build.tests.tasks.repo.testrepo.TestRepoUserParamTask;
import testing.saker.build.tests.tasks.repo.testrepo.TestTask;

public class RepositoryTestUtils {
	public static void copyClassEntry(Class<?> clazz, JarOutputStream jos) throws IOException {
		String entryname = clazz.getName().replace('.', '/') + ".class";
		ZipEntry entry = new ZipEntry(entryname);
		entry.setLastModifiedTime(FileTime.fromMillis(0));
		jos.putNextEntry(entry);
		try (InputStream is = RepositoryActionTest.class.getClassLoader().getResourceAsStream(entryname)) {
			StreamUtils.copyStream(is, jos);
		}
		jos.closeEntry();
		for (Class<?> dcl : clazz.getDeclaredClasses()) {
			copyClassEntry(dcl, jos);
		}
	}

	public static void exportJarWithClasses(SakerFileProvider fileprovider, SakerPath jarpath, Class<?>... classes)
			throws IOException {
		try (JarOutputStream jos = new JarOutputStream(ByteSink.toOutputStream(fileprovider.openOutput(jarpath)))) {
			for (Class<?> c : classes) {
				copyClassEntry(c, jos);
			}
		}
	}

	public static void exportTestRepositoryJar(SakerFileProvider fileprovider, SakerPath jarpath) throws IOException {
		exportTestRepositoryJarWithClasses(fileprovider, jarpath, TestRepoFactory.class, TestRepo.class, TestTask.class,
				RemoteDispatchableTestTask.class, TestRepoFileOutputTaskFactory.class, TestRepoUserParamTask.class);
	}

	public static void exportTestRepositoryJarWithClasses(SakerFileProvider fileprovider, SakerPath jarpath,
			Class<?>... classes) throws IOException {
		try (JarOutputStream jos = new JarOutputStream(ByteSink.toOutputStream(fileprovider.openOutput(jarpath)))) {
			ZipEntry entry = new ZipEntry("META-INF/services/" + SakerRepositoryFactory.class.getCanonicalName());
			entry.setLastModifiedTime(FileTime.fromMillis(0));
			jos.putNextEntry(entry);
			jos.write(TestRepoFactory.class.getName().getBytes(StandardCharsets.UTF_8));
			jos.closeEntry();
			for (Class<?> c : classes) {
				if (c.getDeclaringClass() == null) {
					copyClassEntry(c, jos);
				}
			}
		}
	}

	public static String createRepositoryJarName(Class<?> testclass) {
		return "repo." + testclass.getName() + ".jar";
	}
}
