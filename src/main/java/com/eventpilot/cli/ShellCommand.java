package com.eventpilot.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/** `shell` - interactive session: type commands without the "eventpilot" prefix; `exit` to quit. */
@Command(name = "shell", description = "Interactive mode (one process: faster, and `ai` shows the session's token usage).")
public class ShellCommand extends BaseCliCommand {

    @Override
    protected int run() {
        ctx.printer().println("EventPilot shell - AI: " + ctx.facade().aiDescription() + ". Type `help` or `exit`.");
        CommandLine cli = new CommandLine(new EventPilotCli()).setCaseInsensitiveEnumValuesAllowed(true);
        cli.setOut(new java.io.PrintWriter(ctx.out(), true));
        while (true) {
            ctx.out().print("eventpilot> ");
            ctx.out().flush();
            String line;
            try {
                line = ctx.in().readLine();
            } catch (IOException e) {
                return 1;
            }
            if (line == null || line.trim().equalsIgnoreCase("exit") || line.trim().equalsIgnoreCase("quit")) {
                return 0;
            }
            List<String> tokens = tokenize(line);
            if (tokens.isEmpty()) {
                continue;
            }
            if (tokens.get(0).equals("help")) {
                cli.usage(ctx.out());
                continue;
            }
            if (tokens.get(0).equals("shell")) {
                ctx.printer().println("Already in the shell.");
                continue;
            }
            Common.reset();
            cli.execute(tokens.toArray(new String[0]));
        }
    }

    /** Splits a line into arguments, honouring "double" and 'single' quotes. */
    static List<String> tokenize(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        char quote = 0;
        boolean has = false;
        for (char ch : line.toCharArray()) {
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"' || ch == '\'') {
                quote = ch;
                has = true;
            } else if (Character.isWhitespace(ch)) {
                if (has || cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                    has = false;
                }
            } else {
                cur.append(ch);
            }
        }
        if (has || cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }
}
