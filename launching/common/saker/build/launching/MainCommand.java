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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import saker.build.file.path.SakerPath;
import sipka.cmdline.api.Command;
import sipka.cmdline.api.CommonConverter;
import sipka.cmdline.api.SubCommand;

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
@Command(className = "saker.build.launching.Launcher",
		main = true,
		helpCommand = { "help", "-help", "--help", "?", "/?" })
@CommonConverter(type = SakerPath.class, converter = MainCommand.class, method = "toSakerPath")
@CommonConverter(type = InetAddress.class, converter = MainCommand.class, method = "toInetAddress")
@CommonConverter(type = Path.class, converter = MainCommand.class, method = "toLocalPath")
@SubCommand(name = "build", type = BuildCommand.class, defaultCommand = true)
@SubCommand(name = "daemon", type = DaemonCommand.class)
@SubCommand(name = "action", type = RepositoryActionCommand.class)
@SubCommand(name = "licenses", type = LicensesCommand.class)
@SubCommand(name = { "version", "-version", "--version" }, type = VersionCommand.class)
public class MainCommand {

	/**
	 * @cmd-format &lt;path&gt;
	 */
	public static SakerPath toSakerPath(Iterator<? extends String> it) {
		return SakerPath.valueOf(it.next());
	}

	/**
	 * @cmd-format &lt;local-path&gt;
	 */
	public static Path toLocalPath(Iterator<? extends String> it) {
		Path result = Paths.get(it.next());
		return result;
	}

	/**
	 * @cmd-format &lt;net-address&gt;
	 */
	public static InetAddress toInetAddress(Iterator<? extends String> it) {
		String address = it.next();
		try {
			InetAddress result = InetAddress.getByName(address);
			if (result != null) {
				return result;
			}
		} catch (UnknownHostException e) {
			throw new IllegalArgumentException("Failed to resolve address: " + address, e);
		}
		throw new IllegalArgumentException("Failed to resolve address: " + address);
	}
}