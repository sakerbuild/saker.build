package saker.build.launching;

import sipka.cmdline.api.SubCommand;

/**
 * <pre>
 * Base command for configuring and managing daemons.
 * </pre>
 */
@SubCommand(name = "start", type = StartDaemonCommand.class)
@SubCommand(name = "stop", type = StopDaemonCommand.class)
@SubCommand(name = "run", type = RunDaemonCommand.class)
@SubCommand(name = "io", type = IODaemonCommand.class)
@SubCommand(name = "info", type = InfoDaemonCommand.class)
public class DaemonCommand {
}