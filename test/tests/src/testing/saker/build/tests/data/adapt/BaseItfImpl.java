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

import saker.build.thirdparty.saker.util.ImmutableUtils;

public class BaseItfImpl implements BaseItf {
	@Override
	public void voidMethod() {
	}

	@Override
	public void voidIntMethod(int i) {
	}

	@Override
	public Integer integerMethod() {
		return 1;
	}

	@Override
	public Number numberMethod() {
		return 1;
	}

	@Override
	public void baseItfImplMethod(BaseItfImpl impl) {
	}

	@Override
	public void baseItfMethod(BaseItf itf) {
	}

	@Override
	public BaseItf baseItfReturning() {
		return new BaseItfImpl();
	}

	@Override
	public void baseItfMethodArray(BaseItf[] itf) {
	}

	@Override
	public BaseItf[] baseItfReturningArray() {
		return new BaseItf[] { new BaseItfImpl() };
	}

	@Override
	public BaseItf forward(BaseItf itf) {
		return itf;
	}

	@Override
	public Object objectForward(Object o) {
		return o;
	}

	@Override
	public List<BaseItf> iterabler() {
		return ImmutableUtils.asUnmodifiableArrayList(new BaseItfImpl());
	}

	@Override
	public Iterable<Iterable<BaseItf>> iterablerIterabler() {
		return ImmutableUtils.asUnmodifiableArrayList(iterabler());
	}

	@Override
	public List<BaseItf> lister() {
		return ImmutableUtils.asUnmodifiableArrayList(new BaseItfImpl());
	}

	@Override
	public List<List<BaseItf>> listerLister() {
		return ImmutableUtils.asUnmodifiableArrayList(ImmutableUtils.asUnmodifiableArrayList(new BaseItfImpl()));
	}

	@Override
	public Collection<BaseItf> coller() {
		return ImmutableUtils.asUnmodifiableArrayList(new BaseItfImpl());
	}

	@Override
	public Collection<Collection<BaseItf>> collerColler() {
		return ImmutableUtils.asUnmodifiableArrayList(ImmutableUtils.asUnmodifiableArrayList(new BaseItfImpl()));
	}

	@Override
	public Set<BaseItf> seter() {
		return ImmutableUtils.singletonSet(new BaseItfImpl());
	}

	@Override
	public Set<Set<BaseItf>> seterSeter() {
		return ImmutableUtils.singletonSet(ImmutableUtils.singletonSet(new BaseItfImpl()));
	}

	@Override
	public Map<BaseItf, BaseItf> maper() {
		return ImmutableUtils.singletonMap(new BaseItfImpl(), new BaseItfImpl());
	}
}
