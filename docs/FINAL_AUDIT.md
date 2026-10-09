# Report B — Defect and risk audit

Audit date: 2026-10-09. Scope: all source/build/security boundaries described in independent Report A (REPOSITORY_EXPLANATION.md), plus engine/frontend tests, lint, merged manifests and APK packaging. “Verified core fix” means a regression test executed; “Code mitigation” means compiled code whose platform correction has not been exercised. No issue is marked fully fixed solely because code was written. Device absence and specification gaps prevent release acceptance.

Severity uses CRITICAL / HIGH / MEDIUM / LOW / INFORMATIONAL. No CRITICAL issue was established by this inspection; that is not a claim that adversarial/device testing is complete.

## Verified core corrections

### AUD-001 — MEDIUM — Integer precision/type loss

File/function: engine/Expressions.kt `evaluate`, Compiler.kt expression inference. Reproduction: compare 9007199254740993 and 9007199254740992, or assign INTEGER + INTEGER back to an INTEGER counter. Expected: distinct numbers and integer result; overflow rejected. Previous actual: Double rounded equality and arithmetic returned Decimal, preventing the assignment. Fix: BigDecimal comparison, checked Long arithmetic, matching inference. Verification: `integerArithmeticPreservesTypeAndPrecision` passes, including overflow and compiled assignment. **Verified core fix.**

### AUD-002 — HIGH — Unbounded JSON structural nesting

File/function: engine/SafeInput.kt `json`, Compiler.compile, Backups.import. Reproduction: import deeply nested JSON, quoted bracket strings or unbalanced text. Expected: bounded safe rejection before parsing/writes. Previous actual: untrusted parser input had no structural preflight. Fix: quote/escape-aware depth/size checks before parsing. Verification: `safeJsonBoundsAndQuotedBraces`, malformed graph tests pass; Android `malformedImportHasNoWrites` compiled but not run. **Core preflight verified; database rollback device gate pending.**

### AUD-003 — HIGH — Weekly recovery occurrence identity mismatch

File/function: engine/Scheduling.kt recovery/window keys. Reproduction: miss Friday alarm, reopen Sunday, then receive original delivery/recover again. Expected: same current-window key, one logical run. Previous actual: recovered delivery could use a different identity or weekday-based eligibility. Fix: window-start identity and occurrence key shared by scheduled/recovered deliveries. Verification: `weeklyWindowSundayRecovery`, `catchupUsesScheduledOccurrenceIdentity`, `oldWeeklyWindowDeliveryCannotCauseCatchupStorm` pass. **Schedule logic verified; Room/platform dedup gate pending.**

### AUD-004 — HIGH — Reusable dependency version isolation

File/function: engine/Runtime.kt `start`, CALL/RETURN/handler frames. Reproduction: suspend parent, edit child then resume. Expected: old run uses captured transitive definitions/input/caller values. Previous actual: late resolution could read edited child; error handling could leave child context. Fix: dependency library captured at start, explicit caller frame/value restoration. Verification: `reusableInputReturnAndDependencySnapshot`, `tryHandlesFailure` pass. **Verified core behavior; process-death Android exercise pending.**

### AUD-005 — MEDIUM — Fourth choice branch loss

File/function: blockly-editor/catalogue.js choice mutation; Compiler.kt; Runtime.respond. Reproduction: four choices with separate fourth action; save/reload and choose fourth. Expected: fourth branch preserved/routed independently. Previous actual: fixed first/second/other branch structure did not represent every option. Fix: CHOICE indices and serialized mutation state, native per-choice routing. Verification: Node fourth-choice roundtrip and `arbitraryChoiceBranchRouting` pass. **Core/editor serialization verified; notification UI gate pending.**

### AUD-006 — MEDIUM — Missing execution-wide deadline

File/function: engine/Model.kt Execution.deadline; Runtime.tick/respond. Reproduction: SET TIMEOUT then a longer wait or never-expiring question; send response after overall deadline. Expected: persisted expiration caps wake and cannot be bypassed. Previous actual: only interaction-local deadlines existed. Fix: execution deadline checked before transitions, wait capped, response rejection. Verification: two deadline ControlTests pass, including serialized restoration. **Verified core fix; actual alarm wake pending.**

### AUD-007 — MEDIUM — Clock/overnight boundary and overflow guards

File/function: Scheduling.locationEligible/resolve, Runtime.waitClock, Expressions timestamp arithmetic. Reproduction: Friday-only 22:00–02:00 window on Saturday 01:00; spring-gap 02:30 wait; subtract Long.MIN_VALUE duration. Expected: Friday window eligibility, valid shifted clock occurrence, overflow rejection. Previous actual: location eligibility absent; clock wait absent; negating minimum Long could overflow. Fix: starting-day semantics, java.time resolution and Math.subtractExact. Verification: corresponding three ControlTests pass. **Verified pure behavior; location delivery pending.**

