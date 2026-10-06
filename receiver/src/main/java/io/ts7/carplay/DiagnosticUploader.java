package io.ts7.carplay;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

public final class DiagnosticUploader {
    private DiagnosticUploader() {}

    public static String upload(String report) throws IOException {
        URL endpoint = new URL(BuildConfig.UPLOAD_URL);
        if (!"https".equals(endpoint.getProtocol())) throw new IOException("HTTPS_REQUIRED");
        byte[] body = report.getBytes(StandardCharsets.UTF_8);
        if (body.length > 32768) throw new IOException("REPORT_TOO_LARGE");
        HttpsURLConnection connection = (HttpsURLConnection) endpoint.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(body.length);
        try {
            try (java.io.OutputStream stream = connection.getOutputStream()) { stream.write(body); }
            int code = connection.getResponseCode();
            if (code != 200 && code != 201) throw new IOException("UPLOAD_HTTP_" + code);
            byte[] result = new byte[2048];
            int length = 0;
            try (InputStream stream = connection.getInputStream()) {
                int read;
                while (length < result.length && (read = stream.read(result, length, result.length - length)) != -1) length += read;
                if (stream.read() != -1) throw new IOException("RESPONSE_TOO_LARGE");
            }
            org.json.JSONObject response = new org.json.JSONObject(new String(result, 0, length, StandardCharsets.UTF_8));
            String link = response.getString("githubUrl");
            if (!response.getBoolean("success") || !link.matches(
                    "https://github[.]com/nnnc8/ts7-carplay-lite/issues/13#issuecomment-[0-9]+")) {
                throw new IOException("INVALID_RELAY_RESPONSE");
            }
            return link;
        } catch (org.json.JSONException error) { throw new IOException("INVALID_RELAY_RESPONSE"); }
        finally { connection.disconnect(); }
    }
}
