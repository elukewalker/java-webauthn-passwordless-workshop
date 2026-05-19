# java-webauthn-server 2.9.0 Migration Changes

## Summary

This document tracks all code changes made to migrate from java-webauthn-server 1.2.0 to 2.9.0.

## Dependency Updates

All pom.xml files updated:
- Spring Boot: 2.1.4.RELEASE → 2.7.18
- java-webauthn-server-core: 1.2.0 → 2.9.0
- java-webauthn-server-attestation: 1.2.0 → 2.9.0
- Lombok: (inherited) → 1.18.46 (explicit)
- logback-classic: 1.2.3 → 1.2.13
- azure-webapp-maven-plugin: 1.6.0 → 2.13.0
- **Added**: BouncyCastle 1.70 (for EdDSA support on Java 8)

## Code Changes - Module 3 (3_Registration/complete)

### Config.java
- **Fixed**: Removed `.icon()` usage (lines 143-152)
  - Icon fields removed in WebAuthn Level 2
  - Added warning log if icon env var is set

### WebAuthnServer.java
- **Fixed**: Added `ResidentKeyRequirement` import
- **Fixed**: Removed `.allowUnrequestedExtensions(true)` (line 149)
  - Method removed in v2.x, now always enabled
- **Fixed**: Changed `.requireResidentKey(boolean)` → `.residentKey(ResidentKeyRequirement)` (lines 236, 274)
  - false → ResidentKeyRequirement.DISCOURAGED
  - true → ResidentKeyRequirement.REQUIRED
- **Fixed**: Removed `result.getWarnings()` usage (line 536)
  - Warnings now logged via SLF4J instead of returned
- **Fixed**: Removed attestation framework imports (lines 45-54)
  - Removed: Attestation, AttestationResolver, MetadataObject, MetadataService, etc.
- **Fixed**: Removed TrustResolver and MetadataService initialization (lines 117-127)
  - Attestation now handled internally by RelyingParty
- **Fixed**: Removed `.metadataService()` from RelyingParty builder
  - Using default attestation trust configuration
- **Fixed**: Removed attestation metadata helper methods (readPreviewMetadata, createExtraTrustResolver, createExtraMetadataResolver)
  - No longer needed with v2.x attestation framework
- **Fixed**: Simplified finishU2fRegistration attestation handling (lines 424-445)
  - Removed metadataService.getAttestation() call
  - Set attestationTrusted to false (validated by RelyingParty internally)
  - Removed `.attestationMetadata()` from U2fRegistrationResult builder
- **Fixed**: Updated addRegistration methods to remove attestationMetadata parameter
  - Removed Optional<Attestation> attestationMetadata parameter
  - Removed attestationMetadata from CredentialRegistration builder

### data/CredentialRegistration.java
- **Fixed**: Removed `com.yubico.webauthn.attestation.Attestation` import
- **Fixed**: Removed `Optional<Attestation> attestationMetadata` field
- **Preserved**: All Lombok annotations (@Value, @Builder, @Wither) - CRITICAL for workshop

### data/U2fRegistrationResult.java
- **Fixed**: Removed `com.yubico.webauthn.attestation.Attestation` import
- **Fixed**: Removed `List<String> warnings` field
  - Warnings now logged via SLF4J
- **Fixed**: Removed `Optional<Attestation> attestationMetadata` field
- **Preserved**: All Lombok annotations (@Value, @Builder, @NonNull, @Builder.Default)

## Code Changes - Module 4 (4_Authentication/complete)

**Status**: NEEDS REVIEW
- Module 4 files differ from Module 3
- Similar fixes needed but not yet applied
- Files to check: WebAuthnServer.java, Config.java, data model classes

## Code Changes - Module 2 (2_Credential_Repository/complete)

**Status**: NEEDS REVIEW
- Dependencies updated in pom.xml
- Java code likely needs similar fixes if it uses webauthn-server APIs

## Testing Required

Without Maven/Java installed, the following validations are pending:

1. **Build validation**: `mvn clean compile -B -Dmaven.repo.local=/tmp/m2` for each module
2. **Test execution**: `mvn clean test -B -Dmaven.repo.local=/tmp/m2` for each module  
3. **Lombok verification**: Ensure @Builder, @Value, @Data annotations still work
4. **End-to-end**: Start application and test registration/authentication flows

## Known Issues / TODOs

1. Module 4 (4_Authentication) needs migration fixes applied
2. Module 2 (2_Credential_Repository) needs review
3. README may reference old dependency versions - needs check
4. Build must be tested with `mvn clean` to catch Lombok issues
5. Initial module (initial/) only has dependency updates, no code changes needed (no webauthn usage)

## Breaking Changes Reference

From java-webauthn-server migration guide:

1. ✅ `.icon()` fields removed
2. ✅ `.requireResidentKey(boolean)` → `.residentKey(ResidentKeyRequirement)`  
3. ✅ `.allowUnrequestedExtensions()` removed
4. ✅ `.getWarnings()` removed (use SLF4J)
5. ✅ Attestation framework overhauled (MetadataService → AttestationTrustSource)
6. ✅ `Optional` return types for getUserVerification/getResidentKey (not used in workshop)
7. ✅ Package relocation for UVM classes (not used in workshop)

## Lombok Preservation - CRITICAL

All Lombok annotations MUST be preserved:
- @Value
- @Builder
- @Data
- @AllArgsConstructor
- @RequiredArgsConstructor
- @NonNull
- @Builder.Default
- @Wither

**Previous failure**: Lombok annotations were removed and Maven cached old generated classes, causing build to appear successful when it wasn't. Always use `mvn clean` to prevent this.

## Migration Guide Reference

Official guide: https://developers.yubico.com/java-WebAuthn-Server/Migrating_from_v1.html
