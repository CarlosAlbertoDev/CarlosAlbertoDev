package dev.carlosalberto.rotaentregas.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val EsquemaClaro = lightColorScheme(
    primary = AzulPrimario,
    secondary = VerdeSucesso,
    background = CinzaFundo
)

private val EsquemaEscuro = darkColorScheme(
    primary = AzulPrimarioEscuro,
    secondary = VerdeSucesso
)

@Composable
fun RotaEntregasTheme(
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val temaEscuro = isSystemInDarkTheme()
    val contexto = LocalContext.current

    val esquemaCores = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (temaEscuro) dynamicDarkColorScheme(contexto) else dynamicLightColorScheme(contexto)
        temaEscuro -> EsquemaEscuro
        else -> EsquemaClaro
    }

    MaterialTheme(colorScheme = esquemaCores, content = content)
}
