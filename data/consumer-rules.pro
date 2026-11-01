# Entidades serializadas no backup (kotlinx.serialization) precisam manter nomes de campos.
-keepclassmembers class com.organizador.data.local.entity.** { *; }
-keepclassmembers class com.organizador.data.backup.** { *; }
