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
package saker.build.ide.support;

import java.util.Collection;
import java.util.Map;

import saker.build.runtime.execution.SakerLog;
import saker.build.scripting.model.info.ExternalScriptInformationProvider;
import saker.build.scripting.model.info.LiteralInformation;
import saker.build.scripting.model.info.TaskInformation;
import saker.build.scripting.model.info.TaskParameterInformation;
import saker.build.scripting.model.info.TypeInformation;
import saker.build.task.TaskName;

class LazyLoadedDelegatingExternalScriptInformationProvider implements ExternalScriptInformationProvider {
	private SakerIDEProject project;
	private ExternalScriptInformationProvider provider;

	public LazyLoadedDelegatingExternalScriptInformationProvider(SakerIDEProject project) {
		this.project = project;
	}

	public void setProvider(ExternalScriptInformationProvider provider) {
		this.provider = provider;
	}

	@Override
	public Map<TaskName, ? extends TaskInformation> getTasks(String tasknamekeyword) {
		ExternalScriptInformationProvider p = provider;
		if (p != null) {
			try {
				return p.getTasks(tasknamekeyword);
			} catch (Exception e) {
				project.displayException(SakerLog.SEVERITY_WARNING, "Failed to query tasks for keyword.", e);
			}
		}
		return ExternalScriptInformationProvider.super.getTasks(tasknamekeyword);
	}

	@Override
	public Map<TaskName, ? extends TaskInformation> getTaskInformation(TaskName taskname) {
		ExternalScriptInformationProvider p = provider;
		if (p != null) {
			try {
				return p.getTaskInformation(taskname);
			} catch (Exception e) {
				project.displayException(SakerLog.SEVERITY_WARNING, "Failed to load task information.", e);
			}
		}
		return ExternalScriptInformationProvider.super.getTaskInformation(taskname);
	}

	@Override
	public Map<TaskName, ? extends TaskParameterInformation> getTaskParameterInformation(TaskName taskname,
			String parametername) {
		ExternalScriptInformationProvider p = provider;
		if (p != null) {
			try {
				return p.getTaskParameterInformation(taskname, parametername);
			} catch (Exception e) {
				project.displayException(SakerLog.SEVERITY_WARNING, "Failed to load task parameter information.", e);
			}
		}
		return ExternalScriptInformationProvider.super.getTaskParameterInformation(taskname, parametername);
	}

	@Override
	public Collection<? extends LiteralInformation> getLiterals(String literalkeyword, TypeInformation typecontext) {
		ExternalScriptInformationProvider p = provider;
		if (p != null) {
			try {
				return p.getLiterals(literalkeyword, typecontext);
			} catch (Exception e) {
				project.displayException(SakerLog.SEVERITY_WARNING, "Failed to match literals.", e);
			}
		}
		return ExternalScriptInformationProvider.super.getLiterals(literalkeyword, typecontext);
	}

	@Override
	public LiteralInformation getLiteralInformation(String literal, TypeInformation typecontext) {
		ExternalScriptInformationProvider p = provider;
		if (p != null) {
			try {
				return p.getLiteralInformation(literal, typecontext);
			} catch (Exception e) {
				project.displayException(SakerLog.SEVERITY_WARNING, "Failed to load literal information.", e);
			}
		}
		return ExternalScriptInformationProvider.super.getLiteralInformation(literal, typecontext);
	}
}