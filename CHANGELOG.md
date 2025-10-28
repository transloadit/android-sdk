# Changelog

## 0.2.0 / TO BE RELEASED

Below 1.0 we reserve the right to ship breaking changes; please review the highlights carefully before upgrading.

- **Breaking:** Replaced the deprecated `AndroidAsyncAssembly` wrapper with a new `AndroidAssembly` that uses the modern SSE workflow, dispatches listener callbacks on the main thread by default, and runs on a shared daemon executor (no more per-instance thread leaks).
- **Breaking:** The SharedPreferences store that backs tus resumable uploads now lives under `transloadit_android_sdk_urls` (previously typo’d `tansloadit_android_sdk_urls`). Migrate any persisted URLs if you rely on in-flight resumes.
- **Breaking:** Building the SDK now requires JDK 17+. We still emit Java 11 bytecode so consuming apps running older toolchains can desugar successfully.
- Added `AndroidAssemblyWorkConfig` and `AndroidAssemblyUploadWorker`, making it straightforward to enqueue WorkManager jobs that either use inline secrets or fetch signatures from a remote endpoint (including remote-only `/http/import` style assemblies).
- Hardened background uploads: ensure assemblies are closed after use, unblock completion latches on SSE polling failures, treat signature-provider errors without bodies as deterministic failures, and tolerate configs that upload zero local files.
- Introduced `pauseUploadsSafely` / `resumeUploadsSafely`, plus listener helpers that allow switching between main-thread and direct callback execution.
- Updated samples and documentation to showcase remote signature authentication, the WorkManager flow, and the new listener API; clarified the JDK requirement and the main-thread behaviour.
- Expanded CI and Docker-based test harnesses (dual Java 17/21 matrix, executor/thread leak detection, remote-signature fixtures) and bumped to `com.transloadit.sdk:transloadit:2.2.4` so Android stays aligned with the latest Java SDK.

## 0.1.0 / 2025-10-15

- Added support for external signature generation to improve security ([#19](https://github.com/transloadit/android-sdk/issues/19))
  - New constructors in `AndroidTransloadit` accepting `SignatureProvider` instead of secret
  - Enables secure signature generation on backend servers instead of embedding secrets in APK
  - Prevents secret extraction through APK decompilation
  - Added comprehensive documentation and examples for signature injection
  - Added unit tests for signature provider functionality
- Adopted `com.transloadit.sdk:transloadit:2.1.0` and tus-java-client 0.5.1 to match java-sdk
- Added Docker-based test harness for reproducible local builds

## 0.0.10 / 2024-03-20

- 0.0.9 has been published without AAR files, this release ships them.

## 0.0.9 / 2024-03-20

- Updated dependency for Transloadit Java SDK to 1.0.0
- This update includes the updated signature authentication method, which is now required for all requests.

## 0.0.8 / 2023-07-17

- Changing method signatures including Activity to Context in order to make the SDKs usage more flexible.
- This is considered as not breaking, as a Activity is a Context, and the SDK will still work as before.

## 0.0.7 / 2022-10-30

- Updated dependency for Transloadit Java SDK to 0.4.4 => includes Socket-IO 4 and a security patch
- Updated to androidx.appcompat:appcompat:1.5.1
- Set compileSdkVersion to 31, and targetSdkVersion 31

## 0.0.6 / 2022-02-03

- Update dependency for Transloadit Java SDK to 0.4.2
- Add Android SDK version to Transloadit-Client header
- Updated Tus-Android to 0.1.10

## 0.0.5 / 2022-01-10

- Update dependency for Transloadit Java SDK to 0.4.1,
  this update is recommended as it contains patches for known security vulnerabilities.

## 0.0.4 / 2021-02-25

- Update dependency for Transloadit Java SDK to 0.1.6

## 0.0.3 / 2018-07-18

- Update dependency

## 0.0.2 / 2018-04-07

- Initial release
