package dev.carlosalberto.rotaentregas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dev.carlosalberto.rotaentregas.ui.nav.RotaEntregasNavHost
import dev.carlosalberto.rotaentregas.ui.theme.RotaEntregasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RotaEntregasTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RotaEntregasNavHost()
                }
            }
        }
    }
}
