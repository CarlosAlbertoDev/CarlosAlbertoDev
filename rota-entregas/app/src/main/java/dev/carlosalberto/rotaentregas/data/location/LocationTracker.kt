package dev.carlosalberto.rotaentregas.data.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dev.carlosalberto.rotaentregas.data.geocode.Coordenada
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Fluxo contínuo da posição do entregador. É a base tanto do "você está aqui" no
 * mapa quanto do gatilho de recálculo dinâmico de rota (ver [dev.carlosalberto.rotaentregas.data.route.RouteCoordinator]).
 * Assume que a permissão de localização já foi concedida pela UI antes de coletar o fluxo.
 */
class LocationTracker(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun localizacoes(intervaloMillis: Long = 5_000L, distanciaMinimaMetros: Float = 10f): Flow<Coordenada> =
        callbackFlow {
            val clienteLocalizacao = LocationServices.getFusedLocationProviderClient(context)
            val requisicao = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervaloMillis)
                .setMinUpdateDistanceMeters(distanciaMinimaMetros)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(resultado: LocationResult) {
                    resultado.lastLocation?.let { local ->
                        trySend(Coordenada(local.latitude, local.longitude))
                    }
                }
            }

            clienteLocalizacao.requestLocationUpdates(requisicao, callback, context.mainLooper)
            awaitClose { clienteLocalizacao.removeLocationUpdates(callback) }
        }
}
