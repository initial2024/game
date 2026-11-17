# Test-only compile-time Error Prone annotations used by AndroidX Test.
# These annotations are not reflected on or executed on Android. Keep the exemption
# scoped to instrumentation; the shipping game does not suppress missing classes.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.MustBeClosed
