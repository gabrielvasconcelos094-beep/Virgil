# Ponte JNI: nomes usados pelo código nativo (virgil_llama.cpp) não podem ser renomeados nem removidos.
-keepclasseswithmembernames,includedescriptorclasses class app.virgil.engine.llama.LlamaNative {
    native <methods>;
}
-keep interface app.virgil.engine.llama.PieceCallback { boolean onPiece(byte[]); }
-keep class * implements app.virgil.engine.llama.PieceCallback { boolean onPiece(byte[]); }
