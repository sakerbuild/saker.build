package saker.build.ide.support.properties;

import saker.build.thirdparty.saker.util.ObjectUtils;

public class NestRepositoryFactoryServiceEnumeratorIDEProperty implements ClassPathServiceEnumeratorIDEProperty {

	public NestRepositoryFactoryServiceEnumeratorIDEProperty() {
	}

	@Override
	public <R, P> R accept(Visitor<R, P> visitor, P param) {
		return visitor.visit(this, param);
	}

	@Override
	public int hashCode() {
		return getClass().getName().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return ObjectUtils.isSameClass(this, obj);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + "]";
	}
}
