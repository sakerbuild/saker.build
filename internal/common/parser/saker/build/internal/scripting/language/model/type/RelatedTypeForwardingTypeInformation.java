package saker.build.internal.scripting.language.model.type;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import saker.build.scripting.model.info.TypeInformation;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class RelatedTypeForwardingTypeInformation extends ForwardingTypeInformation {
	private Set<TypeInformation> relatedTypes;

	private RelatedTypeForwardingTypeInformation(TypeInformation type, Set<TypeInformation> relatedTypes) {
		super(type);
		this.relatedTypes = relatedTypes;
	}

	public static TypeInformation create(Collection<TypeInformation> types) {
		if (types.isEmpty()) {
			return null;
		}
		Iterator<TypeInformation> it = types.iterator();
		TypeInformation base = it.next();
		if (!it.hasNext()) {
			return base;
		}
		return new RelatedTypeForwardingTypeInformation(base, ObjectUtils.addAll(new LinkedHashSet<>(), it));
	}

	@Override
	public Set<TypeInformation> getRelatedTypes() {
		Set<TypeInformation> superreltypes = super.getRelatedTypes();
		if (ObjectUtils.isNullOrEmpty(superreltypes)) {
			return relatedTypes;
		}
		Set<TypeInformation> result = new LinkedHashSet<>(superreltypes);
		result.addAll(relatedTypes);
		return result;
	}

	@Override
	public String toString() {
		return "RelatedTypeForwardingTypeInformation["
				+ (relatedTypes != null ? "relatedTypes=" + relatedTypes + ", " : "")
				+ (super.toString() != null ? "toString()=" + super.toString() : "") + "]";
	}

}
