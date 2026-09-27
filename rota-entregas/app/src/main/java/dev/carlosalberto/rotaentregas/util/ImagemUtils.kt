package dev.carlosalberto.rotaentregas.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

object ImagemUtils {
    fun criarUriParaFoto(context: Context): Uri {
        val diretorio = File(context.cacheDir, "fotos").apply { mkdirs() }
        val arquivo = File(diretorio, "foto_${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", arquivo)
    }
}