### AUD-008 — MEDIUM — Response bounds and persistent type conflicts

File/function: Compiler.validate/validateSharedVariables/validatePersistentValues; Runtime.respond; Coordinator.save; Backups.import. Reproduction: text MAX=4 receives five characters; import different global type over an orphan value; change persistent type while old run is suspended. Expected: visible validation/rejection, preserved old state. Previous actual: text maximum ignored and some incompatible declarations reached runtime. Fix: response declaration/bounds checks and stored/active snapshot type checks. Verification: `textBoundsAndResponseVariableAreValidatedBeforeAndAfterWaiting`, `orphanPersistentValuesAndSuspendedVersionTypesAreProtected` and global validation tests pass, including suspendedCallerDeclarationsRemainProtectedInsideChildWorkflow. **Core checks verified; native save/import integration pending.**

## Platform corrections awaiting device execution

### AUD-009 — HIGH — Queued alarm reconciliation/version race

File/function: Platform.schedule, AlarmReceiver, Coordinator.triggerInternal. Reproduction: alarm queued, foreground reconcile advances nextAt, old queued worker executes; separately edit workflow before delivery. Expected: valid original occurrence can start, edited version cannot. Previous actual: nextAt comparison could reject valid queued delivery or old alarm use edited definition. Fix: propagate version and check actual recurrence/key/grace with Scheduling.acceptsDelivery. Verification: queued version/grace tests pass; actual Android race remains AT-019/030. **Code mitigation with core proof, platform unverified.**

### AUD-010 — HIGH — Stale effects after cancellation/deletion

File/function: Coordinator.cancel/delete/drain; Platform.cancelAlarm/cancelAllExecutionNotifications; Database outbox FK. Reproduction: cancel/delete between committed outbox and notification drain, then retry a queued worker. Expected: no restored interaction or future automatic run. Previous actual: stale outbox/work or informational tags could survive cleanup. Fix: remove outbox, cancel tagged jobs/alarms and owned tags; drain verifies live interaction token/state. Verification: build/lint pass; native deletion/notification retry procedure AT-029 unexecuted. **Code mitigation, not certified fixed.**

### AUD-011 — MEDIUM — Stale UI mutations/debug context

File/function: FlowViewModel.rename, Coordinator.enable/rename, Screens.Activity debug, Simulator pause. Reproduction: toggle using a row captured before save; rename during reconciliation; debug old run after child edit; pause while waiting and continue. Expected: latest configuration retained, old library used, blocked state preserved. Previous actual: stale row overwrite/latest child lookup or RUNNING restore could alter behavior. Fix: reload current row, serialized partial rename, snapshot library and remembered pause state. Verification: native compilation/lint plus core breakpoint test; actual Compose interaction pending. **Code mitigation.**

### AUD-012 — HIGH — Permission denial/revocation and API compatibility

File/function: Platform permission/scheduling/notification/register functions; Screens permission/SAF actions; Editor reactive state. Reproduction: revoke permissions during operation; import on API 29; read Compose-managed resources outside state observation. Expected: no crash, truthful status/fallback, supported read and refreshed simulator. Previous actual: lint identified missing revocation guards, an API-33 stream call and state read hazards. Fix: explicit checks/catches, bounded compatible buffer reads and observed StateFlows. Verification: lint zero errors and Kotlin compile; AT-003/018/028 device procedures remain. **Static mitigation only.**

## Open defects, requirements and risks

Latest hosted verification: run 37971159000 (2f36bfd) passed build/editor/core checks, ten native cases and both separate force-stop/reopen phases on API 29/36. AUD-013's verification boundary now includes actual new-process/version/token/wake restoration; physical alarm/reboot, geofence, battery and accessibility gates remain open.

### AUD-013 — HIGH — Android acceptance has not executed

Files/classes: app Android sources, native tests, TESTING.md AT table. API 29/36 KVM emulators now install/launch the app and pass nine database/UI/interaction tests (run 37967271781), including recreation, persisted progress and separate branch notifications/responses. This exposed and verified fixes for AUD-022/023. Physical process death/reboot, geofence movement, permission revocation, accessibility and battery acceptance remain **OPEN**; emulator smoke coverage does not certify all 30 gates.

### AUD-014 — HIGH — Parallel/JOIN previously blocked sibling progress

File/function: Runtime.tickBranches, catalogue.js PARALLEL. Previous reproduction: A waited one hour and blocked B's message. Fix: persisted branch executions and round-robin position, earliest wake, independent tokens/notification tags, isolated local merge with conflict rejection, ordered persistent values, JOIN/failure/TRY propagation, shared budgets and parent cancellation guards. Verification: twelve dedicated parallel regressions and concurrent native questions/notification ownership pass on API 29/36. **Correction verified within tested coverage; physical stress/lifecycle acceptance remains open.**

