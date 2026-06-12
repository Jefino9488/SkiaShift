package com.jefino.skiashift;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedModule;
public class DumpMethods {
    public static void dump() {
        for (Method m : XposedModule.class.getMethods()) {
            android.util.Log.i("SkiaShiftDump", m.toString());
        }
    }
}
