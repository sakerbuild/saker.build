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

import saker.build.internal.scripting.language.task.SakerLiteralTaskFactory;
import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.internal.scripting.language.task.result.SimpleSakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.TaskFuture;
import saker.build.task.identifier.TaskIdentifier;

public class EqualsTaskFactory extends BinaryOperatorTaskFactory {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public EqualsTaskFactory() {
	}

	public EqualsTaskFactory(SakerTaskFactory left, SakerTaskFactory right) {
		super(left, right);
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) throws Exception {
		SakerScriptTaskIdentifier thistaskid = (SakerScriptTaskIdentifier) taskcontext.getTaskId();
		TaskIdentifier lefttaskid = left.createSubTaskIdentifier(thistaskid);
		TaskIdentifier righttaskid = right.createSubTaskIdentifier(thistaskid);

		TaskFuture<SakerTaskResult> leftfut = taskcontext.getTaskUtilities().startTaskFuture(lefttaskid, left);
		TaskFuture<SakerTaskResult> rightfut = taskcontext.getTaskUtilities().startTaskFuture(righttaskid, right);

		SakerTaskResult lefttaskres = leftfut.get();
		SakerTaskResult righttaskres = rightfut.get();
		return new SimpleSakerTaskResult<>(testEquality(taskcontext, lefttaskres, righttaskres));
	}

	public static boolean testEquality(TaskContext taskcontext, SakerTaskResult lefttaskres,
			SakerTaskResult righttaskres) {
		Object left = lefttaskres.toResult(taskcontext);
		Object right = righttaskres.toResult(taskcontext);
		return testEquality(left, right);
	}

	public static boolean testEquality(Object left, Object right) {
		if (left == right) {
			return true;
		}
		if (left == null || right == null) {
			return false;
		}
		if (left.getClass().equals(right.getClass())) {
			return left.equals(right);
		}
		//the objects have different classes
		//try promoting numbers if possible
		if (left instanceof Number && right instanceof Number) {
			if (left instanceof BigInteger) {
				BigInteger lint = (BigInteger) left;
				if (right instanceof BigDecimal) {
					return new BigDecimal(lint).equals(right);
				}
				if (right instanceof BigInteger) {
					return left.equals(right);
				}
				return lint.equals(BigInteger.valueOf(((Number) right).longValue()));
			}
			if (left instanceof BigDecimal) {
				if (right instanceof BigDecimal) {
					return left.equals(right);
				}
				if (right instanceof BigInteger) {
					return left.equals(new BigDecimal((BigInteger) right));
				}
				return left.equals(BigDecimal.valueOf(((Number) right).doubleValue()));
			}
			Number lnum = (Number) left;
			if (right instanceof BigInteger) {
				return right.equals(BigInteger.valueOf(lnum.longValue()));
			}
			if (right instanceof BigDecimal) {
				return right.equals(BigDecimal.valueOf(lnum.doubleValue()));
			}
			Number rnum = (Number) right;
			if (left instanceof Double || left instanceof Float || right instanceof Double || right instanceof Float) {
				return lnum.doubleValue() == rnum.doubleValue();
			}
			return lnum.longValue() == rnum.longValue();
		}
		return left.equals(right);
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		SakerLiteralTaskFactory lc = left.tryConstantize();
		if (lc == null) {
			return null;
		}
		SakerLiteralTaskFactory rc = right.tryConstantize();
		if (rc == null) {
			return null;
		}
		return new SakerLiteralTaskFactory(testEquality(lc.getValue(), rc.getValue()));
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new EqualsTaskFactory(cloneHelper(taskfactoryreplacements, left),
				cloneHelper(taskfactoryreplacements, right));
	}

	@Override
	public String toString() {
		return "(eq:(" + left + " == " + right + "))";
	}

}
