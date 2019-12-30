package testing.saker.build.tests.data.adapt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface BaseItf {
	public void voidMethod();

	public void voidIntMethod(int i);

	public Integer integerMethod();

	public Long numberMethod();

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
