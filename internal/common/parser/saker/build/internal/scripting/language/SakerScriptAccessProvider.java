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
package saker.build.internal.scripting.language;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import saker.build.internal.scripting.language.meta.Versions;
import saker.build.internal.scripting.language.model.SakerScriptModellingEngine;
import saker.build.scripting.ScriptAccessProvider;
import saker.build.scripting.TargetConfigurationReader;
import saker.build.scripting.model.ScriptModellingEngine;
import saker.build.scripting.model.ScriptModellingEnvironment;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class SakerScriptAccessProvider implements ScriptAccessProvider {
	private static final AccessorKey ACCESSOR_KEY = new AccessorKey(Versions.IMPLEMENTATION_VERSION);

	@Override
	public TargetConfigurationReader createConfigurationReader() {
		return new SakerScriptTargetConfigurationReader();
	}

	@Override
	public ScriptModellingEngine createModellingEngine(ScriptModellingEnvironment modellingenvironment) {
		return new SakerScriptModellingEngine(modellingenvironment);
	}

	@Override
	public Object getScriptAccessorKey() {
		return ACCESSOR_KEY;
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return ObjectUtils.isSameClass(this, obj);
	}

	public static class AccessorKey implements Externalizable {
		private static final long serialVersionUID = 1L;
		private String version;

		/**
		 * For {@link Externalizable}.
		 */
		public AccessorKey() {
		}

		public AccessorKey(String version) {
			this.version = version;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((version == null) ? 0 : version.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			AccessorKey other = (AccessorKey) obj;
			if (version == null) {
				if (other.version != null)
					return false;
			} else if (!version.equals(other.version))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return version;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeUTF(version);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			version = in.readUTF();
		}

	}
}
