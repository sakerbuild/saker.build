package saker.build.internal.scripting.language.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import saker.build.scripting.model.info.FieldInformation;
import saker.build.scripting.model.info.InformationHolder;
import saker.build.scripting.model.info.LiteralInformation;
import saker.build.scripting.model.info.SimpleFieldInformation;
import saker.build.scripting.model.info.SimpleTypeInformation;
import saker.build.scripting.model.info.TaskInformation;
import saker.build.scripting.model.info.TaskParameterInformation;
import saker.build.scripting.model.info.TypeInformation;
import saker.build.scripting.model.info.TypeInformationKind;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.function.LazySupplier;

public class TypedModelInformation {
	public static final int MODEL_INFORMATION_TYPE_UNKNOWN = -1;
	public static final int MODEL_INFORMATION_TYPE_TASK = 1;
	public static final int MODEL_INFORMATION_TYPE_FIELD = 2;
	public static final int MODEL_INFORMATION_TYPE_TYPE = 3;
	public static final int MODEL_INFORMATION_TYPE_TASK_PARAMETER = 4;
	public static final int MODEL_INFORMATION_TYPE_LITERAL = 5;
	public static final int MODEL_INFORMATION_TYPE_TARGET_PARAMETER = 6;
	public static final int MODEL_INFORMATION_TYPE_TARGET = 7;

	private final InformationHolder information;
	private final int type;
	private final transient Supplier<TypeInformation> typeInfoSupplier;

	public TypedModelInformation(TaskParameterInformation info) {
		this.information = info;
		this.type = MODEL_INFORMATION_TYPE_TASK_PARAMETER;
		this.typeInfoSupplier = info::getTypeInformation;
	}

	public TypedModelInformation(TaskInformation info) {
		this.information = info;
		this.type = MODEL_INFORMATION_TYPE_TASK;
		this.typeInfoSupplier = info::getReturnType;
	}

	public TypedModelInformation(TypeInformation info) {
		this.information = info;
		this.type = MODEL_INFORMATION_TYPE_TYPE;
		this.typeInfoSupplier = Functionals.valSupplier(info);
	}

	public TypedModelInformation(FieldInformation info) {
		this.information = info;
		this.type = MODEL_INFORMATION_TYPE_FIELD;
		this.typeInfoSupplier = info::getType;
	}

	public TypedModelInformation(LiteralInformation info) {
		this.information = info;
		this.type = MODEL_INFORMATION_TYPE_LITERAL;
		this.typeInfoSupplier = info::getType;
	}

	public TypedModelInformation(TargetParameterInformation info) {
		this.information = info;
		this.type = MODEL_INFORMATION_TYPE_TARGET_PARAMETER;
		this.typeInfoSupplier = Functionals.nullSupplier();
	}

	public TypedModelInformation(TargetInformation info) {
		this.information = info;
		this.type = MODEL_INFORMATION_TYPE_TARGET;
		this.typeInfoSupplier = LazySupplier.of(() -> {
			SimpleTypeInformation result = new SimpleTypeInformation(TypeInformationKind.OBJECT);
			Map<String, FieldInformation> outfields = new LinkedHashMap<>();
			List<TargetParameterInformation> params = info.getParameters();
			if (!ObjectUtils.isNullOrEmpty(params)) {
				for (TargetParameterInformation pinfo : params) {
					SimpleFieldInformation fieldinfo = new SimpleFieldInformation(pinfo.getName());
					fieldinfo.setInformation(pinfo.getInformation());
					outfields.put(pinfo.getName(), fieldinfo);
				}
			}
			result.setFields(outfields);
			return result;
		});
	}

	public InformationHolder getInformation() {
		return information;
	}

	public int getType() {
		return type;
	}

	public TypeInformation getTypeInformation() {
		return typeInfoSupplier.get();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((information == null) ? 0 : information.hashCode());
		result = prime * result + type;
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
		TypedModelInformation other = (TypedModelInformation) obj;
		if (information == null) {
			if (other.information != null)
				return false;
		} else if (!information.equals(other.information))
			return false;
		if (type != other.type)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + information + "]";
	}
}