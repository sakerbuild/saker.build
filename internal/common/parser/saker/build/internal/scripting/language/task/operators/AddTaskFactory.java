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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import saker.build.internal.scripting.language.exc.OperandExecutionException;
import saker.build.internal.scripting.language.exc.SakerScriptEvaluationException;
import saker.build.internal.scripting.language.task.SakerLiteralTaskFactory;
import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.internal.scripting.language.task.result.SakerListTaskResult;
import saker.build.internal.scripting.language.task.result.SakerMapTaskResult;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.internal.scripting.language.task.result.SimpleSakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.TaskFuture;
import saker.build.task.exception.TaskExecutionFailedException;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.StructuredListTaskResult;
import saker.build.task.utils.StructuredMapTaskResult;
import saker.build.task.utils.StructuredTaskResult;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.StringUtils;

public class AddTaskFactory extends BinaryOperatorTaskFactory {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public AddTaskFactory() {
	}

	public AddTaskFactory(SakerTaskFactory left, SakerTaskFactory right) {
		super(left, right);
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) throws Exception {
		SakerScriptTaskIdentifier thistaskid = (SakerScriptTaskIdentifier) taskcontext.getTaskId();
		TaskIdentifier lefttaskid = left.createSubTaskIdentifier(thistaskid);
		TaskIdentifier righttaskid = right.createSubTaskIdentifier(thistaskid);

		TaskFuture<SakerTaskResult> leftfut = taskcontext.getTaskUtilities().startTaskFuture(lefttaskid, left);
		TaskFuture<SakerTaskResult> rightfut = taskcontext.getTaskUtilities().startTaskFuture(righttaskid, right);

		Object leftres = evaluateOperand(taskcontext, lefttaskid, leftfut, "left");
		Object rightres = evaluateOperand(taskcontext, righttaskid, rightfut, "right");

		if (leftres instanceof StructuredListTaskResult && rightres instanceof StructuredListTaskResult) {
			// additions only work if both are lists
			// list + element does not work. Users should use list + [element] syntax
			List<StructuredTaskResult> listelements = new ArrayList<>();
			addToListResult((StructuredListTaskResult) leftres, listelements);
			addToListResult((StructuredListTaskResult) rightres, listelements);

			SakerListTaskResult result = new SakerListTaskResult(listelements);
			taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
			return result;
		}
		if (leftres instanceof StructuredMapTaskResult && rightres instanceof StructuredMapTaskResult) {
			NavigableMap<String, StructuredTaskResult> mapelems = new TreeMap<>(
					StringUtils.nullsFirstStringComparator());
			addToMapResult((StructuredMapTaskResult) leftres, mapelems, thistaskid);
			addToMapResult((StructuredMapTaskResult) rightres, mapelems, thistaskid);
			SakerMapTaskResult result = new SakerMapTaskResult(mapelems);
			taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
			return result;
		}
		if (leftres instanceof StructuredTaskResult) {
			leftres = ((StructuredTaskResult) leftres).toResult(taskcontext);
		}
		if (rightres instanceof StructuredTaskResult) {
			rightres = ((StructuredTaskResult) rightres).toResult(taskcontext);
		}
		if (leftres instanceof Iterable<?>) {
			if (!(rightres instanceof Iterable<?>)) {
				throw new OperandExecutionException(
						"Failed to append right operand to list with class: " + rightres.getClass().getName(),
						righttaskid);
			}
			// additions only work if both are lists
			// list + element does not work. Users should use list + [element] syntax
			List<Object> result = new ArrayList<>();
			addIterableToResult((Iterable<?>) leftres, result);
			addIterableToResult((Iterable<?>) rightres, result);
			return new SimpleSakerTaskResult<>(result);
		}
		if (rightres instanceof Iterable<?>) {
			throw new OperandExecutionException(
					"Failed to append left operand to list with class: " + leftres.getClass().getName(), righttaskid);
		}
		if (leftres instanceof Map<?, ?>) {
			if (!(rightres instanceof Map<?, ?>)) {
				throw new OperandExecutionException(
						"Failed to append right operand to map with class: " + rightres.getClass().getName(),
						righttaskid);
			}
			Map<Object, Object> result = new LinkedHashMap<>((Map<?, ?>) leftres);
			result.putAll((Map<?, ?>) rightres);
			return new SimpleSakerTaskResult<>(result);
		}
		if (rightres instanceof Map<?, ?>) {
			throw new OperandExecutionException(
					"Failed to append left operand to map with class: " + leftres.getClass().getName(), lefttaskid);
		}
		//none of them are maps
		//try adding as numbers
		if (leftres instanceof Number && rightres instanceof Number) {
			final Number result = applyOnNumbers(leftres, rightres);
			return new SimpleSakerTaskResult<>(result);
		}
		throw new OperandExecutionException("Unsupported addition between types: " + leftres.getClass().getName()
				+ " + " + rightres.getClass().getName(), thistaskid);
	}

