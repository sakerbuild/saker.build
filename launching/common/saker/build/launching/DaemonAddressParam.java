package saker.build.launching;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Iterator;

import saker.build.daemon.DaemonLaunchParameters;
import sipka.cmdline.api.Converter;

@Converter(method = "parse")
class DaemonAddressParam {
	public InetSocketAddress address;

	public DaemonAddressParam() {
	}

	public DaemonAddressParam(InetSocketAddress address) {
		this.address = address;
	}

	/**
	 * @cmd-format &lt;address>
	 */
	public static DaemonAddressParam parse(Iterator<? extends String> args) {
		String str = args.next();
		try {
			InetSocketAddress inetaddress = parseInetSocketAddress(str);
			return new DaemonAddressParam(inetaddress);
		} catch (IOException e) {
			throw new IllegalArgumentException("Failed to resolve address: " + str, e);
		}
	}

	public static InetSocketAddress parseInetSocketAddress(String str) throws UnknownHostException {
		int defaultport = DaemonLaunchParameters.DEFAULT_PORT;
		return LaunchingUtils.parseInetSocketAddress(str, defaultport);
	}

	public InetSocketAddress getSocketAddress() {
		InetSocketAddress adr = this.address;
		if (adr == null) {
			return new InetSocketAddress(InetAddress.getLoopbackAddress(), DaemonLaunchParameters.DEFAULT_PORT);
		}
		return adr;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((address == null) ? 0 : address.hashCode());
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
		DaemonAddressParam other = (DaemonAddressParam) obj;
		if (address == null) {
			if (other.address != null)
				return false;
		} else if (!address.equals(other.address))
			return false;
		return true;
	}

}