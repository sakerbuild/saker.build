package saker.build.ide.support.properties;

public interface ClassPathLocationIDEProperty {
	public interface Visitor<R, P> {
		public R visit(JarClassPathLocationIDEProperty property, P param);

		public R visit(HttpUrlJarClassPathLocationIDEProperty property, P param);

		public R visit(BuiltinScriptingLanguageClassPathLocationIDEProperty property, P param);

		public R visit(NestRepositoryClassPathLocationIDEProperty property, P param);
	}

	public <R, P> R accept(Visitor<R, P> visitor, P param);

	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);
}
