package saker.build.thirdparty.saker.util.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Pattern;

import saker.build.thirdparty.saker.util.ArrayUtils;

/**
 * Utility class containing functions for handling files, and file paths.
 */
public class FileUtils {
	/**
	 * The default hashing algorithm that this class uses.
	 */
	public static final String DEFAULT_FILE_HASH_ALGORITHM = "MD5";

	private static final Pattern PATTERN_SEPARATOR_SPLIT = Pattern.compile("[\\\\/]+");

	/**
	 * Removes the extension from the given file name.
	 * <p>
	 * The extension is the part of the file name that is after the last <code>'.'</code> dot character. If there are no
	 * dot characters in the file name, it has no extension.
	 * <p>
	 * This method takes the name of a file, and removes the extension part, including the dot.
	 * <p>
	 * <b>Note:</b> if a file has an extension where the dot is the first character in the name, this method will not
	 * remove that. E.g. a name with a format of <code>".dotfile"</code> is unchanged.
	 * 
	 * @param name
	 *            The file name.
	 * @return The name without the extension or <code>null</code> if the argument was <code>null</code>.
	 */
	public static String removeExtension(String name) {
		if (name == null) {
			return null;
		}
		int index = name.lastIndexOf('.');
		if (index <= 0) {
			return name;
		}
		return name.substring(0, index);
	}

	/**
	 * Gets the extension from the given file name.
	 * <p>
	 * The extension is the part of the file name that is after the last <code>'.'</code> dot character. If there are no
	 * dot characters in the file name, it has no extension.
	 * <p>
	 * <b>Note:</b> if a file has an extension where the dot is the first character in the name, this method will not
	 * return that. E.g. a name with a format of <code>".dotfile"</code> is considered to have no extension.
	 * 
	 * @param name
	 *            The file name.
	 * @return The extension of the given name, or <code>null</code> if the name doesn't have one.
	 */
	public static String getExtension(String name) {
		if (name == null) {
			return null;
		}
		int index = name.lastIndexOf('.');
		if (index <= 0) {
			return null;
		}
		return name.substring(index + 1);
	}

	/**
	 * Changes the extension of a file name.
	 * <p>
	 * The extension is the part of the file name that is after the last <code>'.'</code> dot character. If there are no
	 * dot characters in the file name, it has no extension.
	 * <p>
	 * This method takes the name of a file, removes the extension part, and appends it with the extension argument..
	 * <p>
	 * <b>Note:</b> if a file has an extension where the dot is the first character in the name, this method will not
	 * chane that. E.g. a name with a format of <code>".dotfile"</code> is unchanged.
	 * 
	 * @param name
	 *            The file name.
	 * @param extension
	 *            The extension. This shouldn't contain a preceeding dot.
	 * @return The name with the change extension, or <code>null</code> if the name argument was <code>null</code>.
	 * @throws NullPointerException
	 *             If the name is non-<code>null</code>, and the extension is <code>null</code>.
	 */
	public static String changeExtension(String name, String extension) throws NullPointerException {
		if (name == null) {
			return null;
		}
		Objects.requireNonNull(extension, "extension");
		int index = name.lastIndexOf('.');
		if (index <= 0) {
			return name;
		}
		return name.substring(0, index + 1) + extension;
	}

	/**
	 * Checks if the given name has the specified extension in a case-insensitive manner.
	 * <p>
	 * The extension of a file name is determined by taking the substring that is after the last <code>'.'</code> dot
	 * character in the name. If there is no dot character in the name, the name is considered to have no extension.
	 * <p>
	 * <b>Note:</b> if a file has an extension where the dot is the first character in the name, this method will return
	 * <code>false</code> for it. E.g. a name with a format of <code>".dotfile"</code> is not be considered to have the
	 * <code>"dotfile"</code>.
	 * 
	 * @param name
	 *            The name to check the extension for.
	 * @param ext
	 *            The extension to check. This argument should not contain the preceeding <code>'.'</code> dot in it.
	 * @return <code>true</code> if the given name has the specified extension.
	 * @throws NullPointerException
	 */
	public static boolean hasExtensionIgnoreCase(String name, String ext) throws NullPointerException {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(ext, "ext");
		int baselen = name.length();
		int extlen = ext.length();
		if (baselen < extlen + 1) {
			return false;
		}
		int dotidx = baselen - extlen - 1;
		if (dotidx == 0) {
			return false;
		}
		return name.charAt(dotidx) == '.' && name.regionMatches(true, baselen - extlen, ext, 0, extlen);
	}

