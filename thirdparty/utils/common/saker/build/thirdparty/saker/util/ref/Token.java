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
package saker.build.thirdparty.saker.util.ref;

/**
 * Common superinterface for tokens which play a role in keeping weakly referenced objects from garbage collection.
 * <p>
 * These are mainly used when some objects are added to a consumer and the consumer will reference the object weakly. In
 * this case the consumer returns a token which should be kept until the client is done with the consumer.
 * <p>
 * When the token is no longer referenced, the added object to the consumer might be freely garbage collected, and
 * therefore automatically uninstalling the object from the consumer.
 * <p>
 * This is mainly useful when event listeners are installed over an RMI connection. It can cause a memory leak if the
 * consumer strongly references the listener, as abruptly terminating the RMI connection will leave the listener
 * installed. By weakly referencing, and returning a token, when the token is no longer referenced, the consumer can
 * automatically uninstall the listener when it is garbage collected.
 * <p>
 * <b>Note</b> that in most cases explicitly uninstalling the object from the consumer is beneficial rather than
 * <code>null</code>ing out the references to the token.
 * <p>
 * Tokens should strongly reference the subject of the operation.
 * 
 * @see WeakReferencedToken
 */
public interface Token {
}
