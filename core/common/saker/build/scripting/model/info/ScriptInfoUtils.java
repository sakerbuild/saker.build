package saker.build.scripting.model.info;

import java.util.Set;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.util.ObjectUtils;

/**
 * Utility class containing functions for dealing with scripting model informations.
 */
@PublicApi
public class ScriptInfoUtils {
	private ScriptInfoUtils() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Checks whether the given parameter name is acceptable for the place of the given parameter info.
	 * <p>
	 * The parameter name will be checked against the information provided in the target parameter information.
	 * <p>
	 * If the name of the target parameter is the wildcard (<code>"*"</code>) then <code>true</code> will be returned.
	 * Else then <code>true</code> will be returned if and only if the target parameter name or one of its aliases are
	 * the same as the argument parameter name.
	 * <p>
	 * If the argument parameter name is <code>null</code>, <code>false</code> will be returned.
	 * 
	 * @param parametername
	 *            The parameter name.
	 * @param targetparaminfo
	 *            The target parameter information.
	 * @return <code>true</code> if the parameter name is compatible for the place of parameter information.
	 */
	public static boolean isCompatibleParameterName(String parametername, TaskParameterInformation targetparaminfo) {
		if (parametername == null) {
			return false;
		}
		String pname = targetparaminfo.getParameterName();
		if ("*".equals(pname) || parametername.equals(pname)) {
			return true;
		}
		Set<String> aliases = targetparaminfo.getAliases();
		if (!ObjectUtils.isNullOrEmpty(aliases) && aliases.contains(parametername)) {
			return true;
		}
		return false;
	}

	/**
	 * Same as {@link #isCompatibleParameterName(String, TaskParameterInformation)}, but compares the possible target
	 * parameter names with {@link String#startsWith(String)} relation.
	 * 
	 * @param startswithparametername
	 *            The parameter to check if the target parameter name starts with.
	 * @param targetparaminfo
	 *            The target parameter information.
	 * @return The parameter name that is the one declared by the given parameter info and is compatible with the
	 *             argument, or <code>null</code> if incompatible.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static String isStartsWithCompatibleParameterName(String startswithparametername,
			TaskParameterInformation targetparaminfo) throws NullPointerException {
		String infopname = targetparaminfo.getParameterName();
		if (infopname == null) {
			return null;
		}
		if ("*".equals(infopname)) {
			return startswithparametername;
		}
		if (infopname.startsWith(startswithparametername)) {
			if (infopname.isEmpty()) {
				Set<String> aliases = targetparaminfo.getAliases();
				if (!ObjectUtils.isNullOrEmpty(aliases)) {
					return aliases.iterator().next();
				}
			}
			return infopname;
		}
		Set<String> aliases = targetparaminfo.getAliases();
		if (!ObjectUtils.isNullOrEmpty(aliases)) {
			for (String a : aliases) {
				if (a.startsWith(startswithparametername)) {
					if (a.isEmpty()) {
						return infopname;
					}
					return a;
				}
			}
		}
		return null;
	}
}
