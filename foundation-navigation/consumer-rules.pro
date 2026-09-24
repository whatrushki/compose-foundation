# Keep constructors of NavComponent implementations for reflection-based instantiation
-keepclassmembers class * implements app.what.navigation.core.NavComponent {
    <init>(...);
}
