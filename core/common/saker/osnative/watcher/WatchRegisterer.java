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
package saker.osnative.watcher;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

@FunctionalInterface
public interface WatchRegisterer {
	public static final WatchEvent.Modifier[] EMPTY_MODIFIER_ARRAY = {};

	public WatchKey register(Path path, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers)
			throws IOException;

	public default WatchKey register(Path path, WatchEvent.Kind<?>... events) throws IOException {
		return register(path, events, EMPTY_MODIFIER_ARRAY);
	}

	public static WatchRegisterer of(WatchService service) {
		return (p, e, m) -> p.register(service, e, m);
	}
}