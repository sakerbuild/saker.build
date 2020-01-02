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
package testing.saker.build.tests.utils;

import java.util.Map;

import javax.tools.ToolProvider;

import saker.build.util.java.JavaTools;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

/**
 * Tests that the Java tools can be retrieved in the current JVM.
 * <p>
 * The {@link ToolProvider} class is not on the class path by default on JDK9+, therefore it needs special handling.
 * <p>
 * Note: that was only the case when the testing task didn't set the platform classloader to the user classloader
 * parent.
 */
@SakerTest
public class JavaToolsTest extends SakerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		JavaTools.getSystemDocumentationTool();
		JavaTools.getSystemJavaCompiler();
		ToolProvider.getSystemJavaCompiler();
	}

}
