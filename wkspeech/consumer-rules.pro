# LearningTtsBridge calls SpeechManager by exact class/method name.
# Keep the class and public bridge methods in minified release builds.
-keep public class com.chat.speech.SpeechManager {
    public static com.chat.speech.SpeechManager get(android.content.Context);
    public static void speak(android.content.Context, java.lang.String);
    public static void speak(android.content.Context, java.lang.String, java.lang.String, java.lang.String);
    public static void speak(android.content.Context, java.lang.String, java.lang.String, java.lang.String, java.lang.String);
    public void stop();
}

