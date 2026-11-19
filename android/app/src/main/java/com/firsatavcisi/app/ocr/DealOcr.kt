package com.firsatavcisi.app.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * 1.2.0: tamamen cevrimdisi metin tanima.
 * Model APK icine gomuludur, internet izni gerekmez; hicbir veri cihazdan cikmaz.
 */
object DealOcr {
    fun recognize(bitmap: Bitmap, onResult: (String) -> Unit) {
        runCatching {
            val image = InputImage.fromBitmap(bitmap, 0)
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(image)
                .addOnSuccessListener { result -> onResult(result.text) }
                .addOnFailureListener { onResult("") }
        }.onFailure { onResult("") }
    }
}
