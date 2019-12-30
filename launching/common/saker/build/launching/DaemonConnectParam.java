package saker.build.launching;

import java.util.Iterator;

import sipka.cmdline.api.Converter;

@Converter(method = "parse")
public class DaemonConnectParam {
	public final DaemonAddressParam address;
	public final String name;

	public DaemonConnectParam(DaemonAddressParam address, String name) {
		this.address = address;
		this.name = name;
	}

	/**
	 * @param args
	 * @return
	 * @cmd-format &lt;address> &lt;name>
	 */
	public static DaemonConnectParam parse(Iterator<? extends String> args) {
		return new DaemonConnectParam(DaemonAddressParam.parse(args), args.next());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((address == null) ? 0 : address.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
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
		DaemonConnectParam other = (DaemonConnectParam) obj;
		if (address == null) {
			if (other.address != null)
				return false;
		} else if (!address.equals(other.address))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

}
