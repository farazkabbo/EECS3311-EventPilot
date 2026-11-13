package com.eventpilot.cli;

import com.eventpilot.application.proposal.PlanProposal;
import com.eventpilot.application.proposal.ProposalReviewer;
import com.eventpilot.application.proposal.Warning;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CLI approval (UC13): prints the proposal and asks [y/N/select]. With --yes it
 * approves automatically, but never a proposal with blocking problems; for
 * alternatives it picks the first option without a blocking problem.
 */
public class ConsoleProposalReviewer implements ProposalReviewer {

    private final ConsolePrinter printer;
    private final BufferedReader in;
    private final boolean autoApprove;

    public ConsoleProposalReviewer(ConsolePrinter printer, BufferedReader in, boolean autoApprove) {
        this.printer = printer;
        this.in = in;
        this.autoApprove = autoApprove;
    }

    @Override
    public Optional<List<Integer>> review(PlanProposal proposal) {
        printer.proposal(proposal);
        if (proposal.isQuestion() || proposal.isEmpty()) {
            return Optional.empty();
        }
        if (autoApprove) {
            return autoDecision(proposal);
        }
        if (proposal.hasBlockingWarnings()) {
            printer.println("  Some changes have blocking problems (HIGH); only unaffected ones can be applied.");
        }
        printer.println(proposal.alternatives()
                ? "  Choose an option [1-" + proposal.changes().size() + "] or N to reject: "
                : "  Apply these changes? [y/N/select e.g. 1,3]: ");
        String answer;
        try {
            answer = in.readLine();
        } catch (IOException e) {
            answer = null;
        }
        if (answer == null) {
            return Optional.empty();
        }
        answer = answer.trim().toLowerCase();
        if (answer.equals("y") || answer.equals("yes")) {
            return Optional.of(proposal.alternatives() ? List.of(0) : all(proposal));
        }
        if (answer.isEmpty() || answer.startsWith("n")) {
            return Optional.empty();
        }
        List<Integer> chosen = new ArrayList<>();
        for (String part : answer.replace("select", "").split("[,\\s]+")) {
            if (part.isBlank()) {
                continue;
            }
            try {
                chosen.add(Integer.parseInt(part.trim()) - 1);
            } catch (NumberFormatException e) {
                printer.error("Not a number: " + part);
                return Optional.empty();
            }
        }
        return chosen.isEmpty() ? Optional.empty() : Optional.of(chosen);
    }

    private Optional<List<Integer>> autoDecision(PlanProposal proposal) {
        if (proposal.alternatives()) {
            for (int i = 0; i < proposal.changes().size(); i++) {
                String prefix = "Option " + (i + 1) + ":";
                boolean blocked = proposal.warnings().stream()
                        .anyMatch(w -> w.isBlocking() && w.message().startsWith(prefix));
                if (!blocked) {
                    printer.println("  --yes given: choosing option " + (i + 1) + ".");
                    return Optional.of(List.of(i));
                }
            }
            printer.println("  --yes given, but every option has a blocking problem - nothing applied.");
            return Optional.empty();
        }
        if (proposal.warnings().stream().anyMatch(Warning::isBlocking)) {
            printer.println("  --yes never applies a proposal with blocking problems - nothing applied.");
            return Optional.empty();
        }
        printer.println("  --yes given: approving all changes.");
        return Optional.of(all(proposal));
    }

    private static List<Integer> all(PlanProposal p) {
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < p.changes().size(); i++) {
            all.add(i);
        }
        return all;
    }
}
