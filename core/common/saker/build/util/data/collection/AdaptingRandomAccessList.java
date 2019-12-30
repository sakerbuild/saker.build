package saker.build.util.data.collection;

import java.util.List;
import java.util.RandomAccess;

public class AdaptingRandomAccessList extends AdaptingList implements RandomAccess {
	public AdaptingRandomAccessList(ClassLoader cl, List<?> iterable) {
		super(cl, iterable);
	}
}
