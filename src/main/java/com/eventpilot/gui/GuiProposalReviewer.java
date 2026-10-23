package com.eventpilot.gui;

import com.eventpilot.application.proposal.PlanProposal;
import com.eventpilot.application.proposal.ProposalReviewer;
import java.util.List;
import java.util.Optional;

/** GUI implementation of the approval step: a modal {@link ProposalReviewDialog}. */
public class GuiProposalReviewer implements ProposalReviewer {

    @Override
    public Optional<List<Integer>> review(PlanProposal proposal) {
        return new ProposalReviewDialog(proposal).showAndWait();
    }
}
