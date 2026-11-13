package com.eventpilot;

import com.eventpilot.cli.BaseCliCommand;
import com.eventpilot.cli.CliContext;
import com.eventpilot.cli.EventPilotCli;
import com.eventpilot.infra.AppConfig;
import java.io.InputStream;
import java.io.PrintStream;
import picocli.CommandLine;

/** Entry point: no arguments (or "gui") starts the GUI, anything else runs the CLI. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            System.err.println("The GUI is not available in this build yet; use the CLI (try --help).");
            System.exit(1);
        }
        System.exit(runCli(EventPilotApp.bootstrap(AppConfig.load()), args, System.out, System.in));
    }

    public static int runCli(EventPilotApp app, String[] args, PrintStream out, InputStream in) {
        BaseCliCommand.setContext(new CliContext(app, out, in));
        CommandLine cli = new CommandLine(new EventPilotCli()).setCaseInsensitiveEnumValuesAllowed(true);
        cli.setOut(new java.io.PrintWriter(out, true));
        return cli.execute(args);
    }
}
