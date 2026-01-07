# ProGuard rules for Telegram SMS

# ==================== General Android Rules ====================
# Keep Android components (Activities, Services, Receivers, etc.)
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class * extends android.accessibilityservice.AccessibilityService

# Keep custom views
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ==================== Kotlin Rules ====================
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Keep Kotlin data classes
-keepclassmembers class * {
    public <init>(...);
}

# ==================== Gson Rules ====================
# Gson uses generic type information stored in a class file when working with fields.
# Proguard removes such information by default, so configure it to keep all of it.
-keepattributes Signature

# For using GSON @Expose annotation
-keepattributes *Annotation*

# Gson specific classes
-dontwarn sun.misc.**


# Keep data classes that are serialized with Gson
-keep class com.qwe7002.telegram_sms.data_structure.** { *; }
-keepclassmembers class com.qwe7002.telegram_sms.data_structure.** { *; }

# Prevent proguard from stripping interface information from TypeAdapter, TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Prevent R8 from leaving Data object members always null
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ==================== OkHttp Rules ====================
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# A resource is loaded with a relative path so the package of this class must be preserved.
-adaptresourcefilenames okhttp3/internal/publicsuffix/PublicSuffixDatabase.gz

# Animal Sniffer compileOnly dependency to ensure APIs are compatible with older versions of Java.
-dontwarn org.codehaus.mojo.animal_sniffer.*

# OkHttp platform used only on JVM and when Conscrypt and other security providers are available.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ==================== Conscrypt Rules ====================
-keep class org.conscrypt.Conscrypt { *; }
-keep class org.conscrypt.ConscryptHostnameVerifier { *; }
-dontwarn org.conscrypt.**

# ==================== MMKV Rules ====================
-keep class com.tencent.mmkv.** { *; }
-dontwarn com.tencent.mmkv.**

# ==================== Lazysodium / JNA Rules ====================
-keep class com.sun.jna.Structure { *; }
-keep class com.sun.jna.Callback { *; }
-keep class com.sun.jna.Library { *; }
-keep class com.sun.jna.Native { *; }
-keep class com.sun.jna.Pointer { *; }
-keep class * implements com.sun.jna.Library { *; }
-keep class * implements com.sun.jna.Callback { *; }
-keep class * extends com.sun.jna.Structure { *; }
-dontwarn com.sun.jna.**

-keep class com.goterl.lazysodium.LazySodiumAndroid { *; }
-keep class com.goterl.lazysodium.interfaces.** { *; }
-keep class com.goterl.lazysodium.utils.** { *; }
-dontwarn com.goterl.lazysodium.**

# ==================== QR Code Scanner Rules ====================
-keep class com.budiyev.android.codescanner.** { *; }
-dontwarn com.budiyev.android.codescanner.**

# ==================== AwesomeQRCode Rules ====================
-keep class com.github.sumimakito.awesomeqrcode.** { *; }
-keep class com.github.sumimakito.codeauxlib.** { *; }

# ==================== App-specific Rules ====================
# Keep value classes
-keep class com.qwe7002.telegram_sms.value.** { *; }

# Keep MMKV migration classes
-keep class com.qwe7002.telegram_sms.MMKV.** { *; }

# Keep USSD callback (system callback)
-keep class com.qwe7002.telegram_sms.USSDCallBack { *; }

# Keep NotificationService (accessibility service)
-keep class com.qwe7002.telegram_sms.NotificationService { *; }

# ==================== AndroidX Rules ====================
# AndroidX libraries have their own consumer ProGuard rules, no need to keep all classes
-dontwarn androidx.**

# ==================== Debugging ====================
# Uncomment this to preserve the line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to hide the original source file name.
#-renamesourcefileattribute SourceFile

