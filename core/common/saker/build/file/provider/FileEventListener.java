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
package saker.build.file.provider;

import java.nio.file.WatchService;

import saker.build.thirdparty.saker.util.ref.Token;

/**
 * Listener interface for consuming file related change events.
 * <p>
 * This interface is used when listeners are added to the file provider for listening for file changes in a directory.
 * It works basically the same way as {@link WatchService} but it is backed by an internal thread to the file provider
 * and uses push event handling. The file provider can employ internal caching for avoiding installing multiple native
 * watchers for a directory, and using a common one instead.
 */
public interface FileEventListener {
	/**
	 * Token interface for installed {@linkplain FileEventListener file event listeners}.
	 * <p>
	 * Clients receiving this token should keep them strongly referenced until the listener is no longer needed. Calling
	 * {@link #removeListener()} will uninstall it from the file provider.
	 * <p>
	 * See {@link Token} documentation for more information about tokens.
	 */
	public interface ListenerToken extends Token {
		/**
		 * Removes the associated listener to this token.
		 * <p>
		 * The file listeners might still receive some events after this call, as the events can occur out of order.
		 * Listeners should handle that scenario gracefully.
		 */
		public void removeListener();
	}

	/**
	 * Handle an event for a changed file.
	 * <p>
	 * Implementers should discover the nature of the change by querying its attributes, checking its contents, or in
	 * any other way.
	 * 
	 * @param filename
	 *            The name of the changed file. This is relative to the directory which was used for installing this
	 *            listener.
	 */
	public void changed(String filename);

	/**
	 * Notifies the listener when some events for the installed directory listener have been missed.
	 * <p>
	 * This can happen when the event listener fails to handle the events as fast as they arrive and lacks behind. The
	 * internal buffers for the file watcher can overflow, and events might be lost.
	 */
	public default void eventsMissed() {
	}

	/**
	 * Notifies the listener that it is no longer valid for the installed directory.
	 * <p>
	 * This can happen when the directory is removed, the underlying drive is removed, or in any other case based on the
	 * implementation. The listener will no longer receive events, but should handle gracefully if it does, as they
	 * might arrive out of order.
	 * <p>
	 * Implementations should requery the state of the directory, and act based on the received information. They might
	 * want to reinstall a listener on them if a directory was recreated at the path.
	 */
	public default void listenerAbandoned() {
	}
}