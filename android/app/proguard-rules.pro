# R8 dla wersji publikowanej (deskmate-diy.apk).
# Nasz kod jest mały – zostawiamy go w całości (łatwiejsze ślady stosu w logach, zero ryzyka refleksji).
-keep class pl.arekp.deskmate.** { *; }
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# OkHttp / Okio – opcjonalne zależności platformowe
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**
