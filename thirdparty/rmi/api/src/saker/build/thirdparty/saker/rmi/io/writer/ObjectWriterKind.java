package saker.build.thirdparty.saker.rmi.io.writer;

/**
 * Defines possible types of {@link RMIObjectWriteHandler} implementations.
 */
public enum ObjectWriterKind {
	/**
	 * Kind for {@link ArrayComponentRMIObjectWriteHandler}.
	 */
	ARRAY_COMPONENT,
	/**
	 * Kind for {@link DefaultRMIObjectWriteHandler}.
	 */
	DEFAULT,
	/**
	 * Kind for {@link EnumRMIObjectWriteHandler}.
	 */
	ENUM,
	/**
	 * Kind for {@link RemoteOnlyRMIObjectWriteHandler}.
	 */
	REMOTE_ONLY,
	/**
	 * Kind for {@link RemoteRMIObjectWriteHandler}.
	 */
	REMOTE,
	/**
	 * Kind for {@link SelectorRMIObjectWriteHandler}.
	 */
	SELECTOR,
	/**
	 * Kind for {@link SerializeRMIObjectWriteHandler}.
	 */
	SERIALIZE,
	/**
	 * Kind for {@link WrapperRMIObjectWriteHandler}.
	 */
	WRAPPER;
}
