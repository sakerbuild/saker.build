package saker.build.internal.scripting.language.model.type;

import java.util.List;
import java.util.Map;
import java.util.Set;

import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.info.FieldInformation;
import saker.build.scripting.model.info.TypeInformation;

public class ForwardingTypeInformation implements TypeInformation {
	protected final TypeInformation type;

	public ForwardingTypeInformation(TypeInformation type) {
		this.type = type;
	}

	@Override
	public String getKind() {
		return type.getKind();
	}

	@Override
	public String getTypeQualifiedName() {
		return type.getTypeQualifiedName();
	}

	@Override
	public String getTypeSimpleName() {
		return type.getTypeSimpleName();
	}

	@Override
	public Map<String, FieldInformation> getFields() {
		return type.getFields();
	}

	@Override
	public Map<String, FieldInformation> getEnumValues() {
		return type.getEnumValues();
	}

	@Override
	public Set<TypeInformation> getSuperTypes() {
		return type.getSuperTypes();
	}

	@Override
	public FormattedTextContent getInformation() {
		return type.getInformation();
	}

	@Override
	public Set<TypeInformation> getRelatedTypes() {
		return type.getRelatedTypes();
	}

	@Override
	public boolean isDeprecated() {
		return type.isDeprecated();
	}

	@Override
	public List<? extends TypeInformation> getElementTypes() {
		return type.getElementTypes();
	}

	@Override
	public String toString() {
		return "ForwardingTypeInformation[" + (type != null ? "type=" + type : "") + "]";
	}

}
