# QA Report — Java 8 / Five-Dice Jamb Starter

Date: 2026-11-16

## Build and API compatibility

- `bash scripts/build.sh` — PASS, with JDK 21 compiler targeting Java 8 (`-source 8 -target 8`).
- Independent `javac --release 8` compilation of all main/test sources — PASS. This checks Java 8 language and API availability even though the local JDK is newer.
- `javap -verbose` for `com.jamb.Main` from the finished JAR — PASS: **major version 52, minor version 0**, i.e. Java 8 class files.
- Manifest contains `Main-Class: com.jamb.Main` — PASS.
- `java -jar dist/Jamb-Multiplayer.jar --server 0` — PASS: server starts and listens on a TCP port.

## Functional regression

- Five-dice scorer and column-order/bonus tests — PASS.
- A six-dice scoring input rejected by the five-dice starter — PASS (expected baseline behavior).
- Five player joins, sixth player refused — PASS.
- Three rolls maximum; holding one die across a reroll; sixth die index rejected — PASS.
- Server enforces legal turn and column order; announces and scores Najava — PASS.
- End-to-end 5-player match, 260 moves, reconnect, skip disconnected seat, winner, rematch — PASS.

## Limitations

- Java 8 **runtime execution** on an installed JDK/JRE 8 was not available in this local environment. The GitHub Actions workflow explicitly uses **Temurin JDK 8** and is ready to run after this project is pushed.
- GUI has been source-compiled for Java 8 but was not interactively opened in this headless build environment. The GUI preview is provided as a reference.
- Multiplayer uses unencrypted TCP and is intended for a trusted LAN or private VPN, not direct exposure to the public internet.
- Sixth-die functionality is intentionally **not implemented** and is documented as a trainee exercise.
