# R8 rules for PocketDeck. Libraries (Hilt, Room, kotlinx.serialization, Coil, Compose) ship their own consumer rules.

# Debug and info logging is for development only; it is removed from release builds (messages, scan details, AI steps).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Ktor's logging backend is optional and not packaged.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
