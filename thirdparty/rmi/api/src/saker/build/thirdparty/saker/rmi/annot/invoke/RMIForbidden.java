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

import saker.build.thirdparty.saker.rmi.exception.RMICallForbiddenException;

/**
 * Forbids calling an annotated method via RMI.
 * <p>
 * If a method is annotated with this class then calling it on a remote proxy object will cause an
 * {@link RMICallForbiddenException} to be thrown.
 * <p>
 * If the method is also annotated with {@link RMIDefaultOnFailure} then the default implementation will be called.
 * <p>
 * If the method is also annotated with {@link RMIExceptionRethrow} then the specified exception type will be thrown
 * instead.
 * <p>
 * When designing security sensitive applications, using this annotation on the server side interface definition doesn't
 * protect against remote calls issued by the client. I.e. The annotated method may still be called by a malicious
 * client.
 * 
 * @see RMIExceptionRethrow
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RMIForbidden {
}
