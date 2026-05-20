package com.example.demo.data;

import com.yubico.webauthn.data.AttestationType;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class RegistrationResult {

    @NonNull
    private final PublicKeyCredentialDescriptor keyId;

    private final boolean attestationTrusted;

    @NonNull
    private final AttestationType attestationType;

    @NonNull
    private final ByteArray publicKeyCose;

    // warnings and attestationMetadata removed in java-webauthn-server 2.x
    // Warnings are now logged via SLF4J instead of returned
    // Attestation metadata handling moved to internal RelyingParty implementation

    public static RegistrationResult fromLibraryType(com.yubico.webauthn.RegistrationResult result) {
        return builder()
            .keyId(result.getKeyId())
            .attestationTrusted(result.isAttestationTrusted())
            .attestationType(result.getAttestationType())
            .publicKeyCose(result.getPublicKeyCose())
            .build();
    }

}
