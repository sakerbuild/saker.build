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
package saker.build.internal.scripting.language.task.builtin;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;
import java.util.Objects;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.internal.scripting.language.exc.OperandExecutionException;
import saker.build.internal.scripting.language.task.SakerLiteralTaskFactory;
import saker.build.internal.scripting.language.task.SakerScriptTaskIdentifier;
import saker.build.internal.scripting.language.task.SakerTaskFactory;
import saker.build.internal.scripting.language.task.SelfSakerTaskFactory;
import saker.build.internal.scripting.language.task.result.SakerTaskResult;
import saker.build.internal.scripting.language.task.result.SimpleSakerTaskResult;
import saker.build.task.TaskContext;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;

public class PathTaskFactory extends SelfSakerTaskFactory {
	private static final long serialVersionUID = 1L;

	private SakerTaskFactory pathTaskFactory;

	/**
	 * For {@link Externalizable}.
	 */
	public PathTaskFactory() {
	}

	public PathTaskFactory(SakerTaskFactory pathTaskFactory) {
		this.pathTaskFactory = pathTaskFactory;
	}

	@Override
	public SakerTaskResult run(TaskContext taskcontext) throws Exception {
		TaskIdentifier thistaskid = taskcontext.getTaskId();
		TaskIdentifier pathtaskid = pathTaskFactory.createSubTaskIdentifier((SakerScriptTaskIdentifier) thistaskid);
		Object pathtaskres = runForResult(taskcontext, pathtaskid, pathTaskFactory).toResult(taskcontext);
		SakerPath p = convertResultToPath(pathtaskid, pathtaskres);
		if (p.isRelative()) {
			p = taskcontext.getTaskWorkingDirectory().getSakerPath().resolve(p);
		}
		SimpleSakerTaskResult<SakerPath> result = new SimpleSakerTaskResult<>(p);
		taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
		return result;
	}

	private static SakerPath convertResultToPath(TaskIdentifier pathtaskid, Object pathtaskres) {
		if (pathtaskres instanceof SakerPath) {
			return (SakerPath) pathtaskres;
		}
		String pathstr = Objects.toString(pathtaskres, null);
		if (pathstr == null) {
			throw new OperandExecutionException("Path evaluated to null.", pathtaskid);
		}
		SakerPath p;
		try {
			p = SakerPath.valueOf(pathstr);
		} catch (InvalidPathFormatException e) {
			throw new OperandExecutionException("Failed to parse path: " + pathstr, e, pathtaskid);
		}
		return p;
	}

	@Override
	public SakerTaskFactory clone(Map<SakerTaskFactory, SakerTaskFactory> taskfactoryreplacements) {
		return new PathTaskFactory(cloneHelper(taskfactoryreplacements, pathTaskFactory));
	}

	@Override
	public SakerLiteralTaskFactory tryConstantize() {
		return null;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(pathTaskFactory);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		pathTaskFactory = (SakerTaskFactory) in.readObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((pathTaskFactory == null) ? 0 : pathTaskFactory.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PathTaskFactory other = (PathTaskFactory) obj;
		if (pathTaskFactory == null) {
			if (other.pathTaskFactory != null)
				return false;
		} else if (!pathTaskFactory.equals(other.pathTaskFactory))
			return false;
		return true;
	}

}
