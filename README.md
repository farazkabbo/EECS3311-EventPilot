# EventPilot — AI Event Planning Agent

**EECS 3311 Software Design · Fall 2026 · Course Project**
**Author:** Mohammed Faraz Kabbo

EventPilot is an AI agent-based desktop application (JavaFX GUI + picocli CLI) that helps organizers plan events end-to-end. An LLM-based planning agent reasons over the real event state, calls deterministic tools (venue search, budget calculator, conflict checker, speaker availability, catering calculator, attendee query), and proposes concrete, reviewable changes that the organizer approves before they are applied — with full undo/redo.

## Stage status

| Stage | Content | Status |
|---|---|---|
| **Stage 1 — Design** | [Stage 1 Design Report](docs/stage1/Stage1_Design_Report.md) · [PDF](docs/stage1/Stage1_Design_Report.pdf) · [UML diagrams](docs/stage1/diagrams/) | Submitted |
| **Stage 2 — Implementation** | Source code (`src/`), [Stage 2 Implementation Report](docs/stage2/stage2-report.md) (PDF/Word in `docs/stage2/`), updated UML in `docs/stage2/diagrams/` | Submitted |
| Stage 3 — Testing & Agent Validation | JUnit tests, KUMA behavioral tests, failure analysis | next stage |

## Prerequisites

* **Java 17 or newer** (tested with Temurin 17 and Oracle JDK 26). Check with `java -version`.
* Internet access on the first build (Maven downloads the dependencies, including JavaFX).
* No Maven installation is needed — the Maven wrapper (`mvnw` / `mvnw.cmd`) is included.
* Optional: an **Anthropic API key** (Claude) or a local **Ollama** install. Without either, EventPilot runs with its built-in offline planner (see "AI providers").

## 1. Build

```bash
# Windows
mvnw.cmd -DskipTests package
# macOS / Linux
./mvnw -DskipTests package
```

This creates `target/eventpilot.jar` (one runnable jar containing the GUI and the CLI). Run the tests with `mvnw test` (101 JUnit tests).

## 2. Local configuration and API key (never committed)

```bash
# Windows                         # macOS / Linux
copy .env.example .env            cp .env.example .env
```

Open `.env` and set your values. `.env` is listed in `.gitignore`; only `.env.example` (placeholders only) is in the repository.

```properties
LLM_PROVIDER=claude                 # claude | ollama | mock
ANTHROPIC_API_KEY=sk-ant-...        # your own key, only in .env
LLM_MODEL=claude-sonnet-5-5         # planning model (tool use)
LLM_LIGHT_MODEL=claude-haiku-5-5    # cheap model for FAQ answers and invitations
OLLAMA_URL=http://localhost:11434   # only for LLM_PROVIDER=ollama
OLLAMA_MODEL=llama3.1:8b
EVENTPILOT_DATA_DIR=data            # SQLite database + e-mail outbox
```

Environment variables with the same names override `.env`.

### AI providers

| `LLM_PROVIDER` | What runs | Needs |
|---|---|---|
| `claude` | Claude through the official Anthropic Java SDK (tool use, Sonnet 5.5 + Haiku 5.5) | `ANTHROPIC_API_KEY` |
| `ollama` | A local model through Ollama (`ollama pull llama3.1:8b`, `ollama serve`) | Ollama running |
| `mock` | **Offline planner**: a deterministic, rule-based stand-in used for tests and demos without a key. It drives the same tool loop and answer formats, but its language understanding is limited to simple patterns. | nothing |

If `LLM_PROVIDER=claude` but no key is set, EventPilot falls back to `mock` and shows "AI: mock" in the status bar / `eventpilot ai`.

## 3. Run

```bash
eventpilot.cmd                 # Windows: GUI   (./eventpilot.sh on macOS/Linux)
eventpilot.cmd event list      # Windows: CLI   (./eventpilot.sh event list)

# or directly
java -jar target/eventpilot.jar              # GUI
java -jar target/eventpilot.jar --help       # CLI
mvnw javafx:run                              # GUI through Maven
mvnw -q exec:java -Dexec.args="event list"   # CLI through Maven
```

### GUI tour

