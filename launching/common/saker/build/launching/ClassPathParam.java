package saker.build.launching;

import java.util.Iterator;

import sipka.cmdline.api.Converter;

@Converter(method = "parse")
public class ClassPathParam {
	private String path;

	public ClassPathParam(String path) {
		this.path = path;
	}

	/**
	 * @cmd-format &lt;classpath&gt;
	 */
	public static ClassPathParam parse(Iterator<? extends String> args) {
		return new ClassPathParam(args.next());
	}

	public String getPath() {
		return path;
	}
}
