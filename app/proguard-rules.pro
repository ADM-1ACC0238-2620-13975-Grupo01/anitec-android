# Rules for the release build (R8). Most libraries ship their own consumer rules; only what the app needs is here.

# Keep line numbers so crash reports from testers can be read.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Navigation routes are @Serializable classes that the navigation library rebuilds from their names.
-keep @kotlinx.serialization.Serializable class com.anitec.platform.app.** { *; }
