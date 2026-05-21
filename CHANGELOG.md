# Changelog

All notable changes to this project will be documented in this file.

## [0.0.2.0] - 2026-05-21

### Fixed
- Resolved ObjectMapper configuration issue: added Jdk8Module registration to enable proper JSON deserialization of WebAuthn types that use Optional fields, preventing runtime JSON parsing failures
- Removed unused webauthn-server-attestation dependency from all three complete modules (reduces dependencies and eliminates confusion about attestation handling)
- Fixed Java version conflicts by aligning java.version property to 17 across all modules, matching maven.compiler.release configuration
- Updated Guava from 31.1-jre to 32.1.3-jre for consistency with README documentation
- Updated Azure linuxRuntime from jre8 to java17-java17 to match actual Java version requirements
- Added missing InputStream import in U2fVerifier class to resolve compilation errors

### Changed
- Removed AI-generated code comments across all Java files for cleaner, more professional code appearance following code review feedback
- Improved code quality by using proper imports instead of fully qualified class names
- Updated README examples to use v2.x API (ResidentKeyRequirement instead of deprecated requireResidentKey)
- Added missing Guava dependency to 2_Credential_Repository README instructions

## [0.0.1.0] - 2026-05-19

### Changed
You can now use this workshop with the latest java-webauthn-server 2.9.0 library. This upgrade brings compatibility with modern WebAuthn implementations and ensures the workshop aligns with current Yubico standards.

- Upgraded java-webauthn-server from 1.2.0 to 2.9.0 across all workshop modules
- Updated Spring Boot from 2.1.4.RELEASE to 2.7.18 (latest Java 8 compatible version)
- Updated supporting dependencies: Lombok 1.18.46, logback-classic 1.2.13, azure-webapp-maven-plugin 2.13.0
- Migrated deprecated APIs to java-webauthn-server 2.x standards:
  - Removed `.icon()` usage (field removed in WebAuthn Level 2)
  - Changed `.requireResidentKey(boolean)` to `.residentKey(ResidentKeyRequirement)`
  - Removed `.allowUnrequestedExtensions()` (now always enabled)
  - Removed manual attestation framework initialization (now handled internally by RelyingParty)

### Added
- BouncyCastle 1.70 dependency for EdDSA cryptographic support on Java 8
- Comprehensive migration documentation in MIGRATION_CHANGES.md - review this if you're upgrading from v1.x
- Code comments explaining v2.x API changes for educational purposes

### Fixed
- Completed v2.x API migration for workshop modules 2 (Credential Repository) and 4 (Authentication)
- Preserved all Lombok annotations critical for workshop build process
- Java 25 compatibility: Added explicit maven-compiler-plugin configuration with Lombok annotation processor paths to ensure Lombok `@Value`/`@Builder` annotations work correctly on Java 25 (stricter annotation processing requirements)
- Cross-JDK reproducible builds: Added `maven.compiler.release=17` property to all modules, ensuring consistent compilation regardless of installed JDK version (8, 17, 21, 25, etc.)
- Fixed Lombok `@Builder` + `@NonNull` final fields compatibility issue in U2fRegistrationResult.java by adding `@Builder(toBuilder=true)` and removing redundant field modifiers
