# The app uses no reflection-based serialization (JSON is parsed by hand with
# org.json), so the default optimize rules are enough.
# Strip verbose/debug logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
