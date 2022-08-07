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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import sipka.cmdline.api.Command;
import sipka.cmdline.api.CommonConverter;
import sipka.cmdline.api.SubCommand;
import sipka.cmdline.runtime.ArgumentResolutionException;
import sipka.cmdline.runtime.InvalidArgumentFormatException;
import sipka.cmdline.runtime.ParseUtil;

/**
 * <pre>
 * saker.build system command line interface.
 * 
 * See the subcommands for more information.
 * 
 * Any parameters that take &lt;address> as their arguments 
 * are expected in any of the following format:
 * 
 * 	hostname
 * 	hostname:port
 * 	ipv4
 * 	ipv4:port
 * 	[ipv6]
 * 	[ipv6]:port
 * 
 * Where the host names will be automatically resolved, and 
 * the IPv6 addresses need to be surrounded by square brackets. 
 * If the port numbers are omitted, the default port associated 
 * for the given setting will be used.
 * </pre>
 */
@Command(className = "saker.build.launching.Launcher", helpCommand = { "-h", "help", "-help", "--help", "?", "/?" })
@CommonConverter(type = SakerPath.class, converter = MainCommand.class, method = "toSakerPath")
@CommonConverter(type = InetAddress.class, converter = MainCommand.class, method = "toInetAddress")
@CommonConverter(type = Path.class, converter = MainCommand.class, method = "toLocalPath")
@SubCommand(name = MainCommand.COMMAND_BUILD, type = BuildCommand.class, defaultCommand = true)
@SubCommand(name = MainCommand.COMMAND_DAEMON, type = DaemonCommand.class)
@SubCommand(name = MainCommand.COMMAND_ACTION, type = RepositoryActionCommand.class)
@SubCommand(name = MainCommand.COMMAND_LICENSES, type = LicensesCommand.class)
@SubCommand(name = { "version", "-version", "--version" }, type = VersionCommand.class)
public abstract class MainCommand {

	public static final String COMMAND_LICENSES = "licenses";
	public static final String COMMAND_ACTION = "action";
	public static final String COMMAND_DAEMON = "daemon";
	public static final String COMMAND_BUILD = "build";

	protected boolean shouldSystemExit = false;

	/**
	 * @cmd-format &lt;path&gt;
	 */
	public static SakerPath toSakerPath(String argname, Iterator<? extends String> it) {
		String pathstr = ParseUtil.requireNextArgument(argname, it);
		try {
			return SakerPath.valueOf(pathstr);
		} catch (InvalidPathFormatException e) {
			throw new InvalidArgumentFormatException("Invalid path format for: " + pathstr, e, argname);
		}
	}

	/**
	 * @cmd-format &lt;local-path&gt;
	 */
	public static Path toLocalPath(String argname, Iterator<? extends String> it) {
		String pathstr = ParseUtil.requireNextArgument(argname, it);
		try {
			Path result = Paths.get(pathstr);
			return result;
		} catch (InvalidPathException e) {
			throw new InvalidArgumentFormatException("Invalid local path format for: " + pathstr, e, argname);
		}
	}

	/**
	 * @cmd-format &lt;net-address&gt;
	 */
	public static InetAddress toInetAddress(String argname, Iterator<? extends String> it) {
		String address = ParseUtil.requireNextArgument(argname, it);
		try {
			InetAddress result = InetAddress.getByName(address);
			if (result != null) {
				return result;
			}
		} catch (UnknownHostException e) {
			throw new ArgumentResolutionException("Failed to resolve address: " + address, e, argname);
		}
		throw new ArgumentResolutionException("Failed to resolve address: " + address, argname);
	}

	public static boolean main(String... args) throws Exception {
		Launcher launcher = Launcher.parse(java.util.Arrays.asList(args).iterator());
		launcher.callCommand();
		return launcher.shouldSystemExit;
	}

	protected abstract void callCommand() throws Exception;
}