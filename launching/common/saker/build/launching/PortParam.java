package saker.build.launching;

import java.util.Iterator;

import sipka.cmdline.api.Converter;

@Converter(method = "parse")
class PortParam {
	public static final PortParam DEFAULT = new PortParam(null);

	private final Integer port;

	public PortParam(Integer port) {
		this.port = port;
	}

	/**
	 * @param it
	 * @return
	 * @cmd-format &lt;int[0-65535]>
	 */
	public static PortParam parse(Iterator<? extends String> it) {
		return new PortParam(LaunchingUtils.parsePort(it.next()));
	}

	public Integer getPort() {
		return port;
	}

	public int getPort(int defaultvalue) {
		Integer result = this.port;
		if (result == null) {
			return defaultvalue;
		}
		return result;
	}
}