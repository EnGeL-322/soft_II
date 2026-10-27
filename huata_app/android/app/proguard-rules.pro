# OCR del voucher (google_mlkit_text_recognition, RF-85).
# El plugin referencia los reconocedores de chino, devanagari, japones y coreano, pero la app solo
# incluye el modelo latino. Sin estas lineas R8 corta el build release con
# "Missing classes detected while running R8: com.google.mlkit.vision.text.chinese...".
-dontwarn com.google.mlkit.vision.text.chinese.**
-dontwarn com.google.mlkit.vision.text.devanagari.**
-dontwarn com.google.mlkit.vision.text.japanese.**
-dontwarn com.google.mlkit.vision.text.korean.**

# ML Kit carga sus componentes por reflexion: si R8 los renombra, el reconocedor falla en tiempo de
# ejecucion solo en release (en debug funciona), que es justo el "el OCR no funciona en el APK".
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-keep class com.google_mlkit_text_recognition.** { *; }
-keep class com.google_mlkit_commons.** { *; }
