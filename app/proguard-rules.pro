# Catet AI Proguard Rules

# Preserve Whisper Native JNI
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.catetai.app.engine.whisper.** { *; }

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# Google Tink
-keep class com.google.crypto.tink.** { *; }
