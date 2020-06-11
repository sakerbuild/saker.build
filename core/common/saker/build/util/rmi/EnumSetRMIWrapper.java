package saker.build.util.rmi;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class EnumSetRMIWrapper implements RMIWrapper {

	private Set set;

	public EnumSetRMIWrapper() {
	}

	public EnumSetRMIWrapper(Set set) {
		this.set = set;
	}

	@Override
	public void writeWrapped(RMIObjectOutput out) throws IOException {
		for (Object o : set) {
			out.writeEnumObject(o);
		}
		out.writeObject(null);
	}

	@Override
	public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
		Object first = in.readObject();
		if (first == null) {
			set = Collections.emptySet();
		} else {
			set = EnumSet.of((Enum) first);
			while (true) {
				Object o = in.readObject();
				if (o == null) {
					break;
				}
				set.add(o);
			}
		}
	}

	@Override
	public Object resolveWrapped() {
		return set;
	}

	@Override
	public Object getWrappedObject() {
		throw new UnsupportedOperationException();
	}

}
