# ── Ather audiobook — R8 production rules ─────────────────────────────────
# Demo / seeder classes (DatabaseSeeder, DemoAudioProvider) live in src/main,
# but every reference is guarded by BuildConfig.DEBUG, a compile-time constant
# that is false in release builds, so R8 strips them there. The debug manifest
# registers DemoAudioProvider; the release manifest does not.
#
# Library are kept from the AARs: media3, Hilt, Room and Compose ship their own
# consumer rules and are ingested automatically by AGP.

# Room persists enums via RoomConverters as their `.name`. Keep enumeration
# members so stored databases remain readable if enum names were renamed.
-keepclassmembers enum com.example.audiobook.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Room entities: cheap insurance so cursor mapping never breaks with obfuscation.
-keep class com.example.audiobook.data.room.entity.** { *; }

# Room / Hilt / reflection-friendly attributes.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Application entry points referenced by the manifest / Hilt generated code.
-keep class com.example.audiobook.AudiobookApplication { *; }
-keep class com.example.audiobook.MainActivity { *; }

# Prevent obfuscation warnings turning into failures for optional JDK classes.
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn javax.security.**