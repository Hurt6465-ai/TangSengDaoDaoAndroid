# Retrofit/Gson 依赖运行时泛型签名和注解。
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# 交友接口模型使用 public 字段反序列化；宿主 App 开启 R8 后必须保留字段名。
-keep class com.chat.dating.model.** { *; }

# Retrofit 通过注解代理接口。
-keep interface com.chat.dating.DatingService { *; }
-dontwarn com.chat.dating.**
