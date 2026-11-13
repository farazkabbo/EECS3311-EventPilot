package com.eventpilot.application.command;

import com.eventpilot.domain.Event;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Command pattern - the "Command" role. Every change the agent proposes is one
 * of these objects, so it can be shown to the organizer, applied, undone and
 * redone. The {@link Event} is the "Receiver". The LLM can only request
 * operations that exist as command classes.
 *
 * <p>Commands are serializable so that undo/redo history survives between CLI runs.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AddAgendaItemCommand.class, name = "add_item"),
        @JsonSubTypes.Type(value = RemoveAgendaItemCommand.class, name = "remove_item"),
        @JsonSubTypes.Type(value = MoveSessionCommand.class, name = "move_item"),
        @JsonSubTypes.Type(value = RenameAgendaItemCommand.class, name = "rename_item"),
        @JsonSubTypes.Type(value = AssignSpeakerCommand.class, name = "assign_speaker"),
        @JsonSubTypes.Type(value = SetVenueCommand.class, name = "set_venue"),
        @JsonSubTypes.Type(value = SetBudgetLineCommand.class, name = "set_budget_line"),
        @JsonSubTypes.Type(value = SetCateringPlanCommand.class, name = "set_catering_plan"),
        @JsonSubTypes.Type(value = AddTaskCommand.class, name = "add_task"),
        @JsonSubTypes.Type(value = MacroCommand.class, name = "macro")
})
public interface EventCommand {

    void execute(Event event);

    void undo(Event event);

    /** Human-readable description shown in the review dialog and after undo. */
    String describe();
}
