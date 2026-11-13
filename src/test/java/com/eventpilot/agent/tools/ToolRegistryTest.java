package com.eventpilot.agent.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventpilot.agent.CommandMapper;
import com.eventpilot.agent.TimeParser;
import com.eventpilot.agent.llm.ChatMessage;
import com.eventpilot.application.command.MoveSessionCommand;
import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.AgendaItemType;
import com.eventpilot.domain.Attendee;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.service.BudgetTracker;
import com.eventpilot.domain.service.CateringCalculator;
import com.eventpilot.domain.service.ConflictDetector;
import com.eventpilot.infra.JsonVenueCatalogAdapter;
import com.eventpilot.infra.Json;
import com.eventpilot.infra.VenueDatasetReader;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

    static final LocalDateTime NINE = LocalDateTime.of(2026, 12, 5, 9, 0);
    private ToolRegistry registry;
    private Event event;
    private List<String> all;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry()
                .register(new VenueSearchTool(new JsonVenueCatalogAdapter(new VenueDatasetReader())))
                .register(new BudgetCalculatorTool(new BudgetTracker()))
                .register(new ConflictCheckTool(new ConflictDetector(), new CommandMapper()))
                .register(new SpeakerAvailabilityTool())
                .register(new CateringCalculatorTool(new CateringCalculator()))
                .register(new AttendeeQueryTool());
        all = registry.names();
        event = new Event("Conf", "conference", NINE, NINE.plusHours(8), 120, 10000, "Toronto");
        event.agenda().add(new AgendaItem(null, "Keynote", AgendaItemType.KEYNOTE, NINE, NINE.plusHours(1), "Main Hall", null, 100));
        event.addAttendee(new Attendee(null, "Ann", "ann@x.com", null, List.of("vegan"), null, Attendee.Rsvp.ACCEPTED));
    }

    private ChatMessage.ToolResult run(String tool, String args) {
        return registry.execute(new ChatMessage.ToolCall("c1", tool, args), all, new ToolContext(event));
    }

    @Test
    void invalidArgumentsAreRejectedWithoutRunning() {
        ChatMessage.ToolResult r = run("venue_search", "{\"city\": \"Toronto\", \"min_capacity\": \"lots\"}");
        assertTrue(r.isError());
        assertTrue(r.content().contains("min_capacity must be integer"));
        assertTrue(run("venue_search", "{\"city\": \"Toronto\"}").content().contains("min_capacity is required"));
        assertTrue(run("venue_search", "{\"min_capacity\": 5, \"colour\": \"red\"}").content().contains("not a known argument"));
    }

    @Test
    void unknownOrDisallowedToolIsRejected() {
        ChatMessage.ToolResult r = registry.execute(new ChatMessage.ToolCall("c1", "venue_search", "{\"min_capacity\": 5}"),
                List.of("budget_calculator"), new ToolContext(event));
        assertTrue(r.isError());
    }

    @Test
    void venueSearchReturnsVenuesAndRecordsIds() {
        ToolContext ctx = new ToolContext(event);
        ChatMessage.ToolResult r = registry.execute(new ChatMessage.ToolCall("c1", "venue_search",
                "{\"city\": \"Toronto\", \"min_capacity\": 120, \"accessible\": true}"), all, ctx);
        assertFalse(r.isError());
        JsonNode json = Json.tree(r.content());
        assertTrue(json.get("count").asInt() > 0);
        assertEquals(json.get("count").asInt(), ctx.returnedVenueIds().size());
    }

    @Test
    void budgetCalculatorAllocates() {
        JsonNode json = Json.tree(run("budget_calculator",
                "{\"operation\": \"allocate\", \"percentages\": {\"venue\": 40, \"catering\": 60}}").content());
        assertEquals(4000, json.get("lines").get(0).get("amount").asDouble(), 0.001);
    }

    @Test
    void conflictCheckTestsHypotheticalChangesWithoutTouchingEvent() {
        JsonNode json = Json.tree(run("conflict_check",
                "{\"changes\": [{\"op\": \"add\", \"title\": \"Clash\", \"type\": \"session\", \"start\": \"9:30\", \"end\": \"10:30\", \"room\": \"Main Hall\"}]}").content());
        assertTrue(json.get("blocking").asBoolean());
        assertEquals(1, event.agenda().size(), "real event unchanged");
    }

    @Test
    void cateringAndAttendeeQueries() {
        JsonNode catering = Json.tree(run("catering_calculator", "{\"meal_type\": \"lunch\"}").content());
        assertEquals(2, catering.get("portions").asInt());
        assertEquals(1, catering.get("dietary_counts").get("vegan").asInt());
        JsonNode people = Json.tree(run("attendee_query", "{\"dietary\": \"vegan\"}").content());
        assertEquals(1, people.get("count").asInt());
        assertFalse(people.toString().contains("ann@x.com"), "no e-mails leak to the LLM");
    }

    @Test
    void commandMapperMapsAndRejects() {
        CommandMapper mapper = new CommandMapper();
        assertInstanceOf(MoveSessionCommand.class,
                mapper.toCommand(Json.tree("{\"op\": \"move\", \"item_id\": \"a1\", \"start\": \"2pm\"}"), event));
        assertThrows(CommandMapper.MappingException.class,
                () -> mapper.toCommand(Json.tree("{\"op\": \"move\", \"item_id\": \"a9\", \"start\": \"2pm\"}"), event));
        assertThrows(CommandMapper.MappingException.class,
                () -> mapper.toCommand(Json.tree("{\"op\": \"teleport\"}"), event));
    }

    @Test
    void timeParserFormats() {
        assertEquals(LocalTime.of(14, 0), TimeParser.parseTime("2pm"));
        assertEquals(LocalTime.of(14, 30), TimeParser.parseTime("2:30 PM"));
        assertEquals(LocalTime.of(9, 5), TimeParser.parseTime("09:05"));
        assertEquals(LocalTime.NOON, TimeParser.parseTime("noon"));
        assertEquals(NINE.plusHours(5), TimeParser.parse("2026-12-05T14:00", null));
        assertThrows(IllegalArgumentException.class, () -> TimeParser.parseTime("teatime"));
    }
}
