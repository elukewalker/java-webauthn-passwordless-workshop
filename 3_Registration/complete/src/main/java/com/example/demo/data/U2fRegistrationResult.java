package com.example.demo.data;

// Attestation import removed in v2.x - attestation validation now internal to RelyingParty
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class U2fRegistrationResult {

    @NonNull
    private final PublicKeyCredentialDescriptor keyId;

    private final boolean attestationTrusted;

    @NonNull
    private final ByteArray publicKeyCose;

    // warnings field removed in v2.x - warnings now logged via SLF4J
    // attestationMetadata field removed in v2.x migration
}
