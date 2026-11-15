# EventPilot — Demo Video Script and Test Checklist

Use this file while recording the Stage 2 demonstration videos and while testing the app.
Every step below was tried on the current build. The error and alternative cases behave as described.

The rules for each video segment (from the report, section 7):

- Show the feature ID and name.
- Show how the feature is started and what input it gets.
- Show the result.
- Show where the AI is involved.
- Show one error or alternative case.
- Across all the recordings, show both the GUI and the CLI.
- **Never show `.env`, `.env.local` or the API key.** Close those editor tabs before you record.

After each segment, write down its start time (mm:ss). Those times go into the Demonstration Index (section 7) of `stage2-report.md`.

---

## 0. Before recording

1. Build the app:
   ```
   .\mvnw.cmd clean package
   ```
   It should end with 101 tests and `BUILD SUCCESS`.
2. Start from an empty database. Close the app, then run:
   ```
   Remove-Item -Recurse -Force data
   ```
3. Choose the AI provider:
   - **Claude:** your key must be in a file named `.env`. A file named `.env.local` is not read.
   - **Offline planner:** set `LLM_PROVIDER=mock`, or have no key at all. The offline planner understands only simple phrasings, so use the example sentences in this script word for word.
4. Check the provider with:
   ```
   .\eventpilot.cmd ai
   ```
   The bottom-right chip in the GUI shows the same information.
5. Get the desktop ready:
   - Window size: at least 1440×880. Maximised is best.
   - Screen-recorder resolution: 1080p.
   - Open a PowerShell window in the project folder for the CLI parts.

> In PowerShell, always type `.\eventpilot.cmd`. Plain `eventpilot.cmd` is not found.

---

## 1. Intro (≈30 s)

**Show:**

1. Start the GUI with `.\eventpilot.cmd`.
2. Point at the main parts of the window:
   - the sidebar
   - the empty-state dashboard
   - the **✦ Agent** chat on the right
   - the AI chip in the status bar
3. Click **Dark mode** in the sidebar, then click it again to switch back.

**Say:**
> "EventPilot is an AI event-planning agent with a JavaFX GUI and a picocli CLI. Every AI action is a proposal: the agent uses tools, guardrails check the result, and nothing changes until I approve it. Everything is undoable."

---

## 2. F01 — Create event from plain language (GUI chat + CLI) (≈1.5 min)

**GUI:**

1. In the chat, keep the **Create event** mode selected.
2. **Error case first.** Type `what is the weather like tomorrow` and send it.
   - Expected: the agent declines and asks you to describe an event.
3. **Incomplete description.** Type `Plan a robotics workshop for 80 people`.
   - Expected: the agent asks for the date and time, the end time and the budget.
4. **Answer the questions.** Type `On December 12 from 9am to 5pm in Toronto, budget $9000`.
   - Expected: the event is created. The dashboard fills in, the badge shows **DRAFT**, and a toast appears.

**Say:**
> "The agent extracts the fields, asks only for what is missing, and refuses requests that are not events."

**CLI (short):**
```
.\eventpilot.cmd event create "Plan a robotics workshop for 80 people"
.\eventpilot.cmd event answer "On December 12 from 9am to 5pm in Toronto, budget 9000"
.\eventpilot.cmd event list
```

---

## 3. UC15 — Lifecycle (GUI header) (≈45 s)

1. Point at the header: the **DRAFT** badge and the only allowed actions, **▶ Start planning** and **Cancel event**.
2. Click **▶ Start planning**.
   - Expected: the badge turns **PLANNING** and **✔ Confirm** appears.
3. **Error case.** Click **✔ Confirm** now.
   - Expected: it is refused with a reason, "Cannot confirm yet: choose a venue."
   - Leave this for the end: you confirm the event after F03.

**Say:**
> "This is the State pattern: each state decides which actions are allowed."

---

## 4. F02 — Attendees and RSVP (GUI Attendees) (≈1 min)

1. Press **Ctrl+5**, or click **Attendees** in the sidebar.
2. Click **Import CSV...** and choose `samples/attendees.csv`.
   - Expected report: **12 imported, 2 rejected, 1 duplicate**.
   - The rejections are an invalid e-mail (line 14) and an unknown role (line 15). The duplicate is line 16.
   - This is the error case.
3. Change one attendee's RSVP in the table.
   - Expected: the RSVP pie chart updates.
4. Add one attendee with the form at the bottom.

**CLI alternative:**
```
.\eventpilot.cmd attendee import samples/attendees.csv
```

---

## 5. F03 — Venue recommendation (GUI Venue) (≈1 min)

1. Open **Venue** (Ctrl+3).
2. In *Extra needs*, type `wheelchair accessible` and click **✦ Recommend venues**.
   - The purple progress bar and the "Agent is thinking" indicator appear while it runs.
3. In the review dialog, show:
   - 3 option cards, each with a **Why** line
   - any guardrail warnings
   - the expanded **Agent tool calls** section, which lists `venue_search` and `budget_calculator`
