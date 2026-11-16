# Five-dice baseline QA checklist (Java 8)

- [ ] `javac -version` reports a Java 8 compiler on the training workstation.
- [ ] `scripts/build.bat` or `bash scripts/build.sh` succeeds.
- [ ] `scripts/test.bat` or `bash scripts/test.sh` passes the scoring tests.
- [ ] JAR starts the Java Swing GUI (`java -jar dist/Jamb-Multiplayer.jar`).
- [ ] Exactly **five** dice appear and none can be held before the first roll.
- [ ] All dice values are between 1 and 6 after a roll.
- [ ] Holding/unholding and three-roll limit work correctly.
- [ ] Only current player may roll or score; column ordering blocks invalid writes.
- [ ] Najava may be announced after first roll; announced cell must be scored.
- [ ] All five player seats work; a sixth player receives a room-full error.
- [ ] Full network integration completes 260 turns and supports rematch/reconnect.
- [ ] GitHub Actions compiles/tests with Temurin Java 8.

This checklist establishes the five-dice behavior BEFORE the sixth-die exercise.