* **Agent chat** (right): *Create event* (describe the event in plain language; answer follow-up questions), *Change plan* ("move the keynote to 2pm and add a 15-minute break after it"), *Ask FAQ* (attendee questions answered from the event's information, with sources).
* **Top bar**: select / create / delete events, lifecycle buttons (Start planning, Confirm, Reopen, Complete, Cancel), Undo / Redo (Ctrl+Z / Ctrl+Y).
* **Tabs**: Dashboard (summary + alerts), Agenda (generate, schedule speakers, check & fix conflicts, modify with AI, speakers), Venue (recommendations), Budget (plan, expenses, chart), Attendees (table, RSVP, CSV import), Catering, Invitations (draft, edit, send), Tasks (board with drag-and-drop).
* Every AI action opens a **review dialog** listing the proposed changes, their reasons, guardrail warnings and the agent's tool calls. Nothing changes until you click *Apply*.

### CLI example session

```bash
eventpilot event create "Plan a robotics workshop for 80 people"
eventpilot event answer "On December 12 from 9am to 5pm in Toronto, budget $9000"
eventpilot attendee import samples/attendees.csv
eventpilot speaker add --name "Ada Park" --topic "robot vision" --available 09:00-13:00
eventpilot venue recommend --needs "wheelchair accessible"      # choose 1-3
eventpilot agenda generate --tracks 1 --topics "Robot vision,Motion planning"
eventpilot agenda schedule
eventpilot agenda conflicts
eventpilot agenda modify "rename the closing networking to Farewell reception"
eventpilot agenda undo
eventpilot budget generate
eventpilot budget expense --category venue --amount 3800 --desc deposit
eventpilot catering plan
eventpilot task generate --team Sam,Lee
eventpilot task status t1 DONE
eventpilot event status start-planning
eventpilot invite draft --tone formal
eventpilot invite send                 # writes e-mails to data/outbox; *.invalid addresses fail on purpose
eventpilot event note "Free parking is available in the north lot."
eventpilot ask "Is there parking?"
eventpilot alerts
eventpilot event status confirm
eventpilot shell                       # interactive mode
```

Add `-y` to approve proposals automatically (never applies proposals with blocking problems), `-e <id>` to work on another event than the selected one, and `--help` to any command.

## Features (F01–F12)

| ID | Feature | GUI | CLI |
|---|---|---|---|
| F01 | Create event from a description (AI + clarifying questions) | Chat ▸ Create event | `event create`, `event answer` |
| F02 | Attendee & RSVP management, CSV import | Attendees | `attendee add/import/rsvp/list` |
| F03 | Venue recommendation (tool use) | Venue | `venue recommend` |
| F04 | Agenda generation | Agenda ▸ Generate | `agenda generate` |
| F05 | Speaker scheduling | Agenda ▸ Auto-schedule | `agenda schedule` |
| F06 | Conflict detection & AI resolution | Agenda ▸ Check / Ask agent to fix | `agenda conflicts/resolve` |
| F07 | Budget planning & expense tracking | Budget | `budget generate/expense/status` |
| F08 | Dietary-aware catering | Catering | `catering summary/plan` |
| F09 | Task plan & tracking | Tasks | `task generate/list/status` |
| F10 | Invitation drafting & sending | Invitations | `invite draft/edit/send` |
| F11 | Attendee FAQ agent (RAG) | Chat ▸ Ask FAQ | `ask` |
| F12 | Plain-language plan changes + undo/redo | Chat ▸ Change plan, Agenda ▸ Modify | `agenda modify/undo/redo` |

## Design patterns (implemented)

Facade (`EventPlannerFacade`), Command (`EventCommand`, `MacroCommand`, `CommandHistory`), Observer (`EventBus`, `AlertService`, `KnowledgeBaseIndexer`, GUI views), Strategy (`LLMClient` + `LLMProvider`: Claude / Ollama / Mock), Template Method (`PlanningWorkflow` + 11 workflows), State (`Event` + `EventState` and 5 states), Adapter (`JsonVenueCatalogAdapter`, `ClaudeProvider`, `OllamaProvider`). Details in the Stage 2 report.

## Repository layout

```
src/main/java/com/eventpilot/
  domain/          Event, Agenda, Attendee, Budget, ... ; domain/state (State pattern); domain/service (rule engines)
  application/     EventPlannerFacade, command/ (Command), event/ (Observer, alerts), proposal/
  agent/           AgentOrchestrator, tools/, llm/ (Strategy + Adapters), workflow/ (Template Method),
                   memory/, rag/, offline/ (offline planner), GuardrailValidator, CommandMapper
  infra/           SQLite repository, venue Adapter, CSV importer, outbox gateway, AppConfig
  cli/             picocli commands      gui/  JavaFX views and controllers
src/main/resources/data/venues.json   sample venue dataset (fictional venues)
samples/attendees.csv                 sample attendees (contains deliberate bad rows)
docs/stage1/ docs/stage2/             reports and UML
```

## Security

No API key or credential is stored in the repository, source code, reports or recordings. Keys live only in the local `.env`. If a key is ever pushed by mistake, revoke it immediately in the provider console and remove it from the git history.