4. Pick one option and click **Apply**.
   - Expected: a toast, and the venue card fills in.

**Say:**
> "The agent can only recommend venues that the venue_search tool returned. Anything else is blocked by the guardrails."

---

## 6. F04 — Agenda generation (GUI Agenda) (≈1 min)

1. Open **Agenda** (Ctrl+2) and click **✦ Generate agenda**.
2. In the preferences dialog, set:
   - tracks: 1
   - session length: 45
   - break: 15
   - topics: `Robot vision, Motion planning`
3. Review the proposal: each item has a reason, and the tool trace shows `conflict_check`. Click **Apply**.
   - Expected: the timeline table fills, and "Conflicts" shows "No conflicts found".

---

## 7. F05 — Speaker scheduling (GUI Agenda + CLI) (≈1 min)

1. In the **Speakers** panel on the right of the Agenda page, add a speaker:
   - name `Ada Park`
   - topic `robot vision`
   - availability `09:00-13:00`
2. **Error case.** Add another speaker with availability `9 to 12`.
   - Expected: "Availability must look like 09:00-12:00", and nothing is added.
3. Click **✦ Auto-schedule speakers**, review, and apply.
   - Expected: the Speaker column fills in, inside each speaker's availability.

**CLI:**
```
.\eventpilot.cmd speaker add --name "Bo Chen" --topic "drones" --available 15:00-16:00
.\eventpilot.cmd agenda schedule
```

---

## 8. F06 — Conflict detection and AI resolution (CLI + GUI red rows) (≈1.5 min)

1. Create a clash by shrinking the room:
   ```
   .\eventpilot.cmd event rooms "Main Hall:40" "Room B:100"
   .\eventpilot.cmd agenda conflicts
   ```
   - Expected: several **[HIGH] ROOM_TOO_SMALL** lines, e.g. "Opening keynote expects 80 people but Main Hall seats 40".
   - Note: if you picked a venue in F03, use that venue's room name instead of `Main Hall`. Check it with `.\eventpilot.cmd event show`.
2. Switch to the GUI Agenda page and click **Check conflicts**.
   - Expected: the clashing rows turn **red**.
3. Click **✦ Ask agent to fix**, review the moves, and apply.
   - Expected: the red rows disappear and "No conflicts found" shows.
4. **Alternative case.** Run `.\eventpilot.cmd agenda resolve` again.
   - Expected: "No conflicts found - nothing to resolve."

**Say:**
> "Detection is deterministic code, not AI. The agent only proposes fixes, and each fix is tested on a copy first."

---

## 9. F12 — Plan change in plain language + undo/redo (CLI + GUI) (≈1.5 min)

1. In the GUI chat, switch to **Change plan** mode, or use the *Modify with AI* box on the Agenda page.
2. Type `rename the closing networking to Farewell reception`, review, and apply.
   - Expected: a toast that says "Ctrl+Z to undo".
3. **Ambiguous case.** Type `move session 2 to 9:00`.
   - Expected: the agent asks "Which one do you mean by 'session 2'?" and lists the candidates.
4. **Guardrail case.** Type `move the opening keynote to 9:30`.
   - Expected: a **[HIGH] Creates a conflict** warning. The change is refused when applied.
5. Press **Ctrl+Z** to undo the rename, then **Ctrl+Y** to redo it. The table updates live.

**CLI:**
```
.\eventpilot.cmd agenda modify "change it"
.\eventpilot.cmd agenda undo
.\eventpilot.cmd agenda redo
.\eventpilot.cmd agenda history
```
- Expected for `change it`: the agent asks for more detail.

---

## 10. F07 — Budget (GUI Budget) (≈1 min)

1. Open **Budget** (Ctrl+4) and click **✦ Generate budget plan**. Review and apply.
   - Expected: the categories table and the planned-vs-spent bars fill in.
2. Record an expense:
   - category `venue`
   - amount `3800`
   - description `deposit`
   - then click **Add expense**.
3. **Alert case.**
   - Expected: venue shows **OVER PLAN**, and a budget alert appears.
   - Then open the **Dashboard** (Ctrl+1): the Alerts card shows the warning and the Budget stat card updates.

---

## 11. F08 — Catering (GUI Catering) (≈45 s)

1. Open **Catering** (Ctrl+6) and show the dietary summary: vegetarian, vegan, halal, kosher, nut allergy, gluten-free.
2. Click **✦ Generate catering plan**, review, and apply.
   - Expected: every dietary need is covered, and the plan includes a total estimate and allergen notes.

**CLI:**
```
.\eventpilot.cmd catering summary
```

---

## 12. F09 — Tasks (GUI Tasks + CLI) (≈1 min)

1. Open **Tasks** (Ctrl+8). Type `Sam, Lee` as the team and click **✦ Generate task plan**. Review and apply.
   - Expected: the cards show the owner avatar, the due-date chip and "after tX" dependencies.
