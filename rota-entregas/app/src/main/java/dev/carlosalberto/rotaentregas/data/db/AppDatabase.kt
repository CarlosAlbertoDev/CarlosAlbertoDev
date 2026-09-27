package dev.carlosalberto.rotaentregas.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.carlosalberto.rotaentregas.data.db.dao.EntregaDao
import dev.carlosalberto.rotaentregas.data.db.dao.ParadaDao
import dev.carlosalberto.rotaentregas.data.db.entity.EntregaRegistroEntity
import dev.carlosalberto.rotaentregas.data.db.entity.ParadaEntity

@Database(
    entities = [ParadaEntity::class, EntregaRegistroEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun paradaDao(): ParadaDao
    abstract fun entregaDao(): EntregaDao

    companion object {
        @Volatile
        private var instancia: AppDatabase? = null

        fun obter(context: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rota_entregas.db"
                ).build().also { instancia = it }
            }
    }
}
