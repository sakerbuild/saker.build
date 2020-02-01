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
package saker.build.thirdparty.saker.rmi.annot.transfer;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;

/**
 * Annotates the given RMI transfer point to use the {@link RMIWrapper} specified by this annotation.
 * <p>
 * The specified {@link RMIWrapper} class will be instantiated and used to transfer objects annotated on the given
 * transfer point.
 * <p>
 * The following annotated methods in <code>Stub</code> are semantically the same:
 * 
 * <pre>
 * 
 * interface Stub {
 * 	// The two following method results will be transferred in the same way
 * 
 * 	&#64;RMIWrap(NumberWrapper.class)
 * 	Number parse1(String s);
 * 
 * 	&#64;RMIWriter(NumberWriteHandler.class)
 * 	Number parse2(String s);
 * }
 * 
 * class NumberWrapper implements RMIWrapper {
 * 	//... implement
 * }
 * 
 * class NumberWriteHandler extends WrapperRMIObjectWriteHandler {
 * 	public NumberWriteHandler() {
 * 		super(NumberWrapper.clas);
 * 	}
 * }
 * </pre>
 * 
 * As the example shows, this annotation lets you use {@link RMIWrapper} to transfer objects without creating a subclass
 * to specify the proper write handler.
 * <p>
 * This annotation can be placed on types to configure class transfer properties automatically.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE })
public @interface RMIWrap {
	/**
	 * The wrapper to use during transfer.
	 * <p>
	 * The class should have a default constructor, and a constructor that can accept the target type as a single
	 * parameter.
	 * 
	 * @return The wrapper class.
	 */
	public Class<? extends RMIWrapper> value();
}
