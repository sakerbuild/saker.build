/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package saker.build.runtime.params;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.ref.Reference;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.concurrent.locks.Lock;

import saker.apiextract.api.PublicApi;
import saker.build.runtime.classpath.ClassPathEnumerationError;
import saker.build.runtime.classpath.ClassPathServiceEnumerator;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.scripting.ScriptAccessProvider;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.classloader.MultiDataClassLoader;
import saker.build.thirdparty.saker.util.classloader.SubDirectoryClassLoaderDataFinder;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.util.config.ReferencePolicy;

/**
 * {@link ClassPathServiceEnumerator} implementation for locating the built-int script language provider.
 * <p>
 * The classloader argument may be ignored in {@link #getServices(ClassLoader)}.
 * 
 * @see #getInstance()
 */
@PublicApi
public final class BuiltinScriptAccessorServiceEnumerator
		implements ClassPathServiceEnumerator<ScriptAccessProvider>, Externalizable {
	private static final long serialVersionUID = 1L;

	private static final String CLASSNAME_BUILTIN_SCRIPT_ACCESS_PROVIDER = "saker.build.internal.scripting.language.SakerScriptAccessProvider";
	private static final String BUILTIN_PARSER_DIRECTORY = "internal/scripting";
	private static final Lock BUILTIN_SCRIPT_ACCESS_PROVIDER_LOAD_LOCK = ThreadUtils.newExclusiveLock();
	private static volatile Reference<Class<? extends ScriptAccessProvider>> builtinScriptAccessProviderClass = null;
	private static volatile Reference<ScriptAccessProvider> builtinScriptAccessProvider = null;

	/**
	 * Gets an instance of this service enumerator.
	 * <p>
	 * The result may or may not be a singleton instance.
	 * 
	 * @return An instance.
	 */
	public static ClassPathServiceEnumerator<? extends ScriptAccessProvider> getInstance() {
		return new BuiltinScriptAccessorServiceEnumerator();
	}

	/**
	 * For {@link Externalizable}.
	 * 
	 * @deprecated Use {@link #getInstance()}.
	 */
	//deprecate so the compiler warns about calling it. Only getInstance() should be used to ensure 
	//compatibility between versions
	@Deprecated
	public BuiltinScriptAccessorServiceEnumerator() {
	}

	@Override
	public Iterable<? extends ScriptAccessProvider> getServices(ClassLoader classloader)
			throws ClassPathEnumerationError {
		ScriptAccessProvider provider = ObjectUtils.getReference(builtinScriptAccessProvider);
		if (provider != null) {
			return Collections.singleton(provider);
		}
		Lock lock = BUILTIN_SCRIPT_ACCESS_PROVIDER_LOAD_LOCK;
		lock.lock();
		try {
			provider = ObjectUtils.getReference(builtinScriptAccessProvider);
			if (provider != null) {
				return Collections.singleton(provider);
			}
			Class<? extends ScriptAccessProvider> providerclass = ObjectUtils
					.getReference(builtinScriptAccessProviderClass);
			try {
				if (providerclass == null) {
					ClassLoader envcl = SakerEnvironmentImpl.class.getClassLoader();
					ClassLoader cl = new MultiDataClassLoader(envcl,
							SubDirectoryClassLoaderDataFinder.create(BUILTIN_PARSER_DIRECTORY, envcl));
					providerclass = Class.forName(CLASSNAME_BUILTIN_SCRIPT_ACCESS_PROVIDER, false, cl)
							.asSubclass(ScriptAccessProvider.class);
					builtinScriptAccessProviderClass = ReferencePolicy.createReference(providerclass);
				}
				provider = ReflectUtils.newInstance(providerclass);
				Reference<ScriptAccessProvider> providerref = ReferencePolicy.createReference(provider);
				builtinScriptAccessProvider = providerref;
				return Collections.singleton(provider);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | NoSuchMethodException | SecurityException | ClassCastException
					| ClassNotFoundException e) {
				throw new ClassPathEnumerationError(CLASSNAME_BUILTIN_SCRIPT_ACCESS_PROVIDER, e);
			}
		} finally {
			lock.unlock();
		}
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
		return getClass().getSimpleName();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}
}
