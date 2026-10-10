# Final defect and risk audit — Report B

Updated 2026-10-10. This audit replaces the older open implementation inventory. Report A describes the current source. Verification is bounded by TESTING.md; final physical acceptance uses PHONE_ACCEPTANCE.md.

| Audit | Correction and verification |
|---|---|
| AUD-001 arithmetic/type precision | Checked integer arithmetic and exact comparison; core regressions |
| AUD-002 input nesting/size | Quote-aware depth 128, UTF-8 workspace/snapshot 2 MB and backup 16 MB; core/native rejection/rollback |
| AUD-003 window identity | Shared stable schedule/recovery keys, durable ledger independent of history; core windows/native event identity |
| AUD-004 dependency version isolation | Transitive captured IR, typed multi-parameter/output/status calls and caller restoration; core restart plus external native process fixture |
| AUD-005 fourth choice | Serialized dynamic inputs and independent CHOICE routing; Node/core tests |
| AUD-006 overall deadlines | Persisted deadline caps wake and rejects late response; core/native durable timer |
| AUD-007 clock/overnight/overflow | Starting-day windows, java.time gap/overlap and checked timestamp math; core tests |
| AUD-008 values/response types | Save/import/shared/captured declaration and response bounds; core/native invalid-value rollback |
| AUD-009 queued schedule races | Scheduled key/version captured in explicit delivery; disabled/edited guards and ledger dedup; core tests |
| AUD-010 cleanup | Owned question/message tags, cancelled work/alarms, live-token outbox drain and cascade; native ownership/deletion/process cleanup |
| AUD-011 UI/debug context | Partial rename updates, observed resources, captured-run debug, saved drafts and replay; native recreation/core simulation |
| AUD-012 permissions | Guarded adapter calls, visible health/fallback, bounded Play Services API waits; external native notification recovery; physical location/alarms remain acceptance checks |
| AUD-013 Android verification | Hosted native suites and external process/R8 phases execute; final current-source run is still being checked |
| AUD-014 parallel/JOIN | Durable round-robin branches, independent questions/wakes, conflict merge/failure/cancel budgets; twelve core regressions and native questions |
| AUD-015 storage/history | Configurable terminal retention, bounded history/ledger/diagnostics plus snapshot/value/library budgets; core overflow regressions |
| AUD-016 geofence rebuild cost | Persisted configuration fingerprint reused; invalidated on supported recovery/permission/service events; physical battery behavior remains unmeasured |
| AUD-017 rich interactions | Channels/group/category/expiry/ongoing, named update/cancel, bounded snooze/follow-up/dismissal, templates; native ownership/capture/permission coverage |
| AUD-018 toolchain/migrations | SDK 37.0, AGP 9.4.1/Gradle 9.6/Kotlin 2.4.21/KSP/Hilt/current AndroidX; exported Room 1–5 and explicit migration validation |
| AUD-019 configuration/debug/backup completeness | Defaults/presets/help/data controls, debugger/event/replay and complete backup schema 2 implemented and tested |
| AUD-022 end cursor restart | Explicit null cursor, legacy default null, explicit Runtime.start entry; core/native serialized completion |
| AUD-023 secondary identity conflict | Aborting insert for new executions instead of silent upsert; native uniqueness regression |
| AUD-024 backup envelope/merge mismatch | 16 MB envelope preflight independent of workspace cap; retained template used to compile merge; native regressions |
| AUD-025 editor/CI compatibility | Idempotent trusted-page handshake, direct old-WebView message decoding, external permission phase, explicit Espresso 3.7 and direct GPU renderer; current hosted run pending |
| AUD-026 editor renderer termination | Explicit handled termination, failed-view cleanup and reload from saved draft; actual renderer termination/reload included in the native fixture |

No established application critical/high defect is knowingly left without a code correction. This is not a claim that untested physical behavior is defect-free. Keep current-source hosted failures open until they pass; do not reinterpret build success as device success.

Remaining risks: Android/OEM background delay; physical geofence registration/movement and battery; user accessibility/TalkBack; notification channel/permission settings; private signing key continuity. Room and DataStore do not share an atomic transaction during storage failure, so retain/check a restored backup. Plain JSON exports include sensitive location/history data. Runtime supports local allowlisted effects, not arbitrary exactly-once external side effects.
