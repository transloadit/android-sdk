# Changelog

## 0.2.0 / 2025-10-28

Below 1.0 SemVer allows us to make breaking changes, and we have shipped a number of them in this release, please review carefully.

- **Breaking:** Removed dependency on the Java SDK's deprecated `AsyncAssembly` API and introduced a new `AndroidAssembly` wrapper built on the modern SSE-based workflow
- **Breaking:** SharedPreferences backing resumable uploads now uses `transloadit_android_sdk_urls` (previously typo’d `tansloadit_android_sdk_urls`). Existing persisted tus entries will need manual migration if backward compatibility is required.
- **Breaking:** Building the SDK now requires JDK 17+. Published AARs still target Java 11 bytecode so consuming apps can desugar on older toolchains.
- Upgrade dependency to `com.transloadit.sdk:transloadit:2.2.4` to align with the latest Java SDK release and pick up the simplified SSE handling.
- Keep the Android Docker and CI parity harness aligned with the Java SDK release that ships the stabilized SSE behaviour, ensuring both suites exercise the same SSE fixtures.
- Default `AndroidAssembly` callbacks to the Android main thread and add opt-in APIs for background/custom executors.
- Added `pauseUploadsSafely`/`resumeUploadsSafely` helpers and an optional WorkManager integration (`AndroidAssemblyWorkConfig` + `AndroidAssemblyUploadWorker`) to persist resumable uploads in the background.
- Added a runnable Kotlin WorkManager sample (`examples/…/WorkManagerSample.kt`) and matching E2E test to showcase background uploads with the new API surface, including external signature-provider usage.
- Added `AndroidAssemblyListener` to replace the old `AssemblyProgressListener`
- Updated samples, documentation, and tests to use the new asynchronous API
- Added environment-aware Docker tests plus live assembly integration coverage

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
