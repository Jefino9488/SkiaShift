-keep class com.jefino.skiashift.SkiaShiftModule { *; }
-keepclassmembers class com.jefino.skiashift.SkiaShiftModule {
    native <methods>;
}

-keep class com.jefino.skiashift.MainActivity { *; }
-keep class com.jefino.skiashift.BootReceiver { *; }

# Keep native methods globally just in case
-keepclasseswithmembernames class * {
    native <methods>;
}

# SkiaShift Provider
-keep class com.jefino.skiashift.SkiaShiftProvider { *; }
