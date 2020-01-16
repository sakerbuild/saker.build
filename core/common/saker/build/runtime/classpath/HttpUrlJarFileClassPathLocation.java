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
package saker.build.runtime.classpath;

import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;
import java.util.Properties;

import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWriter;
import saker.build.thirdparty.saker.rmi.io.writer.SerializeRMIObjectWriteHandler;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.FileUtils;

/**
 * {@link ClassPathLocation} implementation that loads a given JAR file from a HTTP URL.
 */
@RMIWriter(SerializeRMIObjectWriteHandler.class)
public final class HttpUrlJarFileClassPathLocation implements ClassPathLocation, Externalizable {

	private static final long serialVersionUID = 1L;

	private URL url;

	protected transient String identifier;

	/**
	 * For {@link Externalizable}.
	 */
	public HttpUrlJarFileClassPathLocation() {
	}

	/**
	 * Creates a new instance for the given URL.
	 * 
	 * @param url
	 *            The url.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the URL doesn't use the <code>http</code> or <code>https</code> protocols.
	 */
	public HttpUrlJarFileClassPathLocation(URL url) throws NullPointerException, IllegalArgumentException {
		requireHttpProtocol(url);
		this.url = url;
		this.identifier = generateIdentifier(url);
	}

	/**
	 * Validation method to check if the argument {@link URL} has either <code>http</code> or <code>https</code>
	 * protocol.
	 * 
	 * @param url
	 *            The URL to examine.
	 * @return The argument.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the URL has a protocol that is not <code>http</code> and not <code>https</code>.
	 */
	public static URL requireHttpProtocol(URL url) throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(url, "url");
		String protocol = url.getProtocol();
		if ("http".equals(protocol) || "https".equals(protocol)) {
			return url;
		}
		throw new IllegalArgumentException("URL protocol is not http or https. (" + url + ")");
	}

	@Override
	public ClassPathLoader getLoader() throws IOException {
		return new HttpUrlJarFileClassPathLoader(url);
	}

	/**
	 * Creates a new {@link ClassPathLoader} instance for the specified {@link URL}.
	 * 
	 * @param url
	 *            The URL.
	 * @return The classpath loader.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the URL has a protocol that is not <code>http</code> and not <code>https</code>.
	 */
	public static ClassPathLoader createClassPathLoader(URL url) throws NullPointerException, IllegalArgumentException {
		requireHttpProtocol(url);
		return new HttpUrlJarFileClassPathLoader(url);
	}

	/**
	 * Creates a new {@link ClassPathLoader} instance for the specified {@link URL} and target subdirectory.
	 * <p>
	 * The returned classpath loader will download the file specified by the given {@link URL} to the specified
	 * subdirectory of the load target directory.
	 * 
	 * @param url
	 *            The URL.
	 * @param subdirectory
	 *            The relative path to the subdirectory where the loading should occur. May be <code>null</code>.
	 * @return The classpath loader.
	 * @throws NullPointerException
	 *             If the URL is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the URL has a protocol that is not <code>http</code> and not <code>https</code>.
	 */
	public static ClassPathLoader createClassPathLoader(URL url, SakerPath subdirectory)
			throws NullPointerException, IllegalArgumentException {
		requireHttpProtocol(url);
		return new HttpUrlJarFileClassPathLoader(url, subdirectory);
	}

	@Override
	public String getIdentifier() {
		return this.identifier;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(url);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		url = (URL) in.readObject();
		this.identifier = generateIdentifier(url);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((url == null) ? 0 : url.hashCode());
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
		HttpUrlJarFileClassPathLocation other = (HttpUrlJarFileClassPathLocation) obj;
		if (url == null) {
			if (other.url != null)
				return false;
		} else if (!url.equals(other.url))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (url != null ? "url=" + url : "") + "]";
	}

	private String generateIdentifier(URL url) {
		return "httpurl/" + StringUtils.toHexString(FileUtils.hashString(this.getClass().getName() + url.toString()));
	}

	private static final class HttpUrlJarFileClassPathLoader implements ClassPathLoader {
		private final URL url;
		private final SakerPath subDirectory;

		public HttpUrlJarFileClassPathLoader(URL url) {
			this.url = url;
			this.subDirectory = SakerPath.EMPTY;
		}

		public HttpUrlJarFileClassPathLoader(URL url, SakerPath subDirectory) {
			this.url = url;
			this.subDirectory = subDirectory == null ? SakerPath.EMPTY
					: SakerPathFiles.requireRelativePath(subDirectory);
		}

		@Override
		public SakerPath loadTo(ProviderHolderPathKey directory) throws IOException {
			//TODO employ some caching rules

			SakerPath targetdirpath = directory.getPath().resolve(subDirectory);
			SakerFileProvider targetfp = directory.getFileProvider();

			Properties props = null;
			SakerPath propsfilepath = targetdirpath.resolve("request.properties");
			try (ByteSource in = targetfp.openInput(propsfilepath)) {
				props = new Properties();
				props.load(ByteSource.toInputStream(in));
			} catch (IOException e) {
				//failed to load properties
			}
			if (props != null) {
				String f = props.getProperty("file");
				if (f != null) {
					String propurl = props.getProperty("url");
					if (propurl != null && propurl.equals(url.toString())) {
						//only reuse the file if it was downloaded for the same URL
						try {
							if (targetfp.getFileAttributes(targetdirpath.resolve(f)).isRegularFile()) {
								//found the file that was previously downloaded
								return subDirectory.resolve(f);
							}
						} catch (IOException e) {
						}
					}
				}
			}

			Properties outprops = new Properties();
			String outputfilename = "out.jar";
			try {
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setDoInput(true);
				conn.setDoOutput(false);
				conn.setInstanceFollowRedirects(true);
				int code = conn.getResponseCode();
				if (code != HttpURLConnection.HTTP_OK) {
					throw new IOException("Invalid HTTP response code: " + code + " for URL: " + url);
				}
				String etag = conn.getHeaderField("ETag");
				if (etag != null) {
					outprops.setProperty("ETag", etag);
				}
				//https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Disposition
				String cdfield = conn.getHeaderField("Content-Disposition");
				if (cdfield != null) {
					if (cdfield.startsWith("attachment;")) {
						int fnidx = cdfield.indexOf("filename=\"");
						if (fnidx >= 0) {
							int fnstart = fnidx + 10;
							int fnendidx = cdfield.indexOf('\"', fnstart);
							if (fnendidx >= 0) {
								String specifiedfn = cdfield.substring(fnstart, fnendidx);
								if (specifiedfn.indexOf('\\') < 0 && specifiedfn.indexOf('/') < 0
										&& specifiedfn.indexOf(':') < 0) {
									//make sure that the filename does not contain malicious characters
									outputfilename = specifiedfn;
								}
							}
						}
					}
				} else {
					outputfilename = FileUtils.getLastPathNameFromURL(url.getPath());
				}
				//safety nets for some inputs
				if (ObjectUtils.isNullOrEmpty(outputfilename)) {
					outputfilename = "out.jar";
				}
				if (!FileUtils.hasExtensionIgnoreCase(outputfilename, "jar")) {
					outputfilename += ".jar";
				}
				SakerPath filenamepath = SakerPath.valueOf(outputfilename);
				if (!filenamepath.isForwardRelative()) {
					//don't allow non forward relative file names, as that would allow any file to be overwritten
					filenamepath = SakerPath.valueOf("out.jar");
				}
				SakerPath outputpath = targetdirpath.resolve(filenamepath);

				targetfp.createDirectories(targetdirpath);
				try (InputStream is = conn.getInputStream()) {
					targetfp.writeToFile(ByteSource.valueOf(is), outputpath);
				}
			} catch (Exception e) {
				throw new IOException("Failed to download: " + url, e);
			}
			outprops.setProperty("file", outputfilename);
			outprops.setProperty("url", url.toString());
			try (ByteSink out = targetfp.openOutput(propsfilepath)) {
				outprops.store(ByteSink.toOutputStream(out), null);
			} catch (IOException e) {
				//ignoreable
			}
			return subDirectory.resolve(outputfilename);
		}
	}
}