	private static Object evaluateOperand(TaskContext taskcontext, TaskIdentifier taskid,
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
		return result;
	}

	private static Number applyOnNumbers(Object leftres, Object rightres) {
		final Number result;
		if (leftres instanceof BigInteger) {
			BigInteger lint = (BigInteger) leftres;
			if (rightres instanceof BigDecimal) {
				result = apply(new BigDecimal(lint), (BigDecimal) rightres);
			} else if (rightres instanceof BigInteger) {
				result = apply(lint, (BigInteger) rightres);
			} else {
				result = apply(lint, BigInteger.valueOf(((Number) rightres).longValue()));
			}
		} else if (leftres instanceof BigDecimal) {
			BigDecimal ldec = (BigDecimal) leftres;
			if (rightres instanceof BigDecimal) {
				result = apply(ldec, (BigDecimal) rightres);
			} else if (rightres instanceof BigInteger) {
				result = apply(ldec, new BigDecimal((BigInteger) rightres));
			} else {
				result = apply(ldec, BigDecimal.valueOf(((Number) rightres).doubleValue()));
			}
		} else {
			Number lnum = (Number) leftres;
			if (rightres instanceof BigInteger) {
				result = apply(BigInteger.valueOf(lnum.longValue()), (BigInteger) rightres);
			} else if (rightres instanceof BigDecimal) {
				result = apply(BigDecimal.valueOf(lnum.doubleValue()), (BigDecimal) rightres);
			} else {
				Number rnum = (Number) rightres;
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

	protected static BigDecimal apply(BigDecimal left, BigDecimal right) {
		return left.add(right);
	}

	protected static BigInteger apply(BigInteger left, BigInteger right) {
		return left.add(right);
	}

	protected static Number apply(double left, double right) {
		return left + right;
	}

	protected static Number apply(long left, long right) {
		long result = left + right;
		if (((left ^ result) & (right ^ result)) < 0) {
			//as seen in Math.addExact
			return apply(BigInteger.valueOf(left), BigInteger.valueOf(right));
		}
		return result;
	}

	@Override
	@SuppressWarnings("unchecked")
	public SakerLiteralTaskFactory tryConstantize() {
		SakerLiteralTaskFactory lc = left.tryConstantize();
		if (lc == null) {
			return null;
		}
		Object leftres = lc.getValue();
		if (leftres == null) {
			return null;
		}
		SakerLiteralTaskFactory rc = right.tryConstantize();
		if (rc == null) {
			return null;
		}
		Object rightres = rc.getValue();
		if (rightres == null) {
			return null;
		}
		if (leftres instanceof List) {
			List<?> ll = (List<?>) leftres;
			List<Object> result = new ArrayList<>(ll);
			if (rightres instanceof List) {
				List<?> rl = (List<?>) rightres;
				result.addAll(rl);
			} else {
				result.add(rightres);
			}
			return new SakerLiteralTaskFactory(result);
		}
		if (leftres instanceof Map) {
			if (!(rightres instanceof Map)) {
				return null;
			}
			NavigableMap<String, Object> result = new TreeMap<>((Map<String, ?>) leftres);
			result.putAll((Map<? extends String, ? extends Object>) rightres);
			return new SakerLiteralTaskFactory(result);
		}
		if (rightres instanceof Map) {
			return null;
		}
		if (leftres instanceof Number && rightres instanceof Number) {
			//XXX reduce precision of the result
			return new SakerLiteralTaskFactory(applyOnNumbers(leftres, rightres));
		}
		return null;
	}

	private static void addIterableToResult(Iterable<?> value, List<Object> result) {
		for (Object e : (Iterable<?>) value) {
			result.add(e);
		}
	}

	private static void addToMapResult(StructuredMapTaskResult value, Map<String, StructuredTaskResult> mapelems,
			TaskIdentifier thistaskid) {
		value.forEach((key, vtid) -> {
			StructuredTaskResult present = mapelems.putIfAbsent(key, vtid);
			if (present != null) {
				throw new OperandExecutionException("Map key present multiple times: " + key, thistaskid);
			}
		});

	}

	private static void addToListResult(StructuredListTaskResult value, List<StructuredTaskResult> result) {
		Iterator<? extends StructuredTaskResult> it = value.resultIterator();
		while (it.hasNext()) {
			result.add(it.next());
		}
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new AddTaskFactory(cloneHelper(taskfactoryreplacements, left),
				cloneHelper(taskfactoryreplacements, right));
	}

	@Override
	public String toString() {
		return "(add:(" + left + " + " + right + "))";
	}

}
