# Trainee Assignment — Implement the Sixth Die (Java 8)

## Scenario

You are working on a fully functioning 5-dice Serbian Jamb application. Product Owner requests a **sixth die**. Keep the multiplayer architecture (up to five *players*) intact. This change is about the number of *dice*, not the number of players.

## Definition of Done

1. **GUI:** All players see **six** dice; the last one is the same size/style as the others. Holding/unholding die #6 works with both mouse and network state updates.
2. **Rolling:** A roll produces **six independently rolled** values from 1 to 6. Rerolling only modifies unheld dice. Still no more than **three rolls** per turn.
3. **Synchronization:** Each connected client receives all six dice values and six hold flags from the authoritative server. A disallowed seventh die index is rejected gracefully (no crash).
4. **Scoring:** Update the rules for *your agreed six-dice variant*. Decide first whether the sixth is a wild/reserve die, whether the best five count for combination rows, and how Max/Min work. Document the decision. Never silently discard the sixth die or count an invalid combination.
5. **Preview:** The available score shown in the UI equals the score the server saves for the same dice and roll count. Invalid dice arrays are rejected predictably.
6. **Multiplayer regression:** Five players can join, chat, roll, score, disconnect/reconnect and complete the game. Host rematch still works. Existing column-order and Najava restrictions are unchanged unless the approved rules require otherwise.
7. **Java 8:** Build with `scripts/build.bat` under **JDK 8**. No records, text blocks, switch expressions, `List.of`, `String.isBlank`, or newer APIs. GitHub Actions Java 8 build and tests must pass.

## Work plan (no solutions included)

Read `GameRules.java`, `GameServer.java`, `GameClient.java`, `GameWindow.java`, and `GameRulesTest.java`. Track dice creation, indices, serialization, client rendering, scoring, and tests. Search for the literal `5`, but distinguish **dice count**, **five-player limit**, **five-of-a-kind**, and **score rows**. They must not all become six!

Start with a short test plan. Update production source, then update/add tests. Open a pull request with an explanation of the chosen six-dice scoring rules and the regression evidence.

## Suggested QA test scenarios

| Test ID | Scenario | Expected result |
| --- | --- | --- |
| D6-01 | New turn, first roll | Exactly six dice have values in 1..6 |
| D6-02 | Hold only die #6, reroll | Die #6 unchanged; all other dice reroll normally |
| D6-03 | Unhold die #6, reroll | Die #6 becomes eligible to reroll |
| D6-04 | Send `HOLD|5` (zero-based die #6) after roll | Accepted; its hold flag is broadcast |
| D6-05 | Send `HOLD|6` | Server rejects with a handled error; no crash |
| D6-06 | Try fourth roll | Rejected; turn state unchanged |
| D6-07 | Score straight/full/poker/Jamb | Matches the approved six-dice rules in scorer, preview, and server |
| D6-08 | Score with a 5-dice/7-dice array | Validation rejects unexpected dice array lengths |
| D6-09 | Five-player match | All scoreboard totals remain synchronized; game finishes |
| D6-10 | Reconnect during a turn | Sixth die and its hold state are restored correctly |
| D6-11 | Start a rematch | All six dice reset and turn order restarts |
| D6-12 | Compile with JDK 8 | Builds without newer Java language/API features |

## Bonus challenges

- Make number of dice configurable (5/6), including protocol version checks to prevent mismatched clients and servers.
- Add automated GUI tests or screenshots proving the sixth die renders on typical laptop resolutions.
- Add deterministic random-seed injection for controlled server integration tests without weakening production randomness.

## Submission

1. GitHub pull request with code and tests.
2. `TEST-REPORT.md` covering test case IDs, actual results, defects and fix verification.
3. A short `RULES-DECISION.md` justifying the scoring semantics of the sixth die.
4. A runnable JAR compiled for Java 8.

**Instructor note:** This starter intentionally includes only the working five-dice implementation. Keep instructor solutions separate from the trainee repository.
