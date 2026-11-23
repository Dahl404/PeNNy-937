# PeNNy-937 release rules.
#
# Nothing in this app reaches for a class by name: the project format is a hand
# written binary codec (DataInputStream/DataOutputStream), there is no
# serialization library, no JSON binding and no reflection. Compose, AndroidX and
# Kotlin ship their own consumer rules, so R8's defaults plus the attributes
# below are enough.

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,MethodParameters

# Keep the source file and line numbers so a Play crash report points at a line.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin stdlib and coroutines warn about optional JVM-only references.
-dontwarn org.jetbrains.annotations.**
-dontwarn kotlinx.coroutines.**