2. **Drag** a card with no dependency to **Done**.
   - Expected: the column highlights while you drag, the card moves, and a toast appears.
3. **Error case.** Drag a card that says "after t3" to Done while t3 is still open.
   - Expected: it is refused with "blocked by: t3".

**CLI:**
```
.\eventpilot.cmd task list
.\eventpilot.cmd task status t9 DONE
```
- Expected for the last command: `ERROR "Send reminder to attendees" is blocked by: t3 Send invitations`.

---

## 13. F10 — Invitations (GUI Invitations + CLI) (≈1 min)

1. **Error case, CLI.** Do this on an event that is still in DRAFT, such as a second event:
   ```
   .\eventpilot.cmd invite draft --tone formal
   ```
   - Expected: "Invitations can only be drafted or sent while the event is in PLANNING or CONFIRMED".
2. In the GUI, on the PLANNING event, open **Invitations** (Ctrl+7).
3. Choose the tone **formal** and click **✦ Draft invitations**. Review and apply.
4. Select one draft, edit its body, and click **Save edit**.
5. Click **Send**.
   - Expected report: **10 sent, 1 failed**. The failure is `liam.murphy@example.invalid`, on purpose.
   - The sent e-mails are in `data/outbox/`.

---

## 14. F11 — FAQ agent (GUI chat + CLI) (≈1 min)

1. On the Dashboard, under *Knowledge for the FAQ agent*, add the note `Free parking is available in the north lot.`
2. In the chat, switch to **Ask FAQ** and ask `Is there parking?`.
   - Expected: the answer quotes the note and cites its source, e.g. `[note:1]`.
3. **Escalation case.** Ask `Is there a dress code?`.
   - Expected: the agent says it has forwarded the question to the organizer.
   - The Dashboard **Alerts** card now shows "Unanswered attendee question".
4. Click **Mark FAQ questions handled**.

**CLI:**
```
.\eventpilot.cmd ask "Is there parking?"
.\eventpilot.cmd alerts
```

---

## 15. UC15 again — Confirm (≈20 s)

1. The venue is chosen now, so click **✔ Confirm**.
   - Expected: the badge turns **CONFIRMED** and turns green.

---

## 16. Outro (≈20 s)

- Run `.\eventpilot.cmd ai` to show the provider and, in `shell` mode, the session's token usage.
- **Say:**
  > "All twelve features run through one facade, with patterns: Facade, Command (undo/redo), Observer (live views and alerts), Strategy (LLM provider), Template Method (workflows), State (lifecycle) and Adapter (venue catalogue)."

---

## Test checklist (quick manual test, ≈10 min)

Run the steps in order on an empty `data/` folder. Each line is: action → expected result.

| # | Action | Expected |
|---|---|---|
| 1 | `.\mvnw.cmd clean test` | 101 tests, 0 failures |
| 2 | `.\eventpilot.cmd` | Window opens; dashboard shows "No event selected" with a **+ New event** button |
| 3 | Chat: `what is the weather like tomorrow` | Declined; no event created |
| 4 | Chat: `Plan a robotics workshop for 80 people` → answer `On December 12 from 9am to 5pm in Toronto, budget $9000` | Questions, then event created; DRAFT badge; toast |
| 5 | Click **✔ Confirm** (needs PLANNING first: click **▶ Start planning**) | Refused: "choose a venue" |
| 6 | Attendees → Import `samples/attendees.csv` | 12 imported, 2 rejected, 1 duplicate; pie chart |
| 7 | Venue → Recommend → Apply option | Review dialog with 3 options and tool trace; venue card filled |
| 8 | Agenda → Generate → Apply | Timeline filled; no conflicts |
| 9 | Speaker availability `9 to 12` | Error dialog, nothing added |
| 10 | `.\eventpilot.cmd event rooms "<room>:40"` then Agenda → Check conflicts | Red rows |
| 11 | Agenda → Ask agent to fix → Apply | Red rows gone |
| 12 | Modify: `move session 2 to 9:00` | Agent asks which session |
| 13 | Modify: `rename the closing networking to Farewell reception` → Ctrl+Z → Ctrl+Y | Applied, undone, redone; views update live |
| 14 | Budget → Generate → expense venue 3800 | OVER PLAN, Dashboard alert |
| 15 | Catering → Generate | All diets covered |
| 16 | Tasks → Generate (`Sam, Lee`) → drag a blocked card to Done | Refused: "blocked by" |
| 17 | Invitations → Draft (formal) → Send | 10 sent, 1 failed; files in `data/outbox` |
| 18 | FAQ: `Is there parking?` after adding the note; then `Is there a dress code?` | Answer with source; escalation alert on Dashboard |
| 19 | **✔ Confirm** | CONFIRMED (green badge) |
| 20 | Sidebar **Dark mode**, **Ctrl+J**, **Ctrl+1..8**, restart the app | Theme switches; chat hides/shows; pages switch; theme is remembered |