### AUD-022 — HIGH — End-of-stack cursor restarted after persisted response/wait

Initial emulator checklist completion timed out on both API 29/36: nullable cursor was omitted by serialization and decoded as definition.entry, restarting the final question. Fix: encode explicit nulls, default a missing legacy cursor to null, and explicitly initialize Runtime.start at the entry. Verification: null/legacy cursor and serialized JOIN JVM tests pass; native checklist completion/duplicate-response regression passes on API 29/36.

### AUD-023 — MEDIUM — Upsert silently ignored a duplicate secondary event key

Initial emulator eventIdentityIsUnique showed Room upsert swallowing the insert conflict and updating a nonexistent primary key. Fix: new execution creation uses an aborting @Insert, while existing snapshots retain @Upsert. Verification: instrumentation uses the Coordinator insertion path and passes on API 29/36. Database uniqueness was present but silent conflict semantics were unsuitable for new-run creation.

### AUD-015 — MEDIUM — Unbounded persisted history

File/function: Database.kt observeExecutions/observeDiagnostics and Coordinator writes. Reproduction: months of frequent executions/recovery diagnostics. Expected: configured retention with preserved dedup records/active state. Actual: display capped but rows persist indefinitely. Required fix: transactional retention and separate retained occurrence identity policy; measure storage/load under realistic volumes. Verification: **OPEN**, no longevity test.

### AUD-016 — MEDIUM — Geofence rebuild frequency/gaps

File/function: Coordinator.reconcileInternal and Platform.register. Reproduction: foreground entry/15-minute recovery repeatedly removes and adds unchanged geofences. Expected: efficient health-aware reconciliation with accurate status. Actual: full rebuild, potentially introducing brief gaps/extra Play Services work. Required fix: fingerprint/generation invalidation after supported boot/update/permission events with failure retries, plus physical-device battery/delivery testing. Verification: **OPEN**, no measured timing/battery data.

### AUD-017 — MEDIUM — Remaining interaction/platform feature gaps

Files/classes: Platform.notification, Interaction, Coordinator.snooze, catalogue.js. Reproduction: attempt configured priority/channel/ongoing notification, custom update tag/escalation, richer snooze or simultaneous branch questions. Expected: supported requested policies. Actual: one channel/fixed tags/group and limited snooze; unsupported policies absent. Required fix: explicit versioned effect/interaction configuration, owned identities and native adapter tests. Verification: **OPEN**, full list in KNOWN_LIMITATIONS.

### AUD-018 — MEDIUM — Retained compatible stack and migration gap

Files: app/build.gradle.kts, root build files, Room schema 1. Reproduction: substitute current Compose BOM requiring SDK37/AGP9.1+ or upgrade stored database schema. Expected: latest compatible stack and tested non-destructive upgrade. Actual: pinned older compatible stack; initial schema only. Required fix: coordinated toolchain/dependency/KSP migration and schema migration fixtures. Verification: **OPEN requirement**; current pins build successfully, no destructive fallback.

### AUD-019 — LOW — General field errors lack uniform remediation metadata

File/function: Compiler.compile Issue construction and Editor errors. Reproduction: malformed literal/date/scoped enum field. Expected: error code, severity, correction and block highlight for every validation failure. Actual: Issue has severity/correction defaults and structural/type issues carry block IDs, but some parse failures surface general exception text; code/severity are not sent to the editor and warnings/specific remediation are incomplete. Required fix: structured per-block parser error conversion and warning model/UX tests. Verification: **OPEN**.

### AUD-020 — INFORMATIONAL — Buildable unsigned release and intentional editor JavaScript

Files: app/build.gradle.kts, Editor.kt. Reproduction: build release without signing environment; inspect JavaScript lint warning. Expected: no credential invention, offline editor with controlled bridge. Actual: unsigned R8 APK and intentionally enabled local JS. Fix/policy: optional secure environment signing; local origin/main-frame listener, request/navigation/file/content restrictions and bounded native compiler. Verification: build/manifest inspection and debug signature verified; signed-release launch and adversarial WebView tests **pending**.

### AUD-021 — MEDIUM — CI Android tool provisioning failure

File/step: .github/workflows/android.yml SDK setup. Reproduction: first hosted run reached SDK install step and exited 127; native checks skipped. Expected: sdkmanager provisioned on PATH, then tested build and APK upload. Actual before correction: runner assumptions failed. Fix: explicit Android SDK setup action and immutable pins for supported action releases. Verification: hosted run after correction must be inspected; final CI result is recorded in BUILD_ARTIFACTS.md. Earlier failed runs remain visible. **Do not infer success from a local build.**

Release decision: retain development status until open functional requirements and Android acceptance gates are resolved. Reports describe implementation and evidence independently; they do not certify production readiness.
