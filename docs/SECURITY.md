# Security and privacy

No login, backend, analytics, ads or telemetry. Location is used only for user-defined saved geofences/current-position requests. The application has no Internet permission. Android cloud backup and device transfer are excluded; explicit SAF export is the supported backup mechanism. JSON exports contain private workflow text and saved coordinates, so store them intentionally.

The WebView loads only local assets over WebViewAssetLoader's HTTPS origin. File/content access and mixed content are disabled; external resources and navigation are blocked. CSP disallows network connections. The bridge is a WebMessage listener restricted to the local origin and main frame; messages are bounded and compiled natively. No arbitrary user-generated source is evaluated. The required JavaScript-enabled lint warning is retained and audited rather than broadly suppressed.

Operational receivers are not exported. Geofencing alone uses a mutable, explicit app-owned PendingIntent as required by the platform; alarm/response/open intents are immutable. Notification actions include an unpredictable per-interaction token validated against persisted execution state and deadline. No PendingIntent accepts an arbitrary component or URI supplied by an imported workflow.

Import limits: 2 MB, depth 128, 500 automations/locations, 2,000 blocks per workflow, 64 expression nesting, 10,000 steps and bounded loops/calls. Invalid imports are fully compiled before transactional writes; duplicate resource IDs and recursive dependencies are rejected. No imported executable object graph is trusted. SafeInput protects the JSON parser from excessive structural nesting.

Private debug traces remain in Room and may contain user-entered messages. There is no remote log sink. Secrets/signing keys/local SDK/build outputs are ignored by Git. No critical vulnerability is claimed absent merely because compilation succeeds; platform and adversarial device tests remain necessary.
