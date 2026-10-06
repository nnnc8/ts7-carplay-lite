package io.ts7diag.tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/** Performs only explicit, user-triggered HTTPS upload work off the UI thread. */
public final class NetworkUploader {
    private static final int CONNECT_TIMEOUT_MS = 9000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final int MAX_RESPONSE_BYTES = 16384;

    private NetworkUploader() {
    }

    public static UploadResult upload(String endpoint, String publicJson) {
        if (endpoint == null || endpoint.trim().length() == 0) {
            return UploadResult.failure("Upload relay is not configured.");
        }
        if (!endpoint.startsWith("https://")) {
            return UploadResult.failure("Upload relay must use HTTPS.");
        }
        HttpURLConnection connection = null;
        try {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");

            byte[] body = publicJson.getBytes(Charset.forName("UTF-8"));
            connection.setFixedLengthStreamingMode(body.length);
            OutputStream output = connection.getOutputStream();
            output.write(body);
            output.flush();
            output.close();

            int responseCode = connection.getResponseCode();
            InputStream responseStream = responseCode >= 400
                    ? connection.getErrorStream()
                    : connection.getInputStream();
            String responseBody = readLimited(responseStream);
            if (responseCode < 200 || responseCode >= 300) {
                if (responseCode == 408 || responseCode == 504) {
                    return UploadResult.failure("Upload timed out.");
                }
                if (responseCode == 503) {
                    return UploadResult.failure("GitHub relay is not configured.");
                }
                return UploadResult.failure("Server unavailable (HTTP " + responseCode + ").");
            }
            String githubUrl = extractJsonString(responseBody, "githubUrl");
            if (responseBody.indexOf("\"success\":true") < 0 || githubUrl.length() == 0) {
                return UploadResult.failure("Invalid response from upload relay.");
            }
            return UploadResult.success(githubUrl);
        } catch (java.net.SocketTimeoutException error) {
            return UploadResult.failure("Upload timed out.");
        } catch (IOException error) {
            return UploadResult.failure("Network unavailable or server unreachable.");
        } catch (Throwable error) {
            return UploadResult.failure("Upload failed safely.");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readLimited(InputStream input) throws IOException {
        if (input == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, Charset.forName("UTF-8")));
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[1024];
        int read;
        while ((read = reader.read(buffer)) >= 0 && result.length() < MAX_RESPONSE_BYTES) {
            int remaining = MAX_RESPONSE_BYTES - result.length();
            result.append(buffer, 0, Math.min(read, remaining));
        }
        reader.close();
        return result.toString();
    }

    private static String extractJsonString(String json, String key) {
        if (json == null) {
            return "";
        }
        String marker = "\"" + key + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            return "";
        }
        start += marker.length();
        int end = start;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\') {
                break;
            }
            end++;
        }
        if (end >= json.length()) {
            return "";
        }
        return json.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
