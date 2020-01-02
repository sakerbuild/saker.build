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
package saker.build.internal.scripting.language.task.operators;

import java.io.Externalizable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import saker.build.internal.scripting.language.task.SakerTaskFactory;

public class DivideTaskFactory extends BinaryNumberOperatorTaskFactory {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public DivideTaskFactory() {
	}

	public DivideTaskFactory(SakerTaskFactory left, SakerTaskFactory right) {
		super(left, right);
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new DivideTaskFactory(cloneHelper(taskfactoryreplacements, left),
				cloneHelper(taskfactoryreplacements, right));
	}

	@Override
	public String toString() {
		return "(div:(" + left + " / " + right + "))";
	}

	@Override
	protected BigDecimal apply(BigDecimal left, BigDecimal right) {
		return left.divide(right);
	}

	@Override
	protected BigInteger apply(BigInteger left, BigInteger right) {
		return left.divide(right);
	}

	@Override
	protected Number apply(double left, double right) {
		return left / right;
	}

	@Override
	protected Number apply(long left, long right) {
		//although division should always result in a less or equals absolute value of the result
		//we need to check for overflow, as Long.MIN_VALUE / -1 results in Long.MIN_VALUE, which is not what's expected
		if (left == Long.MIN_VALUE && right == -1) {
			return apply(BigInteger.valueOf(left), BigInteger.valueOf(right));
		}
		return left / right;
	}

}
