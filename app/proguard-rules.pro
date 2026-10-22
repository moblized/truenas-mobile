# JSON is parsed by hand with org.json (part of Android), and OkHttp/Coil ship their own
# consumer rules, so the default Android rules are enough.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Overload Android's X509TrustManagerExtensions looks up by reflection.
-keepclassmembers class app.truenasmobile.net.PinningTrustManager {
    public java.util.List checkServerTrusted(java.security.cert.X509Certificate[], java.lang.String, java.lang.String);
}
