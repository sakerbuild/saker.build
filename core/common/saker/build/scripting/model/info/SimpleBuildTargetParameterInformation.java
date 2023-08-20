package saker.build.scripting.model.info;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.apiextract.api.PublicApi;
import saker.build.scripting.model.FormattedTextContent;

/**
 * Simple {@link BuildTargetParameterInformation} data class.
 * 
 * @since saker.build 0.8.18
 */
@PublicApi
public class SimpleBuildTargetParameterInformation implements BuildTargetParameterInformation, Externalizable {
	private static final long serialVersionUID = 1L;

	private String parameterName;
	private String type;
	private FormattedTextContent information;

	/**
	 * For {@link Externalizable}.
	 */
	public SimpleBuildTargetParameterInformation() {
	}

	/**
	 * Creates a new instance with the specified parameter name.
	 * 
	 * @param parameterName
	 *            The parameter name.
	 */
	public SimpleBuildTargetParameterInformation(String parameterName) {
		this.parameterName = parameterName;
	}

	@Override
	public String getParameterName() {
		return parameterName;
	}

	@Override
	public String getType() {
		return type;
	}

	@Override
	public FormattedTextContent getInformation() {
		return information;
	}

	/**
	 * Sets the parameter type.
	 * 
	 * @param type
	 *            The type.
	 * @see #getType()
	 * @see BuildTargetParameterInformation#TYPE_INPUT
	 * @see BuildTargetParameterInformation#TYPE_OUTPUT
	 */
	public void setType(String type) {
		this.type = type;
	}

	/**
	 * Sets the documentational information for this parameter.
	 * 
	 * @param information
	 *            The information.
	 * @see #getInformation()
	 */
	public void setInformation(FormattedTextContent information) {
		this.information = information;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(parameterName);
		out.writeObject(type);
		out.writeObject(information);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		parameterName = (String) in.readObject();
		type = (String) in.readObject();
		information = (FormattedTextContent) in.readObject();
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder(getClass().getSimpleName());
		builder.append("[parameterName=");
		builder.append(parameterName);
		builder.append(", type=");
		builder.append(type);
		builder.append(", information=");
		builder.append(information);
		builder.append("]");
		return builder.toString();
	}

}
