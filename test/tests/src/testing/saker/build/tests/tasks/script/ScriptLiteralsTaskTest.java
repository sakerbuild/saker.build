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
package testing.saker.build.tests.tasks.script;

import java.math.BigInteger;

import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;

@SakerTest
public class ScriptLiteralsTaskTest extends CollectingMetricEnvironmentTestCase {

	@Override
	protected void runTestImpl() throws Throwable {
		CombinedTargetTaskResult result;

		result = runScriptTask("build");
		assertEquals(result.getTargetTaskResult("integral"), 123L);
		assertEquals(result.getTargetTaskResult("floating"), 1.23);
		assertEquals(result.getTargetTaskResult("negint"), -123L);
		assertEquals(result.getTargetTaskResult("posint"), +123L);
		assertEquals(result.getTargetTaskResult("hex"), 0x123aFL);
		assertEquals(result.getTargetTaskResult("exp"), 1234.5E-6);
		assertEquals(result.getTargetTaskResult("nan"), Double.NaN);
		assertEquals(result.getTargetTaskResult("truebool1"), true);
		assertEquals(result.getTargetTaskResult("truebool2"), true);
		assertEquals(result.getTargetTaskResult("falsebool1"), false);
		assertEquals(result.getTargetTaskResult("falsebool2"), false);

		assertEquals(result.getTargetTaskResult("lit"), "lit");
		assertEquals(result.getTargetTaskResult("strlit"), "strlit");
		assertEquals(result.getTargetTaskResult("unicode"), "\u1234");
		assertEquals(result.getTargetTaskResult("oct1"), "\1");
		assertEquals(result.getTargetTaskResult("oct2"), "\12");
		assertEquals(result.getTargetTaskResult("oct3"), "\123");
		assertEquals(result.getTargetTaskResult("oct4"), "\1234");

		assertEquals(result.getTargetTaskResult("compound"), "comp" + (1 + 2));

		assertEquals(result.getTargetTaskResult("escapes"), " \t\r\b\n\r\f\'\"\\");
		assertEquals(result.getTargetTaskResult("compoundescapes"),
				" \t\r\b\n\r\f\'\"\\" + (2 * 4) + " \t\r\b\n\r\f\'\"\\{");

		assertEquals(result.getTargetTaskResult("multiline"), "first\nsecond\nthird");

		assertEquals(result.getTargetTaskResult("bigint"), new BigInteger("123456789012345678901234567890"));
		assertEquals(result.getTargetTaskResult("bighex"), new BigInteger("fffffffffffffffffffff", 16));

		assertEquals(result.getTargetTaskResult("longmaxint"), Long.MAX_VALUE);
		assertEquals(result.getTargetTaskResult("longmaxintp1"), new BigInteger("9223372036854775808"));

		assertEquals(result.getTargetTaskResult("longminint"), Long.MIN_VALUE);
		assertEquals(result.getTargetTaskResult("longminintm1"), new BigInteger("-9223372036854775809"));

		assertEquals(result.getTargetTaskResult("op1"), 579L);
		assertEquals(result.getTargetTaskResult("op2"), "123+456");
	}

}
