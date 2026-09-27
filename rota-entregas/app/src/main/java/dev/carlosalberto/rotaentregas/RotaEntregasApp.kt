package dev.carlosalberto.rotaentregas

import android.app.Application
import androidx.preference.PreferenceManager
import org.osmdroid.config.Configuration

class RotaEntregasApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.inicializar(this)

        // osmdroid exige um User-Agent válido e usa esta configuração para decidir onde
        // guardar o cache de tiles em disco — é esse cache que permite reabrir áreas já
        // visitadas sem internet (mapa "offline" após a primeira visualização da região).
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = packageName
    }
}
