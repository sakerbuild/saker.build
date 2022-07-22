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
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import saker.apiextract.api.ExcludeApi;
import saker.build.runtime.execution.SakerLog;
import saker.build.runtime.execution.SakerLog.CommonExceptionFormat;
import saker.build.thirdparty.saker.util.ArrayUtils;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.util.exc.ExceptionView;

// for internal use
// only public so launcher classes can access this, or the IDE support bundle
@ExcludeApi
public class LaunchConfigUtils {

	public static void printArgumentExceptionOmitTraceIfSo(Throwable e) {
		if (Main.isArgumentException(e)) {
			SakerLog.printFormatException(ExceptionView.create(e), CommonExceptionFormat.NO_TRACE);
		} else {
			e.printStackTrace();
		}
	}

	/**
	 * @param inoutkeystorepass
	 *            If contains a <code>null</code> value, passwords will be attempted to be guessed. The actual password
	 *            is returned in <code>inoutkeystorepass[0]</code>.
	 */
	public static KeyStore openKeystore(String filename, Path path, char[][] inoutkeystorepass)
			throws KeyStoreException {
		Collection<String> keystoretypes;
		if (StringUtils.endsWithIgnoreCase(filename, ".jks")) {
			keystoretypes = Collections.singleton("JKS");
		} else if (StringUtils.endsWithIgnoreCase(filename, ".pfx")
				|| StringUtils.endsWithIgnoreCase(filename, ".p12")) {
			keystoretypes = Collections.singleton("PKCS12");
		} else {
			//try both of them
			keystoretypes = ImmutableUtils.asUnmodifiableArrayList("PKCS12", "JKS");
		}
		try (FileChannel fc = FileChannel.open(path)) {
			//no closing, as that would close the channel too
			InputStream is = Channels.newInputStream(fc);

			KeyStoreException[] fails = new KeyStoreException[0];
			for (String kstype : keystoretypes) {
				KeyStore ks;
				try {
					ks = KeyStore.getInstance(kstype);
				} catch (KeyStoreException e) {
					fails = ArrayUtils.appended(fails, e);
					continue;
				}
				boolean performinfer = false;
				for (char[] passarray : inoutkeystorepass) {
					if (passarray == null) {
						performinfer = true;
						continue;
					}
					try {
						//seek back to start
						fc.position(0);
						ks.load(is, passarray);
						inoutkeystorepass[0] = passarray;
						return ks;
					} catch (NoSuchAlgorithmException | CertificateException | IOException e) {
						//Keystore.load may throw IOException in case of incorrect password
						fails = ArrayUtils.appended(fails,
								new KeyStoreException("Failed to open " + kstype + " keystore: " + path, e));
					}
				}
				if (performinfer) {
					//attempt with passwords based on the file name
					String fnwithoutext = FileUtils.removeExtension(filename);
					int idx = 0;
					while (idx < fnwithoutext.length()) {
						String pass = fnwithoutext.substring(idx);
						char[] passarray = pass.toCharArray();
						try {
							//seek back to start
							fc.position(0);
							ks.load(is, passarray);
							inoutkeystorepass[0] = passarray;
							return ks;
						} catch (NoSuchAlgorithmException | CertificateException | IOException e) {
							//Keystore.load may throw IOException in case of incorrect password
							fails = ArrayUtils.appended(fails, new KeyStoreException(
									"Failed to open " + kstype + " keystore: " + path + " with password: " + pass, e));
						}
						idx = fnwithoutext.indexOf('_', idx);
						if (idx < 0) {
							break;
						}
						//don't include the _ in the password substring
						idx++;
					}
				}
			}
			if (fails.length == 1) {
				throw fails[0];
			}
			KeyStoreException e = new KeyStoreException("Failed to open keystore: " + path);
			for (KeyStoreException exc : fails) {
				e.addSuppressed(exc);
			}
			throw e;
		} catch (IOException e) {
			throw new KeyStoreException("Failed to read keystore: " + path, e);
		}
	}

