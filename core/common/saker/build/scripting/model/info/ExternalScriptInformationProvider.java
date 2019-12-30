package saker.build.scripting.model.info;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;

import saker.build.runtime.repository.BuildRepository;
import saker.build.scripting.model.ScriptSyntaxModel;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ObjectUtils;

/**
 * Interface for providing information for script models to improve user experience.
 * <p>
 * External script information providers are used by {@linkplain ScriptSyntaxModel scripting models} to provide better
 * information about various script elements. Information providers can be queried to provide suggestions for script
 * models, or retrieve information for a given context. Most commonly they are used to enhance information tooltips and
 * better content assist proposals based on the contextual informations in the script.
 * <p>
 * <b>Important: </b>Implementations of external script information providers should provide their results as quickly as
 * possible. It is in order to not hinder user experience, and to be able to provide information to the user quickly. If
 * they need to fetch information over the network, they should start a background thread, asynchronously retrieve the
 * related data and cache it for future callse. When the next time an information is queried, they can return the cached
 * data quickly, without contacting the servers again.
 * <p>
 * It is acceptable to return incomplete data for the first calls of the methods, and return complete data after the
 * background fetch is done.
 * <p>
 * External script information providers often have a parent object which might have shorter lifetime. (E.g. a
 * {@link BuildRepository} might be closed before the information provider is released) <br>
 * When the parent object is closed, the information provider should gracefully handle that and not throw any
 * exceptions, but return empty results. Closing the parent object should stop any background fetching.
 * <p>
 * The scheduling of background communication is implementation dependent to the information provider.
 */
public interface ExternalScriptInformationProvider {
	/**
	 * Gets information about tasks based on a hint keyword.
	 * <p>
	 * This method queries the information provider for task informations based on a hint task name keyword. The
	 * provider can interpret the keyword in any way it sees fit.
	 * <p>
	 * The returned map should be ordered based on the relevance of the related task. Implementations are recommended to
	 * use a map which keeps the insertion order, such as {@link LinkedHashMap}.
	 * <p>
	 * The hint keyword might be <code>null</code> in which case implementations should list as many tasks it can
	 * currently provide. In this case implementations are not required to list <b>all</b> task relations, only the ones
	 * it has information about. (I.e. there is no need to build a full index of all the tasks in a given
	 * implementation, as that could result in too big work. E.g. no need to retrieve all tasks from a repository, only
	 * return the ones it currently knows about.)
	 * <p>
	 * The value informations in the returned map are allowed to be lazily populated. The values in the map can be
	 * <code>null</code> to signal that they are not available or are still pending due to background retrieval.
	 * 
	 * @param tasknamekeyword
	 *            The task name hint keyword or <code>null</code>.
	 * @return The informations for the related tasks.
	 */
	public default Map<TaskName, ? extends TaskInformation> getTasks(String tasknamekeyword) {
		return Collections.emptyNavigableMap();
	}

	/**
	 * Gets the task informations based on a task name hint.
	 * <p>
	 * This method queries the information provider for task informations based on a hint task name. The provider should
	 * interpret the task name as an exact match, and the task qualifiers as a hint for the returned results.
	 * <p>
	 * The returned map should be ordered based on the relevance of the related task. Implementations are recommended to
	 * use a map which keeps the insertion order, such as {@link LinkedHashMap}.
	 * <p>
	 * The value informations in the returned map are allowed to be lazily populated. The values in the map can be
	 * <code>null</code> to signal that they are not available or are still pending due to background retrieval.
	 * 
	 * @param taskname
	 *            The task name hint. Never <code>null</code>.
	 * @return The informations for related tasks.
	 */
	public default Map<TaskName, ? extends TaskInformation> getTaskInformation(TaskName taskname) {
		return Collections.emptyNavigableMap();
	}

