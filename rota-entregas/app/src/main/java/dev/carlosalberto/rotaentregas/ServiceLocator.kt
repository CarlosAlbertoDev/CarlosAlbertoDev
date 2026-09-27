package dev.carlosalberto.rotaentregas

import android.content.Context
import dev.carlosalberto.rotaentregas.data.db.AppDatabase
import dev.carlosalberto.rotaentregas.data.geocode.GeocodingRepository
import dev.carlosalberto.rotaentregas.data.location.LocationTracker
import dev.carlosalberto.rotaentregas.data.parser.OcrProcessor
import dev.carlosalberto.rotaentregas.data.repository.EntregaRepository
import dev.carlosalberto.rotaentregas.data.repository.ParadaRepository
import dev.carlosalberto.rotaentregas.data.route.RouteCoordinator
import dev.carlosalberto.rotaentregas.data.settings.SettingsRepository

/**
 * Fábrica manual de dependências. O app é pequeno o suficiente para dispensar um
 * framework de injeção de dependências (Hilt/Koin) — isso mantém o build simples
 * de compilar em CI e evita processadores de anotação extras.
 */
object ServiceLocator {

    @Volatile private var contextoApp: Context? = null

    fun inicializar(context: Context) {
        contextoApp = context.applicationContext
    }

    private fun contexto(): Context =
        contextoApp ?: throw IllegalStateException("ServiceLocator não inicializado")

    val database: AppDatabase by lazy { AppDatabase.obter(contexto()) }
    val geocodingRepository: GeocodingRepository by lazy { GeocodingRepository() }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(contexto()) }
    val ocrProcessor: OcrProcessor by lazy { OcrProcessor(contexto()) }
    val locationTracker: LocationTracker by lazy { LocationTracker(contexto()) }

    val paradaRepository: ParadaRepository by lazy {
        ParadaRepository(database.paradaDao(), database.entregaDao(), geocodingRepository, settingsRepository)
    }

    val entregaRepository: EntregaRepository by lazy { EntregaRepository(database.entregaDao()) }

    val routeCoordinator: RouteCoordinator by lazy {
        RouteCoordinator(paradaRepository, settingsRepository)
    }
}
