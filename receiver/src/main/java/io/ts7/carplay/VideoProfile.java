package io.ts7.carplay;

public enum VideoProfile {
    DEFAULT(30), BALANCED(25), STABILITY(20);

    public final int width = 1280;
    public final int height = 720;
    public final int fps;

    VideoProfile(int fps) { this.fps = fps; }
}
