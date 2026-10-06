package io.ts7.carplay;

public final class TouchMapper {
    private TouchMapper() {}

    public static boolean map(float x, float y, int viewWidth, int viewHeight,
            int videoWidth, int videoHeight, float[] result) {
        if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return false;
        float scale = Math.min((float) viewWidth / videoWidth, (float) viewHeight / videoHeight);
        float width = videoWidth * scale;
        float height = videoHeight * scale;
        float left = (viewWidth - width) / 2;
        float top = (viewHeight - height) / 2;
        if (x < left || y < top || x > left + width || y > top + height) return false;
        result[0] = (x - left) / width;
        result[1] = (y - top) / height;
        return true;
    }
}
