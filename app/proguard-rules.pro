# QuickPay - ProGuard/R8 规则
# 默认模板已足够处理 Compose 运行时；本应用无反射、无 JNI、无序列化依赖第三方，
# 所以无需额外 keep 规则。

# Compose
-keep class androidx.compose.** { *; }

# Application 类（本项目无自定义 Application）
-keep class com.example.quickpay.MainActivity