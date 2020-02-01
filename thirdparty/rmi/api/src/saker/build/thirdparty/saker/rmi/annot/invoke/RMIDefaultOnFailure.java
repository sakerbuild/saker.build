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
package saker.build.thirdparty.saker.rmi.annot.invoke;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import saker.build.thirdparty.saker.rmi.exception.RMIRuntimeException;

/**
 * If a remote method call fails due to RMI runtime failures, the default implementation of the method will be called.
 * <p>
 * Only interface with default implementation can be annotated with this class.
 * <p>
 * If a remote method call fails due to any RMI error, i.e. an subclass of {@link RMIRuntimeException} is thrown, then
 * the default implementation for the interface method will be called.
 * <p>
 * Note: If the method implementation explicitly throws an exception then the default implementation will not be called.
 * I.e. if a method issues a <code>throw</code> statement then the thrown exception will be propagated to the caller
 * over the connection instead of calling the default implementation.
 * <p>
 * If the default implementation throws an exception, the causing {@link RMIRuntimeException} instance will be added as
 * suppressed to it.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RMIDefaultOnFailure {
}
