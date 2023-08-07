package saker.build.scripting.model.info;

import java.util.Collection;

import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.ScriptSyntaxModel;

/**
 * Provides access to information about a build target.
 * 
 * @see ScriptSyntaxModel#getBuildTargets()
 * @since saker.build 0.8.18
 */
public interface BuildTargetInformation extends InformationHolder {
	/**
	 * Gets the name of the build target.
	 * <p>
	 * Implementations should always return a non-<code>null</code> value, however, clients gracefully handle
	 * <code>null</code> value in case of faulty implementations, and possibly ignore such targets.
	 * 
	 * @return The name.
	 */
	public String getTargetName();

	/**
	 * Gets the parameters of the build target.
	 * 
	 * @return The collection of parameters. May be <code>null</code> if the parameters are not known.
	 */
	public default Collection<? extends BuildTargetParameterInformation> getParameters() {
		return null;
	}

	/**
	 * Gets documentational information about this build target.
	 * 
	 * @return The information.
	 */
	@Override
	public default FormattedTextContent getInformation() {
		return null;
	}

}
