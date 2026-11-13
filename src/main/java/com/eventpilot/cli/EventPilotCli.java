package com.eventpilot.cli;

import picocli.CommandLine.Command;

/** Root CLI command. Every subcommand calls only the EventPlannerFacade. */
@Command(name = "eventpilot", mixinStandardHelpOptions = true, version = "EventPilot 0.2.0 (Stage 2)",
        description = "AI event-planning agent - command-line interface.",
        subcommands = {EventCliCommand.class, AttendeeCliCommand.class, SpeakerCliCommand.class})
public class EventPilotCli extends BaseCliCommand {

    @Override
    protected int run() {
        picocli.CommandLine.usage(this, ctx.out());
        return 0;
    }
}
