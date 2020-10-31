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
package saker.build.launching;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;

import saker.build.daemon.LocalDaemonEnvironment;
import saker.build.daemon.LocalDaemonEnvironment.RunningDaemonConnectionInfo;
import saker.build.file.path.SakerPath;
import saker.build.runtime.environment.SakerEnvironmentImpl;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.LazySupplier;
import sipka.cmdline.api.Parameter;
import testing.saker.build.flag.TestFlag;

public class AuthKeystoreParamContext {
	public static final String PARAM_AUTH_KEYSTORE = "-auth-keystore";
	public static final String PARAM_AUTH_KEYPASS = "-auth-keypass";
	public static final String PARAM_AUTH_STOREPASS = "-auth-storepass";

	/**
	 * <pre>
	 * The local path to the keystore file to be used for authentication
	 * when connecting to build daemons. (Relative paths are resolved against
	 * the current working directory.)
	 * 
	 * The type of the keystore is inferred based on its extension (case insensitive).
	 *     .pfx, .p12: PKCS12
	 *     .jks: JKS (Java KeyStore)
	 *     
	 * The password of the keystore and keys can be specified using the
	 * -auth-storepass and -auth-keypass parameters. If these arguments are
	 * not set, then the loader will attempt to infer the password based on
	 * the file name.
	 * It will attempt to use the filename (without extension), and its substrings
	 * present after each '_' character.
	 * E.g. The attempted passwords for my_certificate_abcd1234.pfx will be:
	 *     my_certificate_abcd1234
	 *     certificate_abcd1234
	 *     abcd1234
	 *     
	 * The trusted anchors for establishing connections will be the
	 * TrustedCertEntry entries in the keystore, as well as the last CA
	 * signers in the certificate chain of PrivateKeyEntry entries.
	 * </pre>
	 */
	@Parameter(PARAM_AUTH_KEYSTORE)
	public SakerPath authKeystore;

	/**
	 * <pre>
	 * The store password for the keystore used for authentication.
	 * 
	 * If not specified, the loader will attempt to guess it based
	 * on the keystore file name.
	 * (See -auth-keystore)
	 * </pre>
	 */
	@Parameter(PARAM_AUTH_STOREPASS)
	public String authStorePassword;
	/**
	 * <pre>
	 * The key password for the keystore used for authentication.
	 * 
	 * If not specified, the loader will use the same password that
	 * was used for opening the store.
	 * </pre>
	 */
	@Parameter(PARAM_AUTH_KEYPASS)
	public String authKeyPassword;

	private transient LazySupplier<SSLContext> sslContextComputer = LazySupplier.of(this::computeSSLContext);

	public List<String> toCommandLineArguments() {
		SakerPath kspath = getAuthKeystorePath();
		if (kspath == null) {
			return Collections.emptyList();
		}
		List<String> result = new ArrayList<>(6);
		result.add(PARAM_AUTH_KEYSTORE);
		result.add(kspath.toString());
		if (authStorePassword != null) {
			result.add(PARAM_AUTH_STOREPASS);
			result.add(authStorePassword);
		}
		if (authKeyPassword != null) {
			result.add(PARAM_AUTH_KEYPASS);
			result.add(authKeyPassword);
		}
		return result;
	}

	public SakerPath getAuthKeystorePath() {
		if (authKeystore == null) {
			return null;
		}
		authKeystore = LaunchingUtils.absolutize(authKeystore);
		return authKeystore;
	}

	public ServerSocketFactory getServerSocketFactory() {
		SSLContext sslc = getSSLContext();
		if (sslc == null) {
			return null;
		}
		return sslc.getServerSocketFactory();
	}

	public SocketFactory getSocketFactory() {
		SSLContext sslc = getSSLContext();
		if (sslc == null) {
			return null;
		}
		return sslc.getSocketFactory();
	}

	public SocketFactory getSocketFactoryForDefaultedKeystore(SakerPath keystorepath) {
		if (this.authKeystore == null) {
			//attempt both guessing the password, and using the passed ones if any
			return LaunchingUtils.getSocketFactory(PARAM_AUTH_KEYSTORE, keystorepath,
					ObjectUtils.newHashSet(null, authStorePassword), ObjectUtils.newHashSet(null, authKeyPassword));
		}
		SSLContext sslc = getSSLContext();
		if (sslc == null) {
			return null;
		}
		return sslc.getSocketFactory();
	}

	public SSLContext getSSLContext() {
		return computeSSLContext();
	}

	private SSLContext computeSSLContext() {
		if (authKeystore == null) {
			return null;
		}
		authKeystore = LaunchingUtils.absolutize(authKeystore);
		return LaunchingUtils.getSSLContext(PARAM_AUTH_KEYSTORE, authKeystore,
				ImmutableUtils.singletonList(authStorePassword), ImmutableUtils.singletonList(authKeyPassword));
	}

	public SocketFactory getSocketFactoryForDaemonConnection(InetSocketAddress address, Path storagedirectory,
			RunningDaemonConnectionInfo[] outinfo) {
		if (authKeystore == null) {
			if (address != null) {
				InetAddress inetaddr = address.getAddress();
				if (LaunchingUtils.isLocalAddress(inetaddr)) {
					//attempt to open keystore of running daemon if any
					try {
						if (storagedirectory == null) {
							storagedirectory = SakerEnvironmentImpl.getDefaultStorageDirectory();
						}
						int port = address.getPort();
						for (RunningDaemonConnectionInfo conninfo : LocalDaemonEnvironment
								.getRunningDaemonInfos(storagedirectory)) {
							if (conninfo.getPort() != port) {
								continue;
							}
							String kspath = conninfo.getSslKeystorePath();
							if (ObjectUtils.isNullOrEmpty(kspath)) {
								//no keystore found for daemon
								break;
							}
							//attempt both guessing the password, and using the passed ones if any
							SocketFactory result = LaunchingUtils.getSocketFactory(PARAM_AUTH_KEYSTORE,
									SakerPath.valueOf(kspath), ObjectUtils.newHashSet(null, authStorePassword),
									ObjectUtils.newHashSet(null, authKeyPassword));
							if (outinfo != null) {
								outinfo[0] = conninfo;
							}
							return result;
						}
					} catch (IOException | RuntimeException e) {
						if (TestFlag.ENABLED) {
							e.printStackTrace();
						}
					}
				}
			}
			return null;
		}
		return getSocketFactory();
	}

	public SocketFactory getSocketFactoryForDaemonConnection(InetSocketAddress address) {
		return getSocketFactoryForDaemonConnection(address, null, null);
	}
}
