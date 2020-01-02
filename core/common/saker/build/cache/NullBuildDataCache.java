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
package saker.build.cache;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;

import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.StreamUtils;

public class NullBuildDataCache implements BuildDataCache {
	private static final class NullDataPublisher implements DataPublisher {
		@Override
		public void close(boolean successful) throws IOException {
		}

		@Override
		public void putField(String key, ByteArrayRegion data) {
		}

		@Override
		public ByteSink writeField(String key) {
			return StreamUtils.nullByteSink();
		}

	}

	public static final BuildDataCache INSTANCE = new NullBuildDataCache();

	@Override
	public DataPublisher publish(int hashcode, ByteArrayRegion key) {
		return new NullDataPublisher();
	}

	@Override
	public Collection<? extends DataEntry> lookup(int hashcode, Supplier<? extends ByteArrayRegion> keysupplier) {
		return Collections.emptySet();
	}

}
