# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Capacitor 通过反射调用插件与 @PluginMethod，必须保留注解成员
-keep @com.getcapacitor.annotation.CapacitorPlugin class * { *; }
-keepclassmembers class * {
    @com.getcapacitor.PluginMethod <methods>;
}

# Capacitor 核心类内部依赖 PluginHandle.pluginAnnotation 反射读取权限别名。
# 只保留插件子类时，R8 会把该字段与 getPluginAnnotation() 内联后判定为恒空，
# 将 requestPermissionForAliases / getPermissionStates 整段替换成恒抛 NPE 的代码，
# 表现为 release 包首次登录后申请通知权限立刻闪退。核心类必须整体保留。
-keep class com.getcapacitor.** { *; }

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
