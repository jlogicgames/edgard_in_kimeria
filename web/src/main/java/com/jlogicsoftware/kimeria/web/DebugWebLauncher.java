package com.jlogicsoftware.kimeria.web;

/** Development target entry point; release uses WebLauncher directly. */
public final class DebugWebLauncher {
    public static void main(String[] args) {
        WebLauncher.launch(true);
    }
}
