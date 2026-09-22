# Regras do ProGuard para o KmCerto

# Manter classes de modelo de dados de oferta e configurações
-keep class br.com.entregador.lucro.domain.model.** { *; }

# Suporte a Kotlin Coroutines e DataStore
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keepclassmembers class * {
    @kotlin.jvm.JvmField *;
}

# AndroidX Security Crypto (EncryptedSharedPreferences)
-keepclassmembers class * extends androidx.security.crypto.EncryptedSharedPreferences { *; }
