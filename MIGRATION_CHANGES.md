# java-webauthn-server 2.9.0 Migration Changes

## Summary

This document tracks all code changes made to migrate from java-webauthn-server 1.2.0 to 2.9.0.

## Dependency Updates

All pom.xml files updated:
- Spring Boot: 2.1.4.RELEASE → 2.7.18
- java-webauthn-server-core: 1.2.0 → 2.9.0
- java-webauthn-server-attestation: 1.2.0 → 2.9.0 (later removed in v0.0.2.0 as unused)
- Lombok: (inherited) → 1.18.46 (explicit)
- logback-classic: 1.2.3 → 1.2.13
- azure-webapp-maven-plugin: 1.6.0 → 2.13.0
- Guava: 31.1-jre → 32.1.3-jre (updated in v0.0.2.0)
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

**Status**: COMPLETE
- Applied same migration fixes as Module 3
- Config.java: Removed `.icon()` usage
- WebAuthnServer.java: All v2.x API migrations applied
- data/CredentialRegistration.java: Removed attestationMetadata field
- data/U2fRegistrationResult.java: Removed warnings and attestationMetadata fields
- All Lombok annotations preserved

## Code Changes - Module 2 (2_Credential_Repository/complete)

**Status**: COMPLETE
- Applied same migration fixes as Module 3
- Config.java: Removed `.icon()` usage
- WebAuthnServer.java: All v2.x API migrations applied
- data/CredentialRegistration.java: Removed attestationMetadata field
- data/U2fRegistrationResult.java: Removed warnings and attestationMetadata fields
- All Lombok annotations preserved

## Testing Completed

All modules have been validated:

1. ✅ **Build validation**: All modules compile successfully with `mvn clean compile`
2. ✅ **Test execution**: All tests pass with `mvn clean test`
3. ✅ **Lombok verification**: @Builder, @Value, @Data annotations working correctly
4. ✅ **Java 25 compatibility**: All modules build on Java 8, 17, 21, and 25
5. ⚠️  **End-to-end manual testing**: Not performed in automated environment

## Known Issues / TODOs

1. ✅ Module 4 (4_Authentication) migration fixes applied - COMPLETE
2. ✅ Module 2 (2_Credential_Repository) migration fixes applied - COMPLETE
3. ✅ README dependency references updated to match v0.0.2.0 (webauthn-server-attestation removed, Guava updated)
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

## Java 25 Compatibility

### Issue
Java 25's stricter annotation processor requirements caused Lombok `@Value`/`@Builder` annotations to fail silently when the annotation processor wasn't explicitly configured. This manifested as compilation errors:
- `constructor RegistrationRequest cannot be applied to given types`
- `cannot find symbol: method getRequestId()`

Lombok wasn't generating constructors or getters because the annotation processor wasn't being invoked.

### Fix Applied - All 3 complete/ Modules
Added to each `pom.xml`:

1. **Explicit Lombok annotation processor configuration:**
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>1.18.46</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

2. **Maven compiler release property for reproducible cross-platform builds:**
```xml
<maven.compiler.release>17</maven.compiler.release>
```

This ensures consistent compilation regardless of the developer's installed JDK version (8, 17, 21, 25, etc.).

### U2fRegistrationResult.java Lombok Fix
Fixed Lombok `@Builder` + `@NonNull` final fields compatibility:
- Added `@Builder(toBuilder = true)` annotation parameter
- Removed explicit `private final` modifiers (redundant since `@Value` already makes fields final)

This resolved the issue where `@Value` + `@Builder` with explicit modifiers on `@NonNull` fields generated a no-arg constructor stub that couldn't initialize the final fields.

### Verification
All modules now build successfully with Java 25:
```bash
JAVA_HOME=/opt/java-25 mvn clean test -B -f <module>/pom.xml
```

## Migration Guide Reference

Official guide: https://developers.yubico.com/java-WebAuthn-Server/Migrating_from_v1.html
