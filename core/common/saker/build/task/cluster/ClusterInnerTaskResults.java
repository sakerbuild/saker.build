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
package saker.build.task.cluster;

import saker.build.task.InnerTaskResultHolder;
import saker.build.task.InnerTaskResults;
import saker.build.task.InternalInnerTaskResults;
import saker.build.task.exception.InnerTaskInitializationException;

public class ClusterInnerTaskResults<R> implements InnerTaskResults<R> {
	private ClusterTaskContext taskContext;
	private InnerTaskResults<R> actualResults;

	public ClusterInnerTaskResults(ClusterTaskContext taskContext, InnerTaskResults<R> actualResults) {
		this.taskContext = taskContext;
		this.actualResults = actualResults;
	}

	@SuppressWarnings("unchecked")
	@Override
	public InnerTaskResultHolder<R> getNext() throws InterruptedException, InnerTaskInitializationException {
		taskContext.requireCalledOnMainThread(false);
		return ((InternalInnerTaskResults<R>) actualResults).internalGetNextOnTaskThread();
	}

	@Override
	public void cancelDuplicationOptionally() {
		actualResults.cancelDuplicationOptionally();
	}

}
