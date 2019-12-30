package saker.build.ide.support.properties;

public interface ClassPathServiceEnumeratorIDEProperty {
	public interface Visitor<R, P> {
		public R visit(ServiceLoaderClassPathEnumeratorIDEProperty property, P param);

		public R visit(NamedClassClassPathServiceEnumeratorIDEProperty property, P param);

		public R visit(BuiltinScriptingLanguageServiceEnumeratorIDEProperty property, P param);

		public R visit(NestRepositoryFactoryServiceEnumeratorIDEProperty property, P param);
	}

	public <R, P> R accept(Visitor<R, P> visitor, P param);

	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);
}
