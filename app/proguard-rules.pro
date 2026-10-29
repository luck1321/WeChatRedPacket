# ============================================================
# 混淆规则（合规范围内的保护）
# 目的：提高静态反编译门槛、移除日志、压缩体积
# 注意：这只能提高成本，无法杜绝逆向；且不能防止微信按行为封号。
# ============================================================

# 保留注解与签名信息（避免无障碍/反射相关问题）
-keepattributes *Annotation*,Signature,InnerClasses

# 保留四大组件入口（清单已声明，这里双保险）
-keep public class * extends android.app.Activity
-keep public class * extends android.accessibilityservice.AccessibilityService
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# 保留日志单例（运行时仍记录抢红包，但不依赖 android.util.Log）
-keep class com.example.redpacket.RedPacketLogger { *; }

# 移除 android.util.Log 的调用，减少逻辑泄露
-assumenosideeffects class android.util.Log {
    public static int d(java.lang.String, java.lang.String);
    public static int v(java.lang.String, java.lang.String);
    public static int i(java.lang.String, java.lang.String);
    public static int w(java.lang.String, java.lang.String);
    public static int e(java.lang.String, java.lang.String);
}

# 优化强度
-optimizationpasses 5
-allowaccessmodification

# 收紧异常与行号（增加阅读难度）
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# 兜底：忽略第三方依赖的混淆警告，保证 R8 构建通过（不影响混淆强度）
-ignorewarnings

# 使用非 ASCII（Unicode）混淆字典，将类名/方法名混淆为汉字等字符，进一步增加反编译阅读难度（合规内）
-obfuscationdictionary ../dict.txt
-classobfuscationdictionary ../dict.txt
