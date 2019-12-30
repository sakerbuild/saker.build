package saker.build.ide.support;

import java.util.Collection;
import java.util.Map;

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
				project.displayException(e);
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
				project.displayException(e);
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
				project.displayException(e);
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
				project.displayException(e);
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
				project.displayException(e);
			}
		}
		return ExternalScriptInformationProvider.super.getLiteralInformation(literal, typecontext);
	}
}