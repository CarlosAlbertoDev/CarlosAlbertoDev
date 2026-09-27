package dev.carlosalberto.rotaentregas.data.parser

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Reconhecimento de texto 100% on-device (ML Kit), portanto funciona sem internet.
 * `InputImage.fromFilePath` lê o EXIF da foto para corrigir a orientação automaticamente,
 * o que cobre fotos tiradas na vertical, horizontal ou de cabeça para baixo.
 */
class OcrProcessor(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun reconhecerTexto(imagemUri: Uri): String {
        val inputImage = InputImage.fromFilePath(context, imagemUri)
        val resultado = recognizer.process(inputImage).await()
        return resultado.text
    }
}
