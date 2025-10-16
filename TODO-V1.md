# Android SDK v1.0 Follow-up

- [x] Stop requiring an `Activity` to submit assemblies; accept a plain `Context` so services and WorkManager jobs can drive uploads.
- [x] Deliver per-upload progress and completion/failure callbacks with the new listener API.
- [ ] Deliver listener callbacks on the main thread (provide opt-out for background execution).
- [ ] Improve upload persistence (resume support, optional WorkManager integration, clearer API around pausing/resuming).
- [ ] Establish end-to-end signature-provider + tus integration harness:
  - [x] Add an instrumentation test skeleton that exercises signature-provider uploads via a MockWebServer and real Transloadit credentials (skipped by default when secrets are absent).
  - [x] Drive an actual upload through the Android client, hit Transloadit over tus, and assert the resized result metadata.
  - [ ] Document required secrets/Gradle arguments and wire an opt-in CI job (nightly or manual) that runs the E2E flow.
  - [ ] Expand the test to pause midway, resume the tus upload, and assert SSE progress/completion events.
- [ ] Finalize public API naming/packages and publish a migration guide from the legacy `AndroidAsyncAssembly` API.
- [ ] Refresh samples (Java + Kotlin) and docs to demonstrate the new `AndroidAssembly` workflow end-to-end.
- [x] Update CI and release automation to build/test against the embedded java-sdk source dependency.
