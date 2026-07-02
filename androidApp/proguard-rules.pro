# Only libraries that rely on runtime reflection AND ship no consumer
# ProGuard rules of their own are listed here. Libraries that bundle their
# own rules (kotlinx-serialization, Ktor, kotlinx-coroutines, SQLDelight,
# Decompose) are intentionally omitted.

# Attributes required by reflection-based (de)serialization.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

##---------------------------------------------------------------------------------
# Jackson (jackson-databind, jackson-module-kotlin, jackson-dataformat-yaml).
# Heavy reflection, no consumer rules shipped.
##---------------------------------------------------------------------------------
-keep class com.fasterxml.jackson.** { *; }
-keep interface com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.databind.**

# jackson-module-kotlin reads Kotlin metadata via this annotation.
-keep class kotlin.Metadata { *; }

# snakeyaml is bundled by jackson-dataformat-yaml and instantiates beans via reflection.
-keep class org.yaml.snakeyaml.** { *; }
-dontwarn org.yaml.snakeyaml.**

##---------------------------------------------------------------------------------
# Wycliffe libraries whose model classes are (de)serialized via Jackson reflection
# (kotlin-resource-container manifest parsing, usfmtools).
##---------------------------------------------------------------------------------
-keep class org.wycliffeassociates.** { *; }
-dontwarn org.wycliffeassociates.**
