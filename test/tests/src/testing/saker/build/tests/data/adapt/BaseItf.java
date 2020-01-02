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
package testing.saker.build.tests.data.adapt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface BaseItf {
	public void voidMethod();

	public void voidIntMethod(int i);

	public default void onlyLocalDefaultMethod() {
	}

	public Integer integerMethod();

	public Number numberMethod();

	public void baseItfImplMethod(BaseItfImpl impl);

	public void baseItfMethod(BaseItf itf);

	public BaseItf baseItfReturning();

	public void baseItfMethodArray(BaseItf[] itf);

	public BaseItf[] baseItfReturningArray();

	public BaseItf forward(BaseItf itf);

	public Object objectForward(Object o);

	public Iterable<BaseItf> iterabler();

	public Iterable<Iterable<BaseItf>> iterablerIterabler();

	public List<BaseItf> lister();

	public List<List<BaseItf>> listerLister();

	public Collection<BaseItf> coller();

	public Collection<Collection<BaseItf>> collerColler();

	public Set<BaseItf> seter();

	public Set<Set<BaseItf>> seterSeter();

	public Map<BaseItf, BaseItf> maper();
}