	/**
	 * Checks if the argument file attributes have the same {@linkplain BasicFileAttributes#size() size} and
	 * {@linkplain BasicFileAttributes#lastModifiedTime() last modified time}.
	 * <p>
	 * This method handles <code>null</code> arguments. If both of them are <code>null</code>, returns
	 * <code>true</code>. If only one of them, returns <code>false</code>.
	 * 
	 * @param a
	 *            The first attributes.
	 * @param b
	 *            The second attributes.
	 * @return <code>true</code> if the size and last modification time is the same from the argument attributes.
	 */
	public static boolean isSameModificationAndSize(BasicFileAttributes a, BasicFileAttributes b) {
		if (a == b) {
			return true;
		}
		if (a == null || b == null) {
			return false;
		}
		return a.size() == b.size() && a.lastModifiedTime().compareTo(b.lastModifiedTime()) == 0;
	}

	/**
	 * Updates the given message digest with the file contents at the given path.
	 * <p>
	 * If the path denotes a directory, then it will be recursively enumerated, and all files will be used to update the
	 * argument message digest. The files are enumerated in a deterministic order, therefore if this method is invoked
	 * multiple times, it should return the same hash if the files have not changed meanwhile.
	 * <p>
	 * If the path denotes a file, then the file will be fully read, and the contents are used to update the message
	 * digest.
	 * <p>
	 * If the method fails to open a file, an {@link IOException} is thrown.
	 * 
	 * @param path
	 *            The path to hash.
	 * @param digest
	 *            The message digest to update with the file contents.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static void hashFiles(Path path, MessageDigest digest) throws IOException, NullPointerException {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(digest, "digest");
		if (Files.isDirectory(path)) {
			NavigableSet<Path> subfiles = new TreeSet<>();
			Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					subfiles.add(file);
					return super.visitFile(file, attrs);
				}
			});
			if (!subfiles.isEmpty()) {
				byte[] copybuf = new byte[4 * 1024];
				for (Path f : subfiles) {
					try (InputStream is = Files.newInputStream(f)) {
						StreamUtils.copyStream(is, digest, copybuf);
					}
				}
			}
			digest.digest();
			return;
		}
		try (InputStream is = Files.newInputStream(path)) {
			StreamUtils.copyStream(is, digest);
		}
	}

	/**
	 * Hashes the files at the given path using the {@linkplain #getDefaultFileHasher() default hasher}.
	 * <p>
	 * See {@link #hashFiles(Path, MessageDigest)} for operational details.
	 * 
	 * @param path
	 *            The path to hash.
	 * @return The hash.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the path is <code>null</code>.
	 */
	public static byte[] hashFiles(Path path) throws IOException, NullPointerException {
		MessageDigest digest = getDefaultFileHasher();
		hashFiles(path, digest);
		return digest.digest();
	}

