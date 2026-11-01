# kotlinx.serialization (documento de notas e backup)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class ** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.organizador.**$$serializer { *; }
-keepclassmembers class com.organizador.** { *** Companion; }
