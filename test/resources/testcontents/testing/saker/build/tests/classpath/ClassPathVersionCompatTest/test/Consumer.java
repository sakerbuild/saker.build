package test;

import java.util.function.Function;
import java.util.function.Supplier;

public class Consumer implements Function<Integer, Integer> {

	public Integer apply(Integer i) {
		if (Provider.VERSION >= 4) {
			return new Provider().v4Function(new V4Holder(i)).get();
		}
		if (Provider.VERSION >= 3) {
			return new Provider().v3Function(new V3Holder(i)).val;
		}
		if (Provider.VERSION >= 2) {
			return new Provider().v2Function(i);
		}
		return i;
	}
}