# kotlinx.serialization: keep generated serializers for API models.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.metech.lunch.core.model.**$$serializer { *; }
-keepclassmembers class com.metech.lunch.core.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit: keep service interface methods and generic signatures.
-keepattributes Signature, Exceptions
-keep,allowobfuscation interface com.metech.lunch.core.network.LunchApi
