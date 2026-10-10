# Requirements and release gates

The complete source specification is preserved in PROJECT_BRIEF.md. No planned functionality is considered implemented until code and verification exist.

| Stage | Deliverable | Verification |
|---|---|---|
| 1 | Architecture, decisions, traceability | Repository/toolchain inspection |
| 2 | Android foundation, Room, Compose | Compile |
| 3 | Typed compiler, interpreter, snapshots | JVM tests |
| 4 | Bundled Blockly, secure bridge | Frontend roundtrip + compile |
| 5 | Time recurrence, windows, reconciliation | JVM boundaries + device procedure |
| 6 | Geofences and locations | Mock events + device procedure |
| 7 | Interaction persistence, notifications | Duplicate/expired-response tests |
| 8 | Advanced control and variables | Runtime guards and isolation tests |
| 9 | Deterministic simulator | Fake effects and clock tests |
| 10 | Import/export, templates, diagnostics | Roundtrip and malformed-input tests |
| 11 | File/defect audit | Findings and regression checks |
| 12 | APK and guides | Build, lint, tests, artifact inspection |

AT-001 through AT-030 are mapped in TESTING.md to automated evidence and physical procedures. IMPLEMENTATION_STATUS.md records implemented software. The owner completes PHONE_ACCEPTANCE.md after the final automated build/device checks; physical behavior is not inferred from emulator results.
