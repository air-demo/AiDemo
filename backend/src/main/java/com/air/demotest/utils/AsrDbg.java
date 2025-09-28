package com.air.demotest.utils;

public final class AsrDbg {
    public static boolean on = true;
    public static void log(String fmt, Object... args) {
        if (on) System.out.println("[ASR] " + String.format(fmt, args));
    }
}

