package testing.saker.build.tests.data.adapt;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	public Long numberMethod() {
		return 1L;
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
		return ImmutableUtils.asUnmodifiableList(new BaseItfImpl());
	}

	@Override
	public Iterable<Iterable<BaseItf>> iterablerIterabler() {
		return ImmutableUtils.asUnmodifiableList(lister());
	}

	@Override
	public List<BaseItf> lister() {
		return ImmutableUtils.asUnmodifiableList(new BaseItfImpl());
	}

	@Override
	public List<List<BaseItf>> listerLister() {
		return ImmutableUtils.asUnmodifiableList(ImmutableUtils.asUnmodifiableList(new BaseItfImpl()));
	}

	@Override
	public Collection<BaseItf> coller() {
		return ImmutableUtils.asUnmodifiableList(new BaseItfImpl());
	}

	@Override
	public Collection<Collection<BaseItf>> collerColler() {
		return ImmutableUtils.asUnmodifiableList(ImmutableUtils.asUnmodifiableList(new BaseItfImpl()));
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
