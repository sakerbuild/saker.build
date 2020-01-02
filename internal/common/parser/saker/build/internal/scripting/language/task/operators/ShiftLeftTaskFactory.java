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

import saker.build.internal.scripting.language.task.SakerScriptTaskUtils;
import saker.build.internal.scripting.language.task.SakerTaskFactory;

public class ShiftLeftTaskFactory extends BinaryNumberOperatorTaskFactory {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public ShiftLeftTaskFactory() {
	}

	public ShiftLeftTaskFactory(SakerTaskFactory left, SakerTaskFactory right) {
		super(left, right);
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new ShiftLeftTaskFactory(cloneHelper(taskfactoryreplacements, left),
				cloneHelper(taskfactoryreplacements, right));
	}

	@Override
	public String toString() {
		return "(shl:(" + left + " << " + right + "))";
	}

	@Override
	protected BigDecimal apply(BigDecimal left, BigDecimal right) {
		throw new UnsupportedOperationException(
				"Bitwise operations are unsupported on floating point numbers. (" + left + " << " + right + ")");
	}

	@Override
	protected BigInteger apply(BigInteger left, BigInteger right) {
		return left.shiftLeft(right.intValue());
	}

	@Override
	protected Number apply(double left, double right) {
		throw new UnsupportedOperationException(
				"Bitwise operations are unsupported on floating point numbers. (" + left + " << " + right + ")");
	}

	@Override
	protected Number apply(long left, long right) {
		return applyLeftShift(left, right);
	}

	public static Number applyLeftShift(long left, long right) {
		if (right == 0) {
			return left;
		}
		if (right < 0) {
			//right operand is negative. Execute a shift right instead
			return ShiftRightTaskFactory.applyRightShift(left, Math.negateExact(right));
		}
		if (left < 0 || right >= 64 || SakerScriptTaskUtils.highestBitIndex(left) + right >= 63) {
			return SakerScriptTaskUtils.reducePrecision(BigInteger.valueOf(left).shiftLeft((int) right));
		}
		//right is in the range of [0..63], so we can use the Java version
		return left << right;
	}

}
