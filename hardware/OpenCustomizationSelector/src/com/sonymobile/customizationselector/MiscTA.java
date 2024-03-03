package com.sonymobile.customizationselector;

import com.sonymobile.miscta.MiscTaException;
import java.nio.charset.StandardCharsets;

public class MiscTA {
    public static byte[] read(int unit) {
        return com.sonymobile.miscta.MiscTA.read(unit);
    }

    public static String readString(int unit) {
        return new String(read(unit), StandardCharsets.UTF_8);
    }

    public static void write(int unit, byte[] data) throws MiscTaException{
        com.sonymobile.miscta.MiscTA.write(unit, data);
    }

    public static void write(int unit, String data) throws MiscTaException{
        write(unit, data.getBytes(StandardCharsets.UTF_8));
    }
}
