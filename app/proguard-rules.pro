# Room entities are kept by Room's generated code. Keep model names readable in crash logs.
-keepattributes SourceFile,LineNumberTable
# ZXing (QR code) and ML Kit code scanner ship their own rules; keep Room entities' field names stable
-keep class com.shopmanager.app.data.** { *; }
