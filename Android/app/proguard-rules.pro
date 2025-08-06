# Règles pour protéger la librairie JNA (utilisée par Vosk)
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }

# Tu peux ajouter d'autres règles ProGuard spécifiques à ton projet ici.