package saker.build.thirdparty.saker.rmi.connection;

import java.lang.reflect.Constructor;
import java.util.Objects;

import saker.build.thirdparty.saker.rmi.exception.RMIInvalidConfigurationException;
import saker.build.thirdparty.saker.rmi.io.writer.RMIObjectWriteHandler;

/**
 * Specifies how the method parameters should be transferred when a constructor is called via RMI.
 * <p>
 * The {@link Builder} should be used for construction, or the constructor
 * {@link ConstructorTransferProperties#ConstructorTransferProperties(Constructor)} for annotation based properties
 * parsing.
 * 
 * @param <C>
 *            The declaring class of the constructor.
 */
public final class ConstructorTransferProperties<C> extends ExecutableTransferProperties<Constructor<C>> {
	private ConstructorTransferProperties() {
	}

	/**
	 * Creates an instance by examining the annotations on the parameters of the constructor.
	 * <p>
	 * The annotations of the given method is examined and a configuration is parsed for use.
	 * <p>
	 * See the possible method and parameter annotations for more information. (package
	 * <code>saker.rmi.annot.invoke</code> and package <code>saker.rmi.annot.transfer</code>)
	 * 
	 * @param constructor
	 *            The constructor for this properties.
	 * @throws RMIInvalidConfigurationException
	 *             If the configuration is invalid.
	 */
	public ConstructorTransferProperties(Constructor<C> constructor) throws RMIInvalidConfigurationException {
		super(constructor, createParameterWriteHandlers(constructor));
	}

	/**
	 * Gets the resulting type of the object construction.
	 * 
	 * @return The constructor result type.
	 */
	public Class<C> getReturnType() {
		return getExecutable().getDeclaringClass();
	}

	/**
	 * Creates a new builder instance for this class.
	 * 
	 * @param constructor
	 *            The constructor to create the builder for.
	 * @return The builder instance.
	 */
	public static <C> Builder<C> builder(Constructor<C> constructor) {
		return new Builder<>(constructor);
	}

	/**
	 * Builder class for custom construction of constructor properties.
	 * 
	 * @param <C>
	 *            The declaring class of the constructor.
	 */
	public static final class Builder<C> {
		protected Constructor<C> executable;
		protected RMIObjectWriteHandler[] parameterWriters;

		/**
		 * Creates a properties builder for the given constructor.
		 * <p>
		 * The annotations on the constructor are not taken into account.
		 * 
		 * @param constructor
		 *            The constructor to create the builder for.
		 */
		public Builder(Constructor<C> constructor) {
			Objects.requireNonNull(constructor, "constructor");

			this.executable = constructor;
			this.parameterWriters = new RMIObjectWriteHandler[constructor.getParameterCount()];
		}

		/**
		 * Sets all of write handlers for every parameter.
		 * <p>
		 * The parameter array can contain <code>null</code>, in which case the default write handler will be used.
		 * 
		 * @param writers
		 *            The write handlers for the parameters.
		 * @return <code>this</code>
		 */
		public Builder<C> parameterWriters(RMIObjectWriteHandler[] writers) {
			System.arraycopy(writers, 0, parameterWriters, 0, parameterWriters.length);
			return this;
		}

		/**
		 * Sets the write handler for the parameter at the specified index.
		 * 
		 * @param index
		 *            The index of the parameter.
		 * @param writer
		 *            The write handler. * @return <code>this</code>
		 * @return <code>this</code>
		 */
		public Builder<C> parameterWriter(int index, RMIObjectWriteHandler writer) {
			parameterWriters[index] = writer;
			return this;
		}

		/**
		 * Creates the transfer properties specified by this builder.
		 * 
		 * @return The constructed transfer properties.
		 */
		public ConstructorTransferProperties<C> build() {
			ConstructorTransferProperties<C> result = new ConstructorTransferProperties<C>();
			result.executable = executable;
			for (int i = 0; i < parameterWriters.length; i++) {
				if (parameterWriters[i] == null) {
					parameterWriters[i] = RMIObjectWriteHandler.defaultWriter();
				}
			}
			result.parameterWriters = parameterWriters;
			return result;
		}
	}
}
