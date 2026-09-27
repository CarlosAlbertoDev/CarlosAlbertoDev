package dev.carlosalberto.rotaentregas.ui.mapa

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.content.Context

/** Gera pinos coloridos simples em runtime, sem depender de arquivos de imagem externos. */
object MarcadorFactory {

    /** [rotulo] desenha um número (ordem da parada na rota, "1", "2", "3"...) centrado no pino. */
    fun criarPino(context: Context, corPreenchimento: Int, tamanhoDp: Int = 36, rotulo: String? = null): BitmapDrawable {
        val densidade = context.resources.displayMetrics.density
        val tamanhoPx = (tamanhoDp * densidade).toInt()
        val bitmap = Bitmap.createBitmap(tamanhoPx, tamanhoPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val raio = tamanhoPx / 2.6f
        val centroX = tamanhoPx / 2f
        val centroY = tamanhoPx / 2.6f

        val pinturaSombra = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80, 0, 0, 0) }
        canvas.drawCircle(centroX, tamanhoPx - raio * 0.6f, raio * 0.5f, pinturaSombra)

        val caminhoGota = android.graphics.Path().apply {
            moveTo(centroX, tamanhoPx.toFloat())
            lineTo(centroX - raio * 0.65f, centroY + raio * 0.4f)
            addCircle(centroX, centroY, raio, android.graphics.Path.Direction.CW)
            lineTo(centroX + raio * 0.65f, centroY + raio * 0.4f)
            close()
        }
        val pinturaPreenchimento = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = corPreenchimento }
        canvas.drawPath(caminhoGota, pinturaPreenchimento)

        val pinturaBorda = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * densidade
        }
        canvas.drawCircle(centroX, centroY, raio, pinturaBorda)

        if (!rotulo.isNullOrBlank()) {
            val pinturaTexto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = raio * 1.05f
                isFakeBoldText = true
            }
            val yCentralizado = centroY - (pinturaTexto.descent() + pinturaTexto.ascent()) / 2
            canvas.drawText(rotulo, centroX, yCentralizado, pinturaTexto)
        }

        return BitmapDrawable(context.resources, bitmap)
    }

    /**
     * Seta apontando para cima (posição/direção do entregador), em vez de um pino —
     * no mapa que gira (modo navegação) ela fica sempre "reta"; no mapa fixo (visão
     * geral), gira via [org.osmdroid.views.overlay.Marker.setRotation] conforme o
     * bearing do GPS, do mesmo jeito que o indicador azul do Google Maps.
     */
    fun criarSetaDirecao(context: Context, corPreenchimento: Int, tamanhoDp: Int = 34): BitmapDrawable {
        val densidade = context.resources.displayMetrics.density
        val tamanhoPx = (tamanhoDp * densidade).toInt()
        val bitmap = Bitmap.createBitmap(tamanhoPx, tamanhoPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = tamanhoPx.toFloat()

        val pinturaHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) }
        canvas.drawCircle(c / 2f, c / 2f, c / 2f, pinturaHalo)

        val caminhoSeta = android.graphics.Path().apply {
            moveTo(c * 0.5f, c * 0.08f)
            lineTo(c * 0.85f, c * 0.88f)
            lineTo(c * 0.5f, c * 0.68f)
            lineTo(c * 0.15f, c * 0.88f)
            close()
        }
        canvas.drawPath(caminhoSeta, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = corPreenchimento })

        val pinturaBorda = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * densidade
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(caminhoSeta, pinturaBorda)

        return BitmapDrawable(context.resources, bitmap)
    }

    const val COR_PENDENTE = 0xFF1976D2.toInt()   // azul: aguardando visita
    const val COR_PROXIMA = 0xFFD32F2F.toInt()    // vermelho: próxima parada da rota
    const val COR_ENTREGUE = 0xFF2E7D32.toInt()   // verde: entregue com sucesso
    const val COR_FALHOU = 0xFFF9A825.toInt()     // amarelo: falhou, desconectada da rota
    const val COR_USUARIO = 0xFF6A1B9A.toInt()    // roxo: posição atual do entregador
}
