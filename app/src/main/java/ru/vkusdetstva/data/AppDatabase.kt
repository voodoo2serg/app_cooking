package ru.vkusdetstva.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyDao {
    @Query("SELECT * FROM people ORDER BY name") fun people(): Flow<List<Person>>
    @Query("SELECT * FROM recipes ORDER BY createdAt DESC") fun recipes(): Flow<List<Recipe>>
    @Query("SELECT * FROM versions ORDER BY createdAt DESC") fun versions(): Flow<List<RecipeVersion>>
    @Query("SELECT * FROM moments ORDER BY createdAt DESC") fun moments(): Flow<List<FamilyMoment>>
    @Query("SELECT * FROM events ORDER BY createdAt DESC") fun events(): Flow<List<FamilyEvent>>
    @Query("SELECT * FROM events") suspend fun eventsSnapshot(): List<FamilyEvent>
    @Query("SELECT * FROM recipes") suspend fun recipesSnapshot(): List<Recipe>
    @Query("SELECT * FROM moments") suspend fun momentsSnapshot(): List<FamilyMoment>
    @Query("SELECT * FROM author WHERE id = 1") fun author(): Flow<AuthorProfile?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addPerson(person: Person): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addRecipe(recipe: Recipe): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addVersion(version: RecipeVersion): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addMoment(moment: FamilyMoment): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addEvent(event: FamilyEvent): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAuthor(profile: AuthorProfile)
    @Update suspend fun updatePerson(person: Person)
    @Update suspend fun updateRecipe(recipe: Recipe)
    @Update suspend fun updateMoment(moment: FamilyMoment)
    @Update suspend fun updateEvent(event: FamilyEvent)
    @Delete suspend fun deleteRecipe(recipe: Recipe)
    @Delete suspend fun deletePerson(person: Person)
    @Delete suspend fun deleteMoment(moment: FamilyMoment)
    @Delete suspend fun deleteEvent(event: FamilyEvent)
    @Query("DELETE FROM versions WHERE recipeId = :recipeId") suspend fun deleteVersions(recipeId: Long)
    @Query("DELETE FROM versions") suspend fun clearVersions()
    @Query("DELETE FROM recipes") suspend fun clearRecipes()
    @Query("DELETE FROM people") suspend fun clearPeople()
    @Query("DELETE FROM moments") suspend fun clearMoments()

    @Query("DELETE FROM events") suspend fun clearEvents()
    @Query("DELETE FROM author") suspend fun clearAuthor()
    @Query("UPDATE recipes SET personId = NULL WHERE personId = :personId") suspend fun unlinkPerson(personId: Long)
    @Query("UPDATE recipes SET likes = MAX(0, likes + :delta) WHERE id = :id") suspend fun likeRecipe(id: Long, delta: Int)
    @Query("UPDATE moments SET likes = MAX(0, likes + :delta) WHERE id = :id") suspend fun likeMoment(id: Long, delta: Int)

    @Query("SELECT * FROM basket_items ORDER BY checked, createdAt") fun basket(): Flow<List<BasketItem>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addBasketItems(items: List<BasketItem>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addBasketItem(item: BasketItem)
    @Update suspend fun updateBasketItem(item: BasketItem)
    @Query("DELETE FROM basket_items WHERE id = :id") suspend fun deleteBasketItem(id: Long)
    @Query("DELETE FROM basket_items WHERE checked = 1") suspend fun clearCheckedBasket()
    @Query("DELETE FROM basket_items") suspend fun clearBasket()
}

@Database(entities = [Person::class, Recipe::class, RecipeVersion::class, FamilyMoment::class,
    FamilyEvent::class, AuthorProfile::class, BasketItem::class], version = 5, exportSchema = false)
@TypeConverters(PhotoConverter::class, IdConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): FamilyDao
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `author` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                    "`tagline` TEXT NOT NULL, `bio` TEXT NOT NULL, `photos` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN likes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE moments ADD COLUMN likes INTEGER NOT NULL DEFAULT 0")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, `story` TEXT NOT NULL, `recipeIds` TEXT NOT NULL, " +
                    "`photos` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Событие перестаёт быть отдельной сущностью и становится тегом
                // у блюда или фотоистории; корзина — новая таблица.
                db.execSQL("ALTER TABLE recipes ADD COLUMN eventTag TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE moments ADD COLUMN eventTag TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE IF NOT EXISTS `basket_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`text` TEXT NOT NULL, `amount` TEXT NOT NULL, `section` TEXT NOT NULL, " +
                    "`source` TEXT NOT NULL, `checked` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
            }
        }
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "family-recipes.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build().also { instance = it }
        }
    }
}
