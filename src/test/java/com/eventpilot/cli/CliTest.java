package com.eventpilot.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventpilot.EventPilotApp;
import com.eventpilot.Main;
import com.eventpilot.agent.llm.MockLLMProvider;
import com.eventpilot.agent.offline.OfflinePlanner;
import com.eventpilot.application.command.AddAgendaItemCommand;
import com.eventpilot.application.proposal.PlanProposal;
import com.eventpilot.application.proposal.Warning;
import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.AgendaItemType;
import com.eventpilot.infra.AppConfig;
import com.eventpilot.infra.SqliteEventRepository;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliTest {

    @TempDir
    Path tmp;

    /** Runs one CLI invocation as a separate "process" (fresh app on the same database). */
    private String cli(String stdin, String... args) {
        EventPilotApp app = EventPilotApp.bootstrap(AppConfig.of(Map.of("EVENTPILOT_DATA_DIR", tmp.toString())),
                new SqliteEventRepository(tmp.resolve("eventpilot.db")), new MockLLMProvider(new OfflinePlanner()),
                Clock.systemDefaultZone());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Main.runCli(app, args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)));
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void cliCoversTheMainFlow() {
        String created = cli("", "event", "create", "Plan an AI meetup for 60 people on December 5 from 9am to 4pm in Toronto, budget $6000");
        assertTrue(created.contains("Created"), created);
        assertTrue(cli("", "attendee", "import", "samples/attendees.csv").contains("12 imported"));

        String rejected = cli("n\n", "agenda", "generate");
        assertTrue(rejected.contains("Not applied"), rejected);
        String applied = cli("", "-y", "agenda", "generate");
        assertTrue(applied.contains("Applied: Generate agenda"), applied);
        assertTrue(cli("", "agenda", "conflicts").contains("No conflicts"));

        assertTrue(cli("", "agenda", "undo").contains("Undid: Generate agenda"));
        assertTrue(cli("", "agenda", "redo").contains("Redid"));

        assertTrue(cli("", "invite", "draft").contains("ERROR"), "draft event cannot invite");
        cli("", "event", "status", "start-planning");
        assertTrue(cli("", "invite", "draft", "--tone", "playful").contains("drafted"));
        assertTrue(cli("", "ask", "When", "is", "lunch?").contains("sources"));
    }

    @Test
    void reviewerSelectionAndAutoApproveRules() {
        LocalDateTime nine = LocalDateTime.of(2026, 12, 5, 9, 0);
        PlanProposal p = new PlanProposal("e1", "T", "", List.of(
                new PlanProposal.Change(new AddAgendaItemCommand(new AgendaItem(null, "A", AgendaItemType.SESSION, nine, nine.plusHours(1), "R", null, 1)), ""),
                new PlanProposal.Change(new AddAgendaItemCommand(new AgendaItem(null, "B", AgendaItemType.SESSION, nine, nine.plusHours(1), "R", null, 1)), "")),
                false);
        ConsolePrinter printer = new ConsolePrinter(new PrintStream(new ByteArrayOutputStream()), false);
        Optional<List<Integer>> chosen = new ConsoleProposalReviewer(printer, new BufferedReader(new StringReader("2\n")), false).review(p);
        assertEquals(List.of(1), chosen.orElseThrow());

        p.setWarnings(List.of(Warning.high("clash")));
        assertTrue(new ConsoleProposalReviewer(printer, new BufferedReader(new StringReader("")), true).review(p).isEmpty(),
                "--yes never approves blocked proposals");
    }

    @Test
    void shellTokenizerHonoursQuotes() {
        assertEquals(List.of("agenda", "modify", "move the keynote to 2pm"),
                ShellCommand.tokenize("agenda modify \"move the keynote to 2pm\""));
        assertEquals(List.of("pref", "default_city", "Toronto"), ShellCommand.tokenize("  pref default_city 'Toronto' "));
    }
}
