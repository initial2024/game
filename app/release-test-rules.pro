# Test-only Error Prone annotation metadata refers to a javac enum absent on Android.
# No runtime code uses javax.lang.model; do not apply this exemption to the game APK.
-dontwarn javax.lang.model.element.Modifier
