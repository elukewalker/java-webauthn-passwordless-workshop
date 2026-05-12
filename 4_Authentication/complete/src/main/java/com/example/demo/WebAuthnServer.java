// Copyright (c) 2018, Yubico AB
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this
//    list of conditions and the following disclaimer.
//
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
// FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
// DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
// OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.yubico.util.Either;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.U2fVerifier;
import com.yubico.webauthn.data.AttestationConveyancePreference;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import com.yubico.webauthn.extension.appid.AppId;
import com.yubico.webauthn.extension.appid.InvalidAppIdException;
import com.example.demo.data.AssertionRequestWrapper;
import com.example.demo.data.AssertionResponse;
import com.example.demo.data.CredentialRegistration;
import com.example.demo.data.RegistrationRequest;
import com.example.demo.data.RegistrationResponse;
import com.example.demo.data.U2fRegistrationResponse;
import com.example.demo.data.U2fRegistrationResult;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.NonNull;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WebAuthnServer {
    private static final Logger logger = LoggerFactory.getLogger(WebAuthnServer.class);
    private static final SecureRandom random = new SecureRandom();

    private final Cache<ByteArray, AssertionRequestWrapper> assertRequestStorage;
    private final Cache<ByteArray, RegistrationRequest> registerRequestStorage;
    private final RegistrationStorage userStorage;
    private final Cache<AssertionRequestWrapper, AuthenticatedAction> authenticatedActions = newCache();

    private final Clock clock = Clock.systemDefaultZone();
    private final ObjectMapper jsonMapper = new ObjectMapper().registerModule(new Jdk8Module());

    private final RelyingParty rp;

    public WebAuthnServer() throws InvalidAppIdException, CertificateException {
        this(new InMemoryRegistrationStorage(), newCache(), newCache(), Config.getRpIdentity(), Config.getOrigins(), Config.getAppId());
    }

    public WebAuthnServer(RegistrationStorage userStorage, Cache<ByteArray, RegistrationRequest> registerRequestStorage, Cache<ByteArray, AssertionRequestWrapper> assertRequestStorage, RelyingPartyIdentity rpIdentity, Set<String> origins, Optional<AppId> appId) throws InvalidAppIdException, CertificateException {
        this.userStorage = userStorage;
        this.registerRequestStorage = registerRequestStorage;
        this.assertRequestStorage = assertRequestStorage;

        rp = RelyingParty.builder()
            .identity(rpIdentity)
            .credentialRepository(this.userStorage)
            .origins(origins)
            .attestationConveyancePreference(Optional.of(AttestationConveyancePreference.DIRECT))
            .allowUntrustedAttestation(true)
            .validateSignatureCounter(true)
            .appId(appId)
            .build();
    }

    private static ByteArray generateRandom(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return new ByteArray(bytes);
    }

    /**
     * Convert a U2F raw ECDSA public key to COSE format.
     * U2F uses uncompressed P-256 keys: 0x04 || X || Y (65 bytes)
     * COSE uses CBOR encoding with key type, algorithm, curve, and coordinates.
     */
    private static ByteArray rawEcdaKeyToCose(ByteArray publicKey) {
        byte[] keyBytes = publicKey.getBytes();
        if (keyBytes.length != 65 || keyBytes[0] != 0x04) {
            throw new IllegalArgumentException("Invalid U2F public key format");
        }

        // Extract X and Y coordinates (32 bytes each)
        byte[] x = new byte[32];
        byte[] y = new byte[32];
        System.arraycopy(keyBytes, 1, x, 0, 32);
        System.arraycopy(keyBytes, 33, y, 0, 32);

        // Build COSE key structure manually
        // COSE key format for ES256:
        // { 1: 2, 3: -7, -1: 1, -2: x, -3: y }
        // where: 1=kty (2=EC2), 3=alg (-7=ES256), -1=crv (1=P-256), -2=x coord, -3=y coord

        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            // CBOR map with 5 entries
            baos.write(0xA5);

            // 1 (kty): 2 (EC2)
            baos.write(0x01);
            baos.write(0x02);

            // 3 (alg): -7 (ES256)
            baos.write(0x03);
            baos.write(0x26); // negative integer -7

            // -1 (crv): 1 (P-256)
            baos.write(0x20); // negative integer -1
            baos.write(0x01);

            // -2 (x): x coordinate as byte string
            baos.write(0x21); // negative integer -2
            baos.write(0x58); // byte string, 1-byte length
            baos.write(0x20); // length 32
            baos.write(x);

            // -3 (y): y coordinate as byte string
            baos.write(0x22); // negative integer -3
            baos.write(0x58); // byte string, 1-byte length
            baos.write(0x20); // length 32
            baos.write(y);

            return new ByteArray(baos.toByteArray());
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to encode COSE key", e);
        }
    }

    private static <K, V> Cache<K, V> newCache() {
        return CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();
    }

    public Either<String, RegistrationRequest> startRegistration(
        @NonNull String username,
        @NonNull String displayName,
        Optional<String> credentialNickname,
        boolean requireResidentKey
    ) {
        logger.trace("startRegistration username: {}, credentialNickname: {}", username, credentialNickname);

        if (userStorage.getRegistrationsByUsername(username).isEmpty()) {
            RegistrationRequest request = new RegistrationRequest(
                username,
                credentialNickname,
                generateRandom(32),
                rp.startRegistration(
                    StartRegistrationOptions.builder()
                        .user(UserIdentity.builder()
                            .name(username)
                            .displayName(displayName)
                            .id(generateRandom(32))
                            .build()
                        )
                        .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                            .residentKey(requireResidentKey ? com.yubico.webauthn.data.ResidentKeyRequirement.REQUIRED : com.yubico.webauthn.data.ResidentKeyRequirement.DISCOURAGED)
                            .build()
                        )
                        .build()
                )
            );
            registerRequestStorage.put(request.getRequestId(), request);
            return Either.right(request);
        } else {
            return Either.left("The username \"" + username + "\" is already registered.");
        }
    }

    public <T> Either<List<String>, AssertionRequestWrapper> startAddCredential(
        @NonNull String username,
        Optional<String> credentialNickname,
        boolean requireResidentKey,
        Function<RegistrationRequest, Either<List<String>, T>> whenAuthenticated
    ) {
        logger.trace("startAddCredential username: {}, credentialNickname: {}, requireResidentKey: {}", username, credentialNickname, requireResidentKey);

        if (username == null || username.isEmpty()) {
            return Either.left(Collections.singletonList("username must not be empty."));
        }

        Collection<CredentialRegistration> registrations = userStorage.getRegistrationsByUsername(username);

        if (registrations.isEmpty()) {
            return Either.left(Collections.singletonList("The username \"" + username + "\" is not registered."));
        } else {
            final UserIdentity existingUser = registrations.stream().findAny().get().getUserIdentity();

            AuthenticatedAction<T> action = (SuccessfulAuthenticationResult result) -> {
                RegistrationRequest request = new RegistrationRequest(
                    username,
                    credentialNickname,
                    generateRandom(32),
                    rp.startRegistration(
                        StartRegistrationOptions.builder()
                            .user(existingUser)
                            .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                                .residentKey(requireResidentKey ? com.yubico.webauthn.data.ResidentKeyRequirement.REQUIRED : com.yubico.webauthn.data.ResidentKeyRequirement.DISCOURAGED)
                                .build()
                            )
                            .build()
                    )
                );
                registerRequestStorage.put(request.getRequestId(), request);

                return whenAuthenticated.apply(request);
            };

            return startAuthenticatedAction(Optional.of(username), action);
        }
    }

    @Value
    public static class SuccessfulRegistrationResult {
        final boolean success = true;
        RegistrationRequest request;
        RegistrationResponse response;
        CredentialRegistration registration;
        boolean attestationTrusted;
        Optional<AttestationCertInfo> attestationCert;

        public SuccessfulRegistrationResult(RegistrationRequest request, RegistrationResponse response, CredentialRegistration registration, boolean attestationTrusted) {
            this.request = request;
            this.response = response;
            this.registration = registration;
            this.attestationTrusted = attestationTrusted;
            attestationCert = Optional.ofNullable(
                response.getCredential().getResponse().getAttestation().getAttestationStatement().get("x5c")
            ).map(certs -> certs.get(0))
            .flatMap((JsonNode certDer) -> {
                try {
                    return Optional.of(new ByteArray(certDer.binaryValue()));
                } catch (IOException e) {
                    logger.error("Failed to get binary value from x5c element: {}", certDer, e);
                    return Optional.empty();
                }
            })
            .map(AttestationCertInfo::new);
        }
    }

    @Value
    public class SuccessfulU2fRegistrationResult {
        final boolean success = true;
        final RegistrationRequest request;
        final U2fRegistrationResponse response;
        final CredentialRegistration registration;
        boolean attestationTrusted;
        Optional<AttestationCertInfo> attestationCert;
    }

    @Value
    public static class AttestationCertInfo {
        final ByteArray der;
        final String text;
        public AttestationCertInfo(ByteArray certDer) {
            der = certDer;
            X509Certificate cert = null;
            try {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certDer.getBytes()));
            } catch (CertificateException e) {
                logger.error("Failed to parse attestation certificate");
            }
            if (cert == null) {
                text = null;
            } else {
                text = cert.toString();
            }
        }
    }

    public Either<List<String>, SuccessfulRegistrationResult> finishRegistration(String responseJson) {
        logger.trace("finishRegistration responseJson: {}", responseJson);
        RegistrationResponse response = null;
        try {
            response = jsonMapper.readValue(responseJson, RegistrationResponse.class);
        } catch (IOException e) {
            logger.error("JSON error in finishRegistration; responseJson: {}", responseJson, e);
            return Either.left(Arrays.asList("Registration failed!", "Failed to decode response object.", e.getMessage()));
        }

        RegistrationRequest request = registerRequestStorage.getIfPresent(response.getRequestId());
        registerRequestStorage.invalidate(response.getRequestId());

        if (request == null) {
            logger.debug("fail finishRegistration responseJson: {}", responseJson);
            return Either.left(Arrays.asList("Registration failed!", "No such registration in progress."));
        } else {
            try {
                com.yubico.webauthn.RegistrationResult registration = rp.finishRegistration(
                    FinishRegistrationOptions.builder()
                        .request(request.getPublicKeyCredentialCreationOptions())
                        .response(response.getCredential())
                        .build()
                );

                return Either.right(
                    new SuccessfulRegistrationResult(
                        request,
                        response,
                        addRegistration(
                            request.getPublicKeyCredentialCreationOptions().getUser(),
                            request.getCredentialNickname(),
                            response,
                            registration
                        ),
                        registration.isAttestationTrusted()
                    )
                );
            } catch (RegistrationFailedException e) {
                logger.debug("fail finishRegistration responseJson: {}", responseJson, e);
                return Either.left(Arrays.asList("Registration failed!", e.getMessage()));
            } catch (Exception e) {
                logger.error("fail finishRegistration responseJson: {}", responseJson, e);
                return Either.left(Arrays.asList("Registration failed unexpectedly; this is likely a bug.", e.getMessage()));
            }
        }
    }

    public Either<List<String>, SuccessfulU2fRegistrationResult> finishU2fRegistration(String responseJson) {
        logger.trace("finishU2fRegistration responseJson: {}", responseJson);
        U2fRegistrationResponse response = null;
        try {
            response = jsonMapper.readValue(responseJson, U2fRegistrationResponse.class);
        } catch (IOException e) {
            logger.error("JSON error in finishU2fRegistration; responseJson: {}", responseJson, e);
            return Either.left(Arrays.asList("Registration failed!", "Failed to decode response object.", e.getMessage()));
        }

        RegistrationRequest request = registerRequestStorage.getIfPresent(response.getRequestId());
        registerRequestStorage.invalidate(response.getRequestId());

        if (request == null) {
            logger.debug("fail finishU2fRegistration responseJson: {}", responseJson);
            return Either.left(Arrays.asList("Registration failed!", "No such registration in progress."));
        } else {

            try {
                if (!U2fVerifier.verify(rp.getAppId().get(), request, response)) {
                    throw new RuntimeException("Failed to verify signature.");
                }
            } catch (Exception e) {
                logger.debug("Failed to verify U2F signature.", e);
                return Either.left(Arrays.asList("Failed to verify signature.", e.getMessage()));
            }

            final U2fRegistrationResult result = U2fRegistrationResult.builder()
                .keyId(PublicKeyCredentialDescriptor.builder().id(response.getCredential().getU2fResponse().getKeyHandle()).build())
                .attestationTrusted(false)
                .publicKeyCose(rawEcdaKeyToCose(response.getCredential().getU2fResponse().getPublicKey()))
                .attestationMetadata(Optional.empty())
                .build();

            return Either.right(
                new SuccessfulU2fRegistrationResult(
                    request,
                    response,
                    addRegistration(
                        request.getPublicKeyCredentialCreationOptions().getUser(),
                        request.getCredentialNickname(),
                        0,
                        result
                    ),
                    result.isAttestationTrusted(),
                    Optional.of(new AttestationCertInfo(response.getCredential().getU2fResponse().getAttestationCertAndSignature()))
                )
            );
        }
    }

    public Either<List<String>, AssertionRequestWrapper> startAuthentication(Optional<String> username) {
        logger.trace("startAuthentication username: {}", username);

        if (username.isPresent() && userStorage.getRegistrationsByUsername(username.get()).isEmpty()) {
            return Either.left(Collections.singletonList("The username \"" + username.get() + "\" is not registered."));
        } else {
            AssertionRequestWrapper request = new AssertionRequestWrapper(
                generateRandom(32),
                rp.startAssertion(
                    StartAssertionOptions.builder()
                        .username(username)
                        .build()
                )
            );

            assertRequestStorage.put(request.getRequestId(), request);

            return Either.right(request);
        }
    }

    @Value
    public static class SuccessfulAuthenticationResult {
        final boolean success = true;
        AssertionRequestWrapper request;
        AssertionResponse response;
        Collection<CredentialRegistration> registrations;
    }

    public Either<List<String>, SuccessfulAuthenticationResult> finishAuthentication(String responseJson) {
        logger.trace("finishAuthentication responseJson: {}", responseJson);

        final AssertionResponse response;
        try {
            response = jsonMapper.readValue(responseJson, AssertionResponse.class);
        } catch (IOException e) {
            logger.debug("Failed to decode response object", e);
            return Either.left(Arrays.asList("Assertion failed!", "Failed to decode response object.", e.getMessage()));
        }

        AssertionRequestWrapper request = assertRequestStorage.getIfPresent(response.getRequestId());
        assertRequestStorage.invalidate(response.getRequestId());

        if (request == null) {
            return Either.left(Arrays.asList("Assertion failed!", "No such assertion in progress."));
        } else {
            try {
                AssertionResult result = rp.finishAssertion(
                    FinishAssertionOptions.builder()
                        .request(request.getRequest())
                        .response(response.getCredential())
                        .build()
                );

                if (result.isSuccess()) {
                    try {
                        userStorage.updateSignatureCount(result);
                    } catch (Exception e) {
                        logger.error(
                            "Failed to update signature count for user \"{}\", credential \"{}\"",
                            result.getUsername(),
                            response.getCredential().getId(),
                            e
                        );
                    }

                    return Either.right(
                        new SuccessfulAuthenticationResult(
                            request,
                            response,
                            userStorage.getRegistrationsByUsername(result.getUsername())
                        )
                    );
                } else {
                    return Either.left(Collections.singletonList("Assertion failed: Invalid assertion."));
                }
            } catch (AssertionFailedException e) {
                logger.debug("Assertion failed", e);
                return Either.left(Arrays.asList("Assertion failed!", e.getMessage()));
            } catch (Exception e) {
                logger.error("Assertion failed", e);
                return Either.left(Arrays.asList("Assertion failed unexpectedly; this is likely a bug.", e.getMessage()));
            }
        }
    }

    public Either<List<String>, AssertionRequestWrapper> startAuthenticatedAction(Optional<String> username, AuthenticatedAction<?> action) {
        return startAuthentication(username)
            .map(request -> {
                synchronized (authenticatedActions) {
                    authenticatedActions.put(request, action);
                }
                return request;
            });
    }

    public Either<List<String>, ?> finishAuthenticatedAction(String responseJson) {
        return finishAuthentication(responseJson)
            .flatMap(result -> {
                AuthenticatedAction<?> action = authenticatedActions.getIfPresent(result.request);
                authenticatedActions.invalidate(result.request);
                if (action == null) {
                    return Either.left(Collections.singletonList(
                        "No action was associated with assertion request ID: " + result.getRequest().getRequestId()
                    ));
                } else {
                    return action.apply(result);
                }
            });
    }

    public <T> Either<List<String>, AssertionRequestWrapper> deregisterCredential(String username, ByteArray credentialId, Function<CredentialRegistration, T> resultMapper) {
        logger.trace("deregisterCredential username: {}, credentialId: {}", username, credentialId);

        if (username == null || username.isEmpty()) {
            return Either.left(Collections.singletonList("Username must not be empty."));
        }

        if (credentialId == null || credentialId.getBytes().length == 0) {
            return Either.left(Collections.singletonList("Credential ID must not be empty."));
        }

        AuthenticatedAction<T> action = (SuccessfulAuthenticationResult result) -> {
            Optional<CredentialRegistration> credReg = userStorage.getRegistrationByUsernameAndCredentialId(username, credentialId);

            if (credReg.isPresent()) {
                userStorage.removeRegistrationByUsername(username, credReg.get());
                return Either.right(resultMapper.apply(credReg.get()));
            } else {
                return Either.left(Collections.singletonList("Credential ID not registered:" + credentialId));
            }
        };

        return startAuthenticatedAction(Optional.of(username), action);
    }

    public <T> Either<List<String>, T> deleteAccount(String username, Supplier<T> onSuccess) {
        logger.trace("deleteAccount username: {}", username);

        if (username == null || username.isEmpty()) {
            return Either.left(Collections.singletonList("Username must not be empty."));
        }

        boolean removed = userStorage.removeAllRegistrations(username);

        if (removed) {
            return Either.right(onSuccess.get());
        } else {
            return Either.left(Collections.singletonList("Username not registered:" + username));
        }
    }

    private CredentialRegistration addRegistration(
        UserIdentity userIdentity,
        Optional<String> nickname,
        RegistrationResponse response,
        RegistrationResult result
    ) {
        return addRegistration(
            userIdentity,
            nickname,
            response.getCredential().getResponse().getAttestation().getAuthenticatorData().getSignatureCounter(),
            RegisteredCredential.builder()
                .credentialId(result.getKeyId().getId())
                .userHandle(userIdentity.getId())
                .publicKeyCose(result.getPublicKeyCose())
                .signatureCount(response.getCredential().getResponse().getParsedAuthenticatorData().getSignatureCounter())
                .build()
        );
    }

    private CredentialRegistration addRegistration(
        UserIdentity userIdentity,
        Optional<String> nickname,
        long signatureCount,
        U2fRegistrationResult result
    ) {
        return addRegistration(
            userIdentity,
            nickname,
            signatureCount,
            RegisteredCredential.builder()
                .credentialId(result.getKeyId().getId())
                .userHandle(userIdentity.getId())
                .publicKeyCose(result.getPublicKeyCose())
                .signatureCount(signatureCount)
                .build()
        );
    }

    private CredentialRegistration addRegistration(
        UserIdentity userIdentity,
        Optional<String> nickname,
        long signatureCount,
        RegisteredCredential credential
    ) {
        CredentialRegistration reg = CredentialRegistration.builder()
            .userIdentity(userIdentity)
            .credentialNickname(nickname)
            .registrationTime(clock.instant())
            .credential(credential)
            .signatureCount(signatureCount)
            .attestationMetadata(Optional.empty())
            .build();

        logger.debug(
            "Adding registration: user: {}, nickname: {}, credential: {}",
            userIdentity,
            nickname,
            credential
        );
        userStorage.addRegistrationByUsername(userIdentity.getName(), reg);
        return reg;
    }

    public Collection<CredentialRegistration> getRegistrationsByUsername(String username) {
        return this.userStorage.getRegistrationsByUsername(username);
    }

}
