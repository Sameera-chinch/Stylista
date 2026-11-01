package com.example.stylista.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.stylista.utils.CampusLocationsData
import com.example.stylista.utils.ProductRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ProductEntity::class,
        WishlistEntity::class,
        CartEntity::class,
        OrderEntity::class,
        CampusLocationEntity::class,
        AddressEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class StylistaDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun wishlistDao(): WishlistDao
    abstract fun cartDao(): CartDao
    abstract fun orderDao(): OrderDao
    abstract fun campusLocationDao(): CampusLocationDao
    abstract fun addressDao(): AddressDao

    companion object {
        @Volatile
        private var INSTANCE: StylistaDatabase? = null

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add extended fields to products table
                db.execSQL("ALTER TABLE products ADD COLUMN imageUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products ADD COLUMN inStock INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE products ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN isFeatured INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN isNewArrival INTEGER NOT NULL DEFAULT 0")

                // Add user scoping to cart, wishlist, and orders
                db.execSQL("ALTER TABLE cart ADD COLUMN userId TEXT NOT NULL DEFAULT 'guest_user'")
                db.execSQL("ALTER TABLE wishlist ADD COLUMN userId TEXT NOT NULL DEFAULT 'guest_user'")
                db.execSQL("ALTER TABLE orders ADD COLUMN userId TEXT NOT NULL DEFAULT 'guest_user'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS addresses (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        phone TEXT NOT NULL,
                        pincode TEXT NOT NULL,
                        houseFlat TEXT NOT NULL,
                        streetArea TEXT NOT NULL,
                        landmark TEXT NOT NULL DEFAULT '',
                        city TEXT NOT NULL,
                        state TEXT NOT NULL,
                        type TEXT NOT NULL DEFAULT 'Home',
                        isDefault INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN orderNumber TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE orders ADD COLUMN itemsJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE orders ADD COLUMN addressJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE orders ADD COLUMN status TEXT NOT NULL DEFAULT 'PLACED'")
                db.execSQL("ALTER TABLE orders ADD COLUMN statusHistoryJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE orders ADD COLUMN subtotal INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE orders ADD COLUMN deliveryFee INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE orders ADD COLUMN discountAmount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE orders ADD COLUMN estimatedDeliveryDate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE orders ADD COLUMN paymentStatus TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE orders ADD COLUMN razorpayPaymentId TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): StylistaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StylistaDatabase::class.java,
                    "stylista_database"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback(context.applicationContext))
                    .build()
                INSTANCE = instance
                CoroutineScope(Dispatchers.IO).launch {
                    seedDatabaseIfEmpty(context.applicationContext, instance)
                }
                instance
            }
        }

        private class DatabaseCallback(
            private val context: Context
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    val instance = getInstance(context)
                    seedDatabaseIfEmpty(context, instance)
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                CoroutineScope(Dispatchers.IO).launch {
                    val instance = getInstance(context)
                    seedDatabaseIfEmpty(context, instance)
                }
            }
        }

        suspend fun seedDatabaseIfEmpty(context: Context, database: StylistaDatabase) {
            try {
                // Seed products using ProductRepository from assets
                ProductRepository.ensureDatabaseInitialized(context)

                // Seed campus locations if empty
                val campusDao = database.campusLocationDao()
                if (campusDao.getLocationsCount() == 0) {
                    campusDao.insertLocations(CampusLocationsData.defaultLocations)
                }
            } catch (e: Exception) {
                android.util.Log.e("StylistaDatabase", "Error seeding database", e)
            }
        }
    }
}
