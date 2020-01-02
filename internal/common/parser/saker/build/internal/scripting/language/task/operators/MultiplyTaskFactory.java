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

public class MultiplyTaskFactory extends BinaryNumberOperatorTaskFactory {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public MultiplyTaskFactory() {
	}

	public MultiplyTaskFactory(SakerTaskFactory left, SakerTaskFactory right) {
		super(left, right);
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new MultiplyTaskFactory(cloneHelper(taskfactoryreplacements, left),
				cloneHelper(taskfactoryreplacements, right));
	}

	@Override
	public String toString() {
		return "(mult:(" + left + " * " + right + "))";
	}

	@Override
	protected BigDecimal apply(BigDecimal left, BigDecimal right) {
		return left.multiply(right);
	}

	@Override
	protected BigInteger apply(BigInteger left, BigInteger right) {
		return left.multiply(right);
	}

	@Override
	protected Number apply(double left, double right) {
		return left * right;
	}

	@Override
	protected Number apply(long left, long right) {
		//as seen in Math.multiplyExact
		long result = left * right;
		long ax = Math.abs(left);
		long ay = Math.abs(right);
		if (((ax | ay) >>> 31 != 0)) {
			if (((right != 0) && (result / right != left)) || (left == Long.MIN_VALUE && right == -1)) {
				return apply(BigInteger.valueOf(left), BigInteger.valueOf(right));
			}
		}
		return result;
	}

}
