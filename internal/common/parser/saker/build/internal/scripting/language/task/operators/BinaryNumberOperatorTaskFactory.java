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

import saker.build.internal.scripting.language.exc.OperandExecutionException;
import saker.build.internal.scripting.language.exc.SakerScriptEvaluationException;
import saker.build.internal.scripting.language.task.SakerLiteralTaskFactory;
import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.internal.scripting.language.task.result.SimpleSakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.TaskFuture;
import saker.build.task.exception.TaskExecutionFailedException;
import saker.build.task.identifier.TaskIdentifier;

public abstract class BinaryNumberOperatorTaskFactory extends BinaryOperatorTaskFactory {

	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public BinaryNumberOperatorTaskFactory() {
	}

	public BinaryNumberOperatorTaskFactory(SakerTaskFactory left, SakerTaskFactory right) {
		super(left, right);
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) throws Exception {
		SakerScriptTaskIdentifier thistaskid = (SakerScriptTaskIdentifier) taskcontext.getTaskId();
		TaskIdentifier lefttaskid = left.createSubTaskIdentifier(thistaskid);
		TaskIdentifier righttaskid = right.createSubTaskIdentifier(thistaskid);

		TaskFuture<SakerTaskResult> leftfut = taskcontext.getTaskUtilities().startTaskFuture(lefttaskid, left);
		TaskFuture<SakerTaskResult> rightfut = taskcontext.getTaskUtilities().startTaskFuture(righttaskid, right);

		Number leftres = evaluateOperand(taskcontext, lefttaskid, leftfut, "left");
		Number rightres = evaluateOperand(taskcontext, righttaskid, rightfut, "right");

		final Object result = applyOnNumbers(leftres, rightres);
		return new SimpleSakerTaskResult<>(result);
	}

	private static Number evaluateOperand(TaskContext taskcontext, TaskIdentifier taskid,
			TaskFuture<SakerTaskResult> future, String operandname) {
		Object result;
		try {
			result = future.get().get(taskcontext);
		} catch (TaskExecutionFailedException | SakerScriptEvaluationException e) {
			throw new OperandExecutionException(operandname + " operand failed to evaluate.", e, taskid);
		}
		if (result == null) {
			throw new OperandExecutionException(operandname + " operand evaluated to null.", taskid);
		}
		if (!(result instanceof Number)) {
			throw new OperandExecutionException(
					operandname + " operand is not a Number. (" + result.getClass().getName() + ")", taskid);
		}
		return (Number) result;
	}

	private Object applyOnNumbers(Number leftres, Number rightres) {
		final Object result;
		if (leftres instanceof BigInteger) {
			BigInteger lint = (BigInteger) leftres;
			if (rightres instanceof BigDecimal) {
				result = apply(new BigDecimal(lint), (BigDecimal) rightres);
			} else if (rightres instanceof BigInteger) {
				result = apply(lint, (BigInteger) rightres);
			} else {
				result = apply(lint, BigInteger.valueOf(rightres.longValue()));
			}
		} else if (leftres instanceof BigDecimal) {
			BigDecimal ldec = (BigDecimal) leftres;
			if (rightres instanceof BigDecimal) {
				result = apply(ldec, (BigDecimal) rightres);
			} else if (rightres instanceof BigInteger) {
				result = apply(ldec, new BigDecimal((BigInteger) rightres));
			} else {
				result = apply(ldec, BigDecimal.valueOf(rightres.doubleValue()));
			}
		} else {
			Number lnum = leftres;
			if (rightres instanceof BigInteger) {
				result = apply(BigInteger.valueOf(lnum.longValue()), (BigInteger) rightres);
			} else if (rightres instanceof BigDecimal) {
				result = apply(BigDecimal.valueOf(lnum.doubleValue()), (BigDecimal) rightres);
			} else {
				Number rnum = rightres;
				if (leftres instanceof Double || leftres instanceof Float || rightres instanceof Double
						|| rightres instanceof Float) {
					result = apply(lnum.doubleValue(), rnum.doubleValue());
				} else {
					result = apply(lnum.longValue(), rnum.longValue());
				}
			}
		}
		return result;
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		SakerLiteralTaskFactory lc = left.tryConstantize();
		if (lc == null) {
			return null;
		}
		Object lv = lc.getValue();
		if (!(lv instanceof Number)) {
			return null;
		}
		SakerLiteralTaskFactory rc = right.tryConstantize();
		if (rc == null) {
			return null;
		}
		Object rv = rc.getValue();
		if (!(rv instanceof Number)) {
			return null;
		}
		try {
			//XXX reduce precision of the result			
			return new SakerLiteralTaskFactory(applyOnNumbers((Number) lv, (Number) rv));
		} catch (UnsupportedOperationException e) {
			return null;
		}
	}

	protected abstract Object apply(BigDecimal left, BigDecimal right) throws UnsupportedOperationException;

	protected abstract Object apply(BigInteger left, BigInteger right) throws UnsupportedOperationException;

	protected abstract Object apply(double left, double right) throws UnsupportedOperationException;

	protected abstract Object apply(long left, long right) throws UnsupportedOperationException;
}
