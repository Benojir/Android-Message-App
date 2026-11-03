# Project NextGen Messages — ProGuard & R8 Optimization and Obfuscation Rules
# Target: Production Release (Step 12)

# 1. Android Platform & Framework Components
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod

# 2. Material Design 3 & AndroidX
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**
-keep class androidx.appcompat.** { *; }
-keep class androidx.recyclerview.widget.** { *; }
-keep class androidx.constraintlayout.** { *; }
-keep class androidx.coordinatorlayout.** { *; }

# 3. ViewBinding Rules
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(...);
    public static * bind(...);
}
-keep class com.nextgen.messages.databinding.** { *; }

# 4. Room Database Rules
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public abstract <methods>;
}
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class com.nextgen.messages.smartinbox.db.** { *; }
-keepclassmembers class com.nextgen.messages.smartinbox.db.** { *; }

# 5. Data Models & Value Objects
-keepclassmembers class com.nextgen.messages.data.model.** { *; }
-keep class com.nextgen.messages.data.model.** { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 6. AndroidX Biometric & Keystore Security
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**
-keep class com.nextgen.messages.security.lock.** { *; }
-keep class com.nextgen.messages.security.backup.** { *; }
-keepclassmembers class com.nextgen.messages.security.backup.** { *; }

# 7. Privacy Preservation: Strip Debug & Verbose Logging in Release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
