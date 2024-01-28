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
import java.util.Map;

import saker.build.internal.scripting.language.task.SakerLiteralTaskFactory;
import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.internal.scripting.language.task.result.SimpleSakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.TaskFuture;
import saker.build.task.identifier.TaskIdentifier;

public class NotEqualsTaskFactory extends BinaryOperatorTaskFactory {
	private static final long serialVersionUID = 1L;

	/**
	 * For {@link Externalizable}.
	 */
	public NotEqualsTaskFactory() {
	}

	public NotEqualsTaskFactory(SakerTaskFactory left, SakerTaskFactory right) {
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
		return new SimpleSakerTaskResult<>(!EqualsTaskFactory.testEquality(taskcontext, lefttaskres, righttaskres));
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new NotEqualsTaskFactory(cloneHelper(taskfactoryreplacements, left),
				cloneHelper(taskfactoryreplacements, right));
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
		return new SakerLiteralTaskFactory(!EqualsTaskFactory.testEquality(lc.getValue(), rc.getValue()));
	}

	@Override
	public String toString() {
		return "(neq:(" + left + " != " + right + "))";
	}

}
