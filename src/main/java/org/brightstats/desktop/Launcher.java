package org.brightstats.desktop;

/** Separate launcher keeps command-line calculations independent of JavaFX startup. */
public final class Launcher {
    private Launcher() {}
    public static void main(String[] args) { javafx.application.Application.launch(Studio.class, args); }
}