	/**
	 * Hashes the bytes in the given input stream with the {@linkplain #getDefaultFileHasher() default hasher}.
	 * <p>
	 * The input stream will be read until it returns no more bytes.
	 * 
	 * @param is
	 *            The input stream.
	 * @return The hash of the bytes from the input stream.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static byte[] hashInputStream(InputStream is) throws IOException, NullPointerException {
		Objects.requireNonNull(is, "input");
		MessageDigest digest = getDefaultFileHasher();
		StreamUtils.copyStream(is, digest);
		return digest.digest();
	}

	/**
	 * Hashes a string using the {@linkplain #getDefaultFileHasher() default hasher}.
	 * <p>
	 * The string will be converted to a byte sequence by encoding it using UTF-8.
	 * 
	 * @param s
	 *            The string to hash.
	 * @return The hash.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static byte[] hashString(String s) throws NullPointerException {
		Objects.requireNonNull(s, "string");
		MessageDigest digest = getDefaultFileHasher();
		digest.update(s.getBytes(StandardCharsets.UTF_8));
		return digest.digest();
	}

	/**
	 * Gets the message digest that has the default hash algorithm defined by this class.
	 * <p>
	 * Callers should not rely on the returned digest to have a specific algorithm, and they shouldn't rely on it not
	 * chaning between different invocation of the Java Virtual Machine.
	 * <p>
	 * The default hash algorithm may change between library releases, and may be configureable by external properties
	 * in the future. Callers can only rely on the fact that the default file hasher algorithm will stay the same in the
	 * same JVM. (I.e. it will not change during the lifetime of the application.)
	 * 
	 * @return A message digest with the default hash algorithm.
	 * @throws AssertionError
	 *             If the default hash algorithm was not found.
	 * @see #DEFAULT_FILE_HASH_ALGORITHM
	 */
	public static MessageDigest getDefaultFileHasher() throws AssertionError {
		try {
			//XXX make the default algorithm customizable?
			return MessageDigest.getInstance(DEFAULT_FILE_HASH_ALGORITHM);
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError(e);
		}
	}

