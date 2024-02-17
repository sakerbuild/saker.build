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
package testing.saker.build.tests.classpath;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import saker.build.file.path.SakerPath;
import saker.build.meta.Versions;
import saker.build.thirdparty.saker.util.classloader.MultiDataClassLoader;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.TestUtils;

/**
 * Test for ensuring that version compatibility if statements don't cause class file verification and other errors.
 * <p>
 * A Consumer class is compiled against a new version of a Provider class. It references a method that is only available
 * in the newer version, but the method call is guarded by an if statement on the version constant.
 * <p>
 * No class errors should be encountered during runtime, and the proper code paths should be taken.
 * <p>
 * Similar to how the version compatibility is planned based on the constants in {@link Versions}.
 * <p>
 * This test is to ensure that the JVM works as we expect.
 */
@SakerTest
public class ClassPathVersionCompatTest extends SakerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		SakerPath workdir = SakerPath.valueOf(EnvironmentTestCase.resolveClassNamedDirectory(
				EnvironmentTestCase.getTestingBaseWorkingDirectory(), this.getClass().getName()));

		Map<String, ByteArrayRegion> v1provider = TestUtils.compileJavaSources(workdir.resolve("test/V1Provider.java"));
		Map<String, ByteArrayRegion> v2provider = TestUtils.compileJavaSources(workdir.resolve("test/V2Provider.java"));
		Map<String, ByteArrayRegion> v3provider = TestUtils.compileJavaSources(workdir.resolve("test/V3Provider.java"));
		Map<String, ByteArrayRegion> v4provider = TestUtils.compileJavaSources(workdir.resolve("test/V4Provider.java"));

		Map<String, ByteArrayRegion> consumerclass = TestUtils
				.compileJavaSources(workdir.resolve("test/V4Provider.java"), workdir.resolve("test/Consumer.java"));
		//retain only the consumer class
		consumerclass.keySet().retainAll(Arrays.asList("test/Consumer.class"));

		{
			//test with v4 provider
			//calls an interface function only available in v4, with a function returning non-interface type
			TreeMap<String, ByteArrayRegion> classes = new TreeMap<>(v4provider);
			classes.putAll(consumerclass);

			MultiDataClassLoader cl = new MultiDataClassLoader((ClassLoader) null,
					new TestUtils.MemoryClassLoaderDataFinder(classes));
			@SuppressWarnings("unchecked")
			Function<Integer, Integer> consumer = (Function<Integer, Integer>) Class.forName("test.Consumer", false, cl)
					.getConstructor().newInstance();
			//returns the 3x value
			assertEquals(consumer.apply(4), 4 * 4);
		}

		{
			//test with v3 provider
			//references (and instantiates) a type which is only available in the v3 version
			TreeMap<String, ByteArrayRegion> classes = new TreeMap<>(v3provider);
			classes.putAll(consumerclass);

			MultiDataClassLoader cl = new MultiDataClassLoader((ClassLoader) null,
					new TestUtils.MemoryClassLoaderDataFinder(classes));
			@SuppressWarnings("unchecked")
			Function<Integer, Integer> consumer = (Function<Integer, Integer>) Class.forName("test.Consumer", false, cl)
					.getConstructor().newInstance();
			//returns the 3x value
			assertEquals(consumer.apply(4), 4 * 3);
		}
		{
			//test with v2 provider
			//references and calls a function that is only available in the v2 version
			TreeMap<String, ByteArrayRegion> classes = new TreeMap<>(v2provider);
			classes.putAll(consumerclass);

			MultiDataClassLoader cl = new MultiDataClassLoader((ClassLoader) null,
					new TestUtils.MemoryClassLoaderDataFinder(classes));
			@SuppressWarnings("unchecked")
			Function<Integer, Integer> consumer = (Function<Integer, Integer>) Class.forName("test.Consumer", false, cl)
					.getConstructor().newInstance();
			//returns the 2x value
			assertEquals(consumer.apply(4), 4 * 2);
		}
		{
			// test with v1 provider
			// falls back to without calling any provider function
			TreeMap<String, ByteArrayRegion> classes = new TreeMap<>(v1provider);
			classes.putAll(consumerclass);

			MultiDataClassLoader cl = new MultiDataClassLoader((ClassLoader) null,
					new TestUtils.MemoryClassLoaderDataFinder(classes));
			@SuppressWarnings("unchecked")
			Function<Integer, Integer> consumer = (Function<Integer, Integer>) Class.forName("test.Consumer", false, cl)
					.getConstructor().newInstance();
			//return the same number
			assertEquals(consumer.apply(4), 4);
		}
	}

}
