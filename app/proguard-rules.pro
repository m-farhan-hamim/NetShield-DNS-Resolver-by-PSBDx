# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# Manual JSON parsing (org.json) - no reflection, just keep it quiet.
-dontwarn org.json.**

# Keep line numbers in stack traces for crash reports, but hide the source file.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Extra free hardening on top of the default R8 rules ---

# Repackage every obfuscated class into a single flat, anonymous package
# instead of preserving the original package structure. Makes the app's
# internal architecture much less obvious from a decompiled release build.
-repackageclasses ''

# Let R8 relax access modifiers (private/protected -> package/public) where
# doing so enables more aggressive inlining and merging. Slightly smaller,
# slightly harder-to-follow bytecode; behavior is unaffected.
-allowaccessmodification

# Strip debug/verbose logging from release builds entirely - the Log.d/Log.v
# calls scattered through the services and widgets exist for development
# troubleshooting only, and have no reason to ship in a release APK (smaller
# APK, nothing for a reverse engineer to read off logcat, no risk of
# leaking implementation details in production).
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