	/**
	 * Gets the {@link WatchEvent.Modifier} that is the <code>FILE_TREE</code> modifier.
	 * <p>
	 * The <code>FILE_TREE</code> modifier is an API in the JVM that may not be supported on all platforms. Therefore,
	 * it is necessary to error handle its absence, and this function will return <code>null</code> if it was not found.
	 * 
	 * @return The <code>FILE_TREE</code> watch event modifier or <code>null</code> if not supported.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static WatchEvent.Modifier getFileTreeExtendedWatchEventModifier() {
		try {
			Class clazz = Class.forName("com.sun.nio.file.ExtendedWatchEventModifier", false, null);
			return (Modifier) Enum.valueOf(clazz, "FILE_TREE");
		} catch (ClassNotFoundException | IllegalArgumentException e) {
		}
		return null;
	}

	/**
	 * Splits the given path into separate parts by its contained slashes (<code>'\\'</code> and <code>'/'</code>).
	 * <p>
	 * Multiple consecutive slashes will be stripped, e.g. <code>"a//b"</code> will return <code>{ "a", "b" }</code>.
	 * <p>
	 * If there's a leading slash, an empty string will be the first element. Trailing slash characters will be removed.
	 * An empty string will not be the last element, if the last character is a slash character.
	 * 
	 * @param path
	 *            The path to split.
	 * @return The path split by its contained slash chracters.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static String[] splitPath(CharSequence path) throws NullPointerException {
		Objects.requireNonNull(path, "path");
		return PATTERN_SEPARATOR_SPLIT.split(path);
	}

	/**
	 * Checks if the file contents at the given path matches the argument byte array.
	 * <p>
	 * This method will open the file, check if the size of the file matches the argument array, and if so, will read
	 * the file and check if the contents match.
	 * <p>
	 * This method doesn't do any locking and such to ensure that the file is not modified during the contents are
	 * checked. The result of this method should not be used in a secure context, meaning that the result of this method
	 * should be considered immediately stale, as the file system is a shared resource, and others may have modified the
	 * checked file after this method returns.
	 * <p>
	 * If this method returns <code>true</code>, that doesn't ensure that the file contents are, and even were ever the
	 * same as the expected bytes. It just tells the caller that an input was opened to the file, and the same bytes
	 * were sequentally read from the input, during this method call.
	 * 
	 * @param filepath
	 *            The path of the file to check.
	 * @param bytes
	 *            The expected byte contents of the file.
	 * @return <code>true</code> if the file contents are the same as the argument bytes.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static boolean isFileBytesSame(Path filepath, byte[] bytes) throws NullPointerException {
		Objects.requireNonNull(filepath, "file path");
		Objects.requireNonNull(bytes, "bytes");
		try (SeekableByteChannel channel = Files.newByteChannel(filepath, StandardOpenOption.READ)) {
			if (channel.size() != bytes.length) {
				//size doesnt match
				return false;
			}
			try (InputStream is = Channels.newInputStream(channel)) {
				byte[] buf = new byte[1024 * 4];
				int offset = 0;
				while (true) {
					int r = is.read(buf);
					if (r <= 0) {
						return offset == bytes.length;
					}
					if (offset + r > bytes.length) {
						//read more bytes than the data length
						return false;
					}
					for (int i = 0; i < r; i++) {
						if (bytes[offset++] != buf[i]) {
							return false;
						}
					}
					//all bytes in the data and buf matches
				}
			}
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * Gets the last path name from an URL formatted string.
	 * <p>
	 * The method works in the following way:
	 * <ol>
	 * <li>If the URL contains a <code>'?'</code> character, it and any consecutive part is removed. This is in order to
	 * remove the query string from the URL such as <code>"http://example.com/path?some=query"</code> will be
	 * <code>"http://example.com/path"</code>.</li>
	 * <li>If the URL contains a <code>'#'</code> character, it and any consecutive part is removed. This is to remove
	 * any anchor parts from the URL, such as <code>"http://example.com/path#anchor"</code> will be
	 * <code>"http://example.com/path"</code>.</li>
	 * <li>From the remaining string, the last path name will be retrieved, that is the last part after any slash
	 * characters. E.g. <code>"http://example.com/path"</code> will be <code>"path"</code>.</li>
	 * </ol>
	 * If there are no path parts in the URL, <code>null</code> will be returned. (E.g. for <code>"?some=query"</code>.)
	 * <p>
	 * Note that, for simple URLs, the result might be the same as the domain name. (E.g. for
	 * <code>"http://example.com"</code>, <code>"example.com"</code> will be returned.)
	 * <p>
	 * The method doesn't strip trailing slash characters. (E.g. for <code>"http://example.com/"</code>,
	 * <code>null</code> will be returned.)
	 * 
	 * @param url
	 *            The URL to get the last path name from.
	 * @return The last path name, or <code>null</code> if not present.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static String getLastPathNameFromURL(String url) throws NullPointerException {
		Objects.requireNonNull(url, "url");
		int qidx = url.indexOf('?');
		int hidx = url.indexOf('#');
		int endidx = url.length();
		if (qidx >= 0) {
			endidx = Math.min(qidx, endidx);
		}
		if (hidx >= 0) {
			endidx = Math.min(hidx, endidx);
		}
		String path = url.substring(0, endidx);
		int bsidx = path.lastIndexOf('\\') + 1;
		int sidx = path.lastIndexOf('/') + 1;
		int startidx = Math.max(bsidx, sidx);
		String res = path.substring(startidx);
		if (res.isEmpty()) {
			return null;
		}
		return res;
	}

	/**
	 * Checks if the given char sequence contains only slash characters.
	 * <p>
	 * The slash characters are: <code>'/'</code> and <code>'\\'</code>.
	 * <p>
	 * An empty string is considered to contains only slash characters.
	 * 
	 * @param cs
	 *            The char sequence to examine.
	 * @return <code>true</code> if there are only slash characters in the argument.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static boolean isSlashesOnly(CharSequence cs) throws NullPointerException {
		Objects.requireNonNull(cs, "char sequence");
		int len = cs.length();
		for (int i = 0; i < len; i++) {
			char c = cs.charAt(i);
			if (c != '/' && c != '\\') {
				return false;
			}
		}
		return true;
	}

	/**
	 * Finds the index at which the given input of bytes and the contents of the specified file doesn't equal.
	 * <p>
	 * This method will read bytes from the argument {@link InputStream} and the file specified by the given
	 * {@link Path} simultaneously, and determine the index of the first byte that doesn't equal.
	 * <p>
	 * The method internally buffers the bytes from the input stream, and will leave the input stream in a state in
	 * which some bytes will be lost by the reader.
	 * <p>
	 * If the input stream contains the same amount of bytes, and all bytes match the ones in the file, this method will
	 * return -1, signaling that all bytes matched.
	 * <p>
	 * If the file doesn't exists, or reading from any of the inputs fail with exception it will be forwarded to the
	 * caller.
	 * <p>
	 * In any other cases, the method will return the index at which either a mismatching byte was found, or any of the
	 * inputs ended prematurely.
	 * <p>
	 * This method can handle large files without incurring heavy memory overhead. This method is similar to the
	 * {@link Arrays#mismatch(byte[], byte[])} method (available on JDK9+), or
	 * {@link ArrayUtils#mismatch(byte[], int, byte[], int, int)}.
	 * 
	 * @param is
	 *            The input stream to validate the file against.
	 * @param filepath
	 *            The file to check the bytes of.
	 * @return The index of the first mismatching byte, or -1 if all bytes equal.
	 * @throws IOException
	 *             In case of any reading error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static long fileMismatch(InputStream is, Path filepath) throws IOException, NullPointerException {
		Objects.requireNonNull(is, "input stream");
		Objects.requireNonNull(filepath, "file path");
		long compareoffset = 0;
		try (SeekableByteChannel readchannel = Files.newByteChannel(filepath, StandardOpenOption.READ)) {
			InputStream channelin = Channels.newInputStream(readchannel);

			byte[] channelbuffer = new byte[StreamUtils.DEFAULT_BUFFER_SIZE];
			byte[] isbuffer = new byte[StreamUtils.DEFAULT_BUFFER_SIZE];
			int isbuffersize = 0;
			int isbufferoffset = 0;

			int channelbuffersize = channelin.read(channelbuffer);
			int channelbufferoffset = 0;
			while (true) {
				while (channelbuffersize - channelbufferoffset > 0) {
					//there are bytes in the channel buffer
					//compare them to the input
					if (isbuffersize - isbufferoffset <= 0) {
						//no bytes in the input buffer
						isbuffersize = is.read(isbuffer);
						if (isbuffersize <= 0) {
							//no more bytes in the input
							//the file was truncated
							return compareoffset;
						}
						isbufferoffset = 0;
					}
					//compare the bytes in the channel buffer with the input buffer
					int bcount = Math.min(channelbuffersize - channelbufferoffset, isbuffersize - isbufferoffset);
					int mismatchindex = ArrayUtils.mismatch(isbuffer, isbufferoffset, channelbuffer,
							channelbufferoffset, bcount);
					if (mismatchindex < 0) {
						//all bytes equal
						channelbufferoffset += bcount;
						isbufferoffset += bcount;
						compareoffset += bcount;
						continue;
					}
					//there was a mismatch between the byte arrays
					//update the bytes of the file from the given offset
					//truncate the file, which also sets the position
					return compareoffset + mismatchindex;
				}
				//no more bytes in the channel buffer

				channelbuffersize = channelin.read(channelbuffer);
				if (channelbuffersize <= 0) {
					//no more bytes available in the channel
					//the file was extended, with all the bytes matching from the stream
					if (isbuffersize - isbufferoffset > 0) {
						return compareoffset;
					}
					isbuffersize = is.read(isbuffer);
					if (isbuffersize <= 0) {
						//no more bytes in the input, all bytes match
						return -1;
					}
					return compareoffset;
				}
				channelbufferoffset = 0;
				//read some bytes from the channel
				//continue the loop to compare with the input
				continue;
			}
		}
	}

	/**
	 * Writes the contents of the input stream to the given file, and checking the bytes for equality before performing
	 * write operations.
	 * <p>
	 * This method will only write the bytes from the input stream to the given file if and only if they doesn't already
	 * equal. This means that if the file contents on the disk already matches the contents of the {@link InputStream},
	 * no writing operations will be issued.
	 * <p>
	 * The method simultaneously reads the input from the stream and file, and only starts writing the file if a
	 * mismatch is found. See {@link #fileMismatch(InputStream, Path)}.
	 * <p>
	 * If the file cannot be opened for writing, the byte contents will still be checked for equality, and the method
	 * will successfully return if the file contents equal. This is an useful scenario when a client tries to write to a
	 * file that locked by another agent, but wants to handle gracefully is the file contents are the same. Often
	 * occurrs when dynamic libraries are being exported and loaded by {@link ClassLoader ClassLoaders}.
	 * <p>
	 * If the file bytes mismatch, and the file cannot be opened for writing, the original write opening
	 * {@link IOException} is thrown.
	 * 
	 * @param is
	 *            The input stream of bytes to write.
	 * @param filepath
	 *            The file path to write the bytes to.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 */
	public static void writeStreamEqualityCheckTo(InputStream is, Path filepath)
			throws IOException, NullPointerException {
		Objects.requireNonNull(is, "input stream");
		Objects.requireNonNull(filepath, "file path");
		long inreadcount = 0;
		long compareoffset = 0;
		SeekableByteChannel rwchannel;
		try {
			rwchannel = Files.newByteChannel(filepath, StandardOpenOption.READ, StandardOpenOption.WRITE,
					StandardOpenOption.CREATE);
		} catch (IOException e) {
			//failed to open the channel for writing. this may be due to the file being used by another process and is not writeable
			// open it for reading, and check if all the bytes equal
			// if they do, this method succeeds.
			// if don't then we throw the exception
			try {
				long mismatch = fileMismatch(is, filepath);
				if (mismatch < 0) {
					//all bytes equal
					return;
				}
				e.addSuppressed(
						new IllegalStateException("Target and input stream files mismatch at byte index: " + mismatch));
				throw e;
			} catch (IOException e2) {
				e.addSuppressed(e2);
				throw e;
			}
		}
		try (SeekableByteChannel channel = rwchannel) {
			InputStream channelin = Channels.newInputStream(channel);

			byte[] channelbuffer = new byte[StreamUtils.DEFAULT_BUFFER_SIZE];
			byte[] isbuffer = new byte[StreamUtils.DEFAULT_BUFFER_SIZE];
			int isbuffersize = 0;
			int isbufferoffset = 0;

			int channelbuffersize = channelin.read(channelbuffer);
			int channelbufferoffset = 0;
			while (true) {
				while (channelbuffersize - channelbufferoffset > 0) {
					//there are bytes in the channel buffer
					//compare them to the input
					if (isbuffersize - isbufferoffset <= 0) {
						//no bytes in the input buffer
						isbuffersize = is.read(isbuffer);
						if (isbuffersize <= 0) {
							//no more bytes in the input
							//the file was truncated
							channel.truncate(inreadcount);
							return;
						}
						isbufferoffset = 0;
						inreadcount += isbuffersize;
					}
					//compare the bytes in the channel buffer with the input buffer
					int bcount = Math.min(channelbuffersize - channelbufferoffset, isbuffersize - isbufferoffset);
					int mismatchindex = ArrayUtils.mismatch(isbuffer, isbufferoffset, channelbuffer,
							channelbufferoffset, bcount);
					if (mismatchindex < 0) {
						//all bytes equal
						channelbufferoffset += bcount;
						isbufferoffset += bcount;
						compareoffset += bcount;
						continue;
					}
					//there was a mismatch between the byte arrays
					//update the bytes of the file from the given offset
					//truncate the file, which also sets the position
					channel.truncate(compareoffset + mismatchindex);
					OutputStream os = Channels.newOutputStream(channel);
					//write the remaining buffered bytes to the channel
					os.write(isbuffer, isbufferoffset + mismatchindex, isbuffersize - isbufferoffset - mismatchindex);
					//copy the remaining of the stream
					StreamUtils.copyStream(is, os, isbuffer);
					return;
				}
				//no more bytes in the channel buffer

				channelbuffersize = channelin.read(channelbuffer);
				if (channelbuffersize <= 0) {
					//no more bytes available in the channel
					//the file was extended, with all the bytes matching from the stream
					OutputStream os = Channels.newOutputStream(channel);
					if (isbuffersize - isbufferoffset > 0) {
						//write the remaining buffered bytes to the channel
						os.write(isbuffer, isbufferoffset, isbuffersize - isbufferoffset);
					}
					//copy the remaining of the stream
					StreamUtils.copyStream(is, os, isbuffer);
					return;
				}
				channelbufferoffset = 0;
				//read some bytes from the channel
				//continue the loop to compare with the input
				continue;
			}
		}
	}

	private FileUtils() {
		throw new UnsupportedOperationException();
	}
}