	/**
	 * Gets information about a task parameter.
	 * <p>
	 * This method queries the information provider for task parameter informations based on a hint task name and an
	 * exact parameter name. The parameter name passed as argument should match the informations returned in the result.
	 * The provider should interpret the task name as an exact match, and the task qualifiers as a hint for the returned
	 * results.
	 * <p>
	 * The returned map should be ordered based on the relevance of the related task. Implementations are recommended to
	 * use a map which keeps the insertion order, such as {@link LinkedHashMap}.
	 * <p>
	 * The value informations in the returned map are allowed to be lazily populated. The values in the map cannot be
	 * <code>null</code>.
	 * <p>
	 * Task parameter names should be matched using the rules specified in {@link TaskParameterInformation}. Also see
	 * {@link ScriptInfoUtils#isCompatibleParameterName(String, TaskParameterInformation)} for more information.
	 * 
	 * @param taskname
	 *            The task name hint.
	 * @param parametername
	 *            The parameter name to match.
	 * @return The task parameter informations mapped to their corresponding tasks.
	 */
	public default Map<TaskName, ? extends TaskParameterInformation> getTaskParameterInformation(TaskName taskname,
			String parametername) {
		Map<TaskName, ? extends TaskInformation> infos = getTaskInformation(taskname);
		if (ObjectUtils.isNullOrEmpty(infos)) {
			return Collections.emptyNavigableMap();
		}
		NavigableMap<TaskName, TaskParameterInformation> result = new TreeMap<>();
		for (Entry<TaskName, ? extends TaskInformation> entry : infos.entrySet()) {
			TaskInformation tinfo = entry.getValue();
			if (tinfo == null) {
				continue;
			}
			Collection<? extends TaskParameterInformation> tparams = tinfo.getParameters();
			if (ObjectUtils.isNullOrEmpty(tparams)) {
				continue;
			}
			for (TaskParameterInformation paraminfo : tparams) {
				if (ScriptInfoUtils.isCompatibleParameterName(parametername, paraminfo)) {
					TaskName entrytaskname = entry.getKey();
					//only overwrite a previous task information if that is the wildcard, as we have a more specific
					TaskParameterInformation prev = result.putIfAbsent(entrytaskname, paraminfo);
					if (prev != null) {
						if ("*".equals(prev.getParameterName())) {
							result.put(entrytaskname, paraminfo);
						}
					}
					break;
				}
			}
		}
		return result;
	}

	/**
	 * Gets information about literals based on a hint keyword and type context hint.
	 * <p>
	 * Implementations should discover possible literals that are related with the given keyword. The key word can be
	 * interpreted in any way the implementation decides to handle. As a general rule of thumb the key word should be
	 * used to discover literals that start with it, however implementations can deviate from this rule.
	 * <p>
	 * The type context information can serve as a hint to what kind of literals should be returned. It should be an
	 * instance that this information provider previously returned, however implementations are recommended to handle
	 * the case when the caller fails to adhere to this requirement.
	 * <p>
	 * The literal and type context hints may be <code>null</code>, but at least one of them will be
	 * non-<code>null</code>.
	 * <p>
	 * The resulting collection should be ordered based on relevance to the hint parameters.
	 * <p>
	 * This method is usually useful when creating completion proposals for a script model.
	 * 
	 * @param literalkeyword
	 *            The literal hint or <code>null</code> if not available.
	 * @param typecontext
	 *            The type information context hint or <code>null</code> if not available.
	 * @return The literal informations.
	 */
	public default Collection<? extends LiteralInformation> getLiterals(String literalkeyword,
			TypeInformation typecontext) {
		return Collections.emptySet();
	}

	/**
	 * Gets information about a literal with optional type context hint.
	 * <p>
	 * Implementations should return information about the literal to display to the user. The literal should be exactly
	 * matched, and not as a keyword hint.
	 * <p>
	 * The type context information may be used to determine how to interpred the argument literal. It may be
	 * <code>null</code>. It should be an instance that this information provider previously returned, however
	 * implementations are recommended to handle the case when the caller fails to adhere to this requirement.
	 * <p>
	 * This method is usually used when the user explicitly requests information about a given literal token in the
	 * script.
	 * 
	 * @param literal
	 *            The literal to query the informations for.
	 * @param typecontext
	 *            The type information context hint or <code>null</code> if not available.
	 * @return The information about the literal or <code>null</code> if not available.
	 */
	public default LiteralInformation getLiteralInformation(String literal, TypeInformation typecontext) {
		return null;
	}

}
