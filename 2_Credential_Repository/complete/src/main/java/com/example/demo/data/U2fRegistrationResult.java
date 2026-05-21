package com.example.demo.data;

import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class U2fRegistrationResult {

    @NonNull
    PublicKeyCredentialDescriptor keyId;

    boolean attestationTrusted;

    @NonNull
    ByteArray publicKeyCose;

}
