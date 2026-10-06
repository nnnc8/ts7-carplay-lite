package io.ts7diag.tool;

public final class UploadResult {
    private final boolean success;
    private final String message;
    private final String githubUrl;

    private UploadResult(boolean success, String message, String githubUrl) {
        this.success = success;
        this.message = message;
        this.githubUrl = githubUrl;
    }

    public static UploadResult success(String githubUrl) {
        return new UploadResult(true, "Upload successful", githubUrl);
    }

    public static UploadResult failure(String message) {
        return new UploadResult(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getGithubUrl() {
        return githubUrl;
    }
}
