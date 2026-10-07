// SPDX-License-Identifier: GPL-3.0-only
package io.ts7.carplay.auth;

import java.util.Arrays;

/** External, provenance-gated authentication. No provider stores or exports a private key. */
public interface AuthenticationProvider extends AutoCloseable {
    boolean isAvailable();
    ProviderInfo getInfo();
    AuthResult authenticate(AuthRequest request);
    void close();

    final class ProviderInfo {
        public enum Type { UNAVAILABLE, HARDWARE_MFI, REMOTE_AUTHORIZED }
        public enum Source { UNAVAILABLE, MFI_AND_VENDOR_AGREEMENT, AUTHORIZED_SERVICE_AGREEMENT }
        public enum IdentifierType { NONE, I2C_BUS, USB_INTERFACE, UART_INTERFACE, SERVICE }
        public enum Provisioning { UNAVAILABLE, UNVERIFIED, AUTHORIZED }
        public final Type type;
        public final Source authorizedSource;
        public final IdentifierType hardwareIdentifierType;
        public final Provisioning provisioning;
        public final String implementation;

        public ProviderInfo(Type type, String implementation, Source source,
                IdentifierType identifierType, Provisioning provisioning) {
            if (type == null || source == null || identifierType == null || provisioning == null
                    || implementation == null || !implementation.matches("[A-Za-z0-9_.]{1,128}"))
                throw new IllegalArgumentException("INVALID_PROVIDER_INFO");
            this.type = type;
            this.implementation = implementation;
            this.authorizedSource = source;
            this.hardwareIdentifierType = identifierType;
            this.provisioning = provisioning;
        }

        /** Recorded authorization is a reviewed provisioning prerequisite, not inferred from a USB ID. */
        public boolean isAuthorized() {
            if (provisioning != Provisioning.AUTHORIZED) return false;
            return type == Type.HARDWARE_MFI && authorizedSource == Source.MFI_AND_VENDOR_AGREEMENT
                && hardwareIdentifierType != IdentifierType.NONE && hardwareIdentifierType != IdentifierType.SERVICE
                || type == Type.REMOTE_AUTHORIZED && authorizedSource == Source.AUTHORIZED_SERVICE_AGREEMENT
                && hardwareIdentifierType == IdentifierType.SERVICE;
        }

        @Override public String toString() {
            return type.name() + "/" + implementation + "/" + authorizedSource.name() + "/"
                + hardwareIdentifierType.name() + "/" + provisioning.name();
        }
    }

    final class AuthRequest implements AutoCloseable {
        public enum Operation { READ_ACCESSORY_CERTIFICATE, SIGN_CHALLENGE }
        public final Operation operation;
        public final long deadlineNanos;
        private final byte[] challenge;
        private boolean closed;

        public AuthRequest(Operation operation, byte[] challenge, long deadlineNanos) {
            if (operation == null || challenge == null || challenge.length > 128
                    || operation == Operation.SIGN_CHALLENGE && challenge.length == 0
                    || operation == Operation.READ_ACCESSORY_CERTIFICATE && challenge.length != 0)
                throw new IllegalArgumentException("INVALID_AUTH_REQUEST");
            this.operation = operation;
            this.challenge = challenge.clone();
            this.deadlineNanos = deadlineNanos;
        }

        public synchronized boolean isExpired() {
            return closed || deadlineNanos - System.nanoTime() <= 0;
        }

        /** Protocol-only access; never pass the returned bytes to diagnostics/logging. */
        public synchronized byte[] copyChallenge() {
            if (closed) throw new IllegalStateException("AUTH_REQUEST_CLOSED");
            return challenge.clone();
        }

        @Override public synchronized void close() { Arrays.fill(challenge, (byte) 0); closed = true; }
        @Override public String toString() { return "AuthRequest/" + operation.name(); }
    }

    final class AuthResult implements AutoCloseable {
        public enum Status { SUCCESS, UNAVAILABLE, UNAUTHORIZED, FAILED, CANCELLED }
        public final Status status;
        private final byte[] protocolBytes;
        private boolean closed;

        private AuthResult(Status status, byte[] bytes) {
            if (status == null || bytes == null || bytes.length > 65525
                    || status == Status.SUCCESS && bytes.length == 0
                    || status != Status.SUCCESS && bytes.length != 0)
                throw new IllegalArgumentException("INVALID_AUTH_RESULT");
            this.status = status;
            this.protocolBytes = bytes.clone();
        }

        public static AuthResult unavailable() { return new AuthResult(Status.UNAVAILABLE, new byte[0]); }
        public static AuthResult rejected() { return new AuthResult(Status.UNAUTHORIZED, new byte[0]); }
        public static AuthResult cancelled() { return new AuthResult(Status.CANCELLED, new byte[0]); }
        /** Only an explicitly provisioned provider may return protocol bytes; nothing is bundled. */
        public static AuthResult success(byte[] bytes) { return new AuthResult(Status.SUCCESS, bytes); }
        public synchronized byte[] copyProtocolBytes() {
            if (closed) throw new IllegalStateException("AUTH_RESULT_CLOSED");
            return protocolBytes.clone();
        }
        @Override public synchronized void close() { Arrays.fill(protocolBytes, (byte) 0); closed = true; }
        @Override public String toString() { return "AuthResult/" + status.name(); }
    }
}