	public static <E extends Throwable> KeyManagerFactory openKeyManagerFactory(KeyStore ks, Path realpath,
			char[] storepass, Collection<String> keypasswords, Function<? super String, ? extends E> exceptionfactory)
			throws E {
		Throwable[] causeexc = ObjectUtils.EMPTY_THROWABLE_ARRAY;
		boolean attemptstorepass;
		if (ObjectUtils.isNullOrEmpty(keypasswords)) {
			attemptstorepass = true;
		} else {
			attemptstorepass = false;
			for (String passstr : keypasswords) {
				if (passstr == null) {
					attemptstorepass = true;
					continue;
				}
				try {
					KeyManagerFactory kmfactory = KeyManagerFactory.getInstance("PKIX");
					kmfactory.init(ks, passstr.toCharArray());
					return kmfactory;
				} catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException e) {
					causeexc = ArrayUtils.appended(causeexc, e);
				}
			}
		}
		if (attemptstorepass) {
			try {
				KeyManagerFactory kmfactory = KeyManagerFactory.getInstance("PKIX");
				kmfactory.init(ks, storepass);
				return kmfactory;
			} catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException e) {
				causeexc = ArrayUtils.appended(causeexc, e);
			}
		}
		String msg = "Failed to create keystore key manager for: " + realpath;
		E throwexc = exceptionfactory.apply(msg);
		for (Throwable e : causeexc) {
			throwexc.addSuppressed(e);
		}
		throw throwexc;
	}

	public static <E extends Throwable> SSLContext createSSLContext(String fn, Path realpath,
			Collection<String> storepasswords, Collection<String> keypasswords,
			BiFunction<? super String, ? super Throwable, ? extends E> exceptionfactory) throws E {
		char[][] inoutkspass;
		if (ObjectUtils.isNullOrEmpty(storepasswords)) {
			inoutkspass = new char[1][];
		} else {
			inoutkspass = new char[storepasswords.size()][];
			int i = 0;
			for (String pwd : storepasswords) {
				if (pwd == null) {
					inoutkspass[i++] = null;
					continue;
				}
				inoutkspass[i++] = pwd.toCharArray();
			}
		}
		KeyStore ks;
		try {
			ks = LaunchConfigUtils.openKeystore(fn, realpath, inoutkspass);
		} catch (KeyStoreException e) {
			String msg = "Failed to open keystore.";
			throw exceptionfactory.apply(msg, e);
		}
		KeyManagerFactory kmfactory = LaunchConfigUtils.openKeyManagerFactory(ks, realpath, inoutkspass[0],
				keypasswords, msg -> exceptionfactory.apply(msg, null));
		return createSSLContext(realpath, ks, kmfactory, exceptionfactory);
	}

	public static <E extends Throwable> SSLContext createSSLContext(Path realpath, KeyStore ks,
			KeyManagerFactory kmfactory, BiFunction<? super String, ? super Throwable, ? extends E> exceptionfactory)
			throws E {
		TrustManagerFactory tmfactory;
		try {
			tmfactory = getTrustManagerFactory(ks);
		} catch (KeyStoreException | NoSuchAlgorithmException | InvalidAlgorithmParameterException
				| IllegalArgumentException e) {
			String msg = "Failed to create keystore trust manager for: " + realpath;
			throw exceptionfactory.apply(msg, e);
		}
		SSLContext sc;
		try {
			sc = SSLContext.getInstance("SSL");
			sc.init(kmfactory.getKeyManagers(), tmfactory.getTrustManagers(), null);
		} catch (NoSuchAlgorithmException | KeyManagementException e) {
			String msg = "Failed to create SSL context for: " + realpath;
			throw exceptionfactory.apply(msg, e);
		}
		return sc;
	}

	public static TrustManagerFactory getTrustManagerFactory(KeyStore ks) throws KeyStoreException,
			NoSuchAlgorithmException, InvalidAlgorithmParameterException, IllegalArgumentException {
		Set<TrustAnchor> trustanchors = new HashSet<>();

		Enumeration<String> aliases = ks.aliases();
		while (aliases.hasMoreElements()) {
			String alias = aliases.nextElement();

			if (ks.isCertificateEntry(alias)) {
				Certificate cert = ks.getCertificate(alias);
				if (cert instanceof X509Certificate) {
					trustanchors.add(new TrustAnchor((X509Certificate) cert, null));
				}
				continue;
			}

			Certificate[] chain = ks.getCertificateChain(alias);
			if (ObjectUtils.isNullOrEmpty(chain)) {
				continue;
			}
			Certificate cert = chain[0];
			int basicconstraints = ((X509Certificate) cert).getBasicConstraints();
			X509Certificate trustedcert;
			if (basicconstraints >= 0 || chain.length < 2) {
				//this certificate is a ca, or there are no signers for it
				//use this as the anchor
				trustedcert = (X509Certificate) cert;
			} else {
				//use the next certificate for the anchor
				trustedcert = (X509Certificate) chain[1];
			}
			trustanchors.add(new TrustAnchor(trustedcert, null));
		}

		TrustManagerFactory tmfactory = TrustManagerFactory.getInstance("PKIX");
		PKIXBuilderParameters pkixbuilderparams = new PKIXBuilderParameters(trustanchors, null);
//no need for revocation checks as we expect self managed certificates
//XXX make this configurable?
		pkixbuilderparams.setRevocationEnabled(false);
		tmfactory.init(new CertPathTrustManagerParameters(pkixbuilderparams));
		return tmfactory;
	}

	public static ServerSocketFactory getServerSocketFactory(SSLContext sc) {
		if (sc == null) {
			return null;
		}
		return new ClientAuthSSLServerSocketFactory(sc.getServerSocketFactory());
	}

	public static SocketFactory getSocketFactory(SSLContext sc) {
		if (sc == null) {
			return null;
		}
		return sc.getSocketFactory();
	}
}
