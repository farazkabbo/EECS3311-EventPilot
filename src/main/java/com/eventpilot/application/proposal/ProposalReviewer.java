package com.eventpilot.application.proposal;

import java.util.List;
import java.util.Optional;

/**
 * The human approval step (UC13). The GUI shows a dialog, the CLI a y/N prompt;
 * both return the chosen change indexes, or empty when the organizer rejects.
 */
public interface ProposalReviewer {

    Optional<List<Integer>> review(PlanProposal proposal);
}
