package dev.carlosalberto.rotaentregas.data.db

import androidx.room.TypeConverter
import dev.carlosalberto.rotaentregas.data.model.StatusParada

class Converters {
    @TypeConverter
    fun fromStatus(status: StatusParada): String = status.name

    @TypeConverter
    fun toStatus(value: String): StatusParada = StatusParada.valueOf(value)
}
