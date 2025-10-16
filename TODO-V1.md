# Android SDK v1.0 Follow-up

- [ ] Stop requiring an `Activity` to submit assemblies; accept a plain `Context` so services and WorkManager jobs can drive uploads.
- [ ] Deliver per-upload progress and completion/failure callbacks with the new listener API.
- [ ] Deliver listener callbacks on the main thread (provide opt-out for background execution).
- [ ] Improve upload persistence (resume support, optional WorkManager integration, clearer API around pausing/resuming).
- [ ] Finalize public API naming/packages and publish a migration guide from the legacy `AndroidAsyncAssembly` API.
- [ ] Refresh samples (Java + Kotlin) and docs to demonstrate the new `AndroidAssembly` workflow end-to-end.
- [ ] Update CI and release automation to build/test against the embedded java-sdk source dependency.
