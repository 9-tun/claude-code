# Keep Vosk / JNA classes — they are loaded reflectively via native bindings.
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-dontwarn com.sun.jna.**
-dontwarn java.awt.**
