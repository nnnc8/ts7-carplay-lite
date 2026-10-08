package io.ts7.carplay;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import io.ts7.carplay.auth.AuthenticationProvider;
import io.ts7.carplay.core.ExperimentalDiPlayAuthenticationProvider;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERSequence;
import org.json.JSONObject;

/** CI-only generated identity proves Android cryptography, not iPhone trust or a CarPlay session. */
public final class AuthenticationInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        MainActivity activity = null;
        ExperimentalDiPlayAuthenticationProvider provider = new ExperimentalDiPlayAuthenticationProvider();
        try {
            activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            MainActivity app = activity;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while ("NOT_INITIALIZED".equals(app.coreStatusForTest()) && System.nanoTime() < deadline) Thread.sleep(50);
            require("DIPLAY_CORE_READY_EXPERIMENTAL".equals(app.coreStatusForTest()));
            require(app.coreAuthForTest() && !app.sessionForTest().authenticated());
            require(app.rendererForTest() == null && "IDLE".equals(app.modeForTest()));
            AuthenticationAssets.initialize(getTargetContext().getAssets(), provider);
            require(provider.isAvailable() && !provider.getInfo().isAuthorized());
            byte[] certificate;
            try (AuthenticationProvider.AuthRequest request = new AuthenticationProvider.AuthRequest(
                    AuthenticationProvider.AuthRequest.Operation.READ_ACCESSORY_CERTIFICATE, new byte[0],
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(3));
                 AuthenticationProvider.AuthResult response = provider.authenticate(request)) {
                require(response.status == AuthenticationProvider.AuthResult.Status.SUCCESS);
                certificate = response.copyProtocolBytes();
            }
            java.security.PublicKey publicKey;
            try {
                publicKey = CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(certificate)).getPublicKey();
            } finally { Arrays.fill(certificate, (byte) 0); }
            for (int index = 0; index < 8; index++) {
                byte[] challenge = new byte[32];
                new SecureRandom().nextBytes(challenge);
                try (AuthenticationProvider.AuthRequest request = new AuthenticationProvider.AuthRequest(
                        AuthenticationProvider.AuthRequest.Operation.SIGN_CHALLENGE, challenge,
                        System.nanoTime() + TimeUnit.SECONDS.toNanos(3));
                     AuthenticationProvider.AuthResult response = provider.authenticate(request)) {
                    require(response.status == AuthenticationProvider.AuthResult.Status.SUCCESS);
                    byte[] raw = response.copyProtocolBytes();
                    try {
                        require(raw.length == 64);
                        byte[] der = new DERSequence(new ASN1Integer[] {
                            new ASN1Integer(new BigInteger(1, Arrays.copyOfRange(raw, 0, 32))),
                            new ASN1Integer(new BigInteger(1, Arrays.copyOfRange(raw, 32, 64)))
                        }).getEncoded();
                        Signature verifier = Signature.getInstance("NONEwithECDSA");
                        verifier.initVerify(publicKey); verifier.update(challenge);
                        require(verifier.verify(der));
                        challenge[0] ^= 1;
                        verifier.initVerify(publicKey); verifier.update(challenge);
                        require(!verifier.verify(der));
                        Arrays.fill(der, (byte) 0);
                    } finally { Arrays.fill(raw, (byte) 0); }
                } finally { Arrays.fill(challenge, (byte) 0); }
            }
            JSONObject report = new JSONObject(app.reportForTest());
            require("EXPERIMENTAL_IDENTITY_AVAILABLE".equals(report.getString("authentication")));
            require("IDLE".equals(report.getString("mode")) && !app.sessionForTest().authenticated());
            Bundle evidence = new Bundle();
            evidence.putString("authenticationReport", report.toString());
            sendStatus(1, evidence);
            result.putString("result", "PASS: Android 8.1 generated identity load, P-256 challenge signatures, mutation rejection, experimental NOT authorized, no phone/session/frame claim");
            runOnMainSync(app::finish);
            finish(Activity.RESULT_OK, result);
        } catch (Throwable ignored) {
            result.putString("result", "FAIL: API27_EXPERIMENTAL_AUTH_TEST_FAILED");
            if (activity != null) { MainActivity app = activity; runOnMainSync(app::finish); }
            finish(Activity.RESULT_CANCELED, result);
        } finally { provider.close(); }
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("API27_EXPERIMENTAL_AUTH_TEST_FAILED");
    }
}
