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
    @Query("SELECT * FROM author WHERE id = 1") fun author(): Flow<AuthorProfile?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addPerson(person: Person): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addRecipe(recipe: Recipe): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addVersion(version: RecipeVersion): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addMoment(moment: FamilyMoment): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAuthor(profile: AuthorProfile)
    @Update suspend fun updatePerson(person: Person)
    @Update suspend fun updateRecipe(recipe: Recipe)
    @Update suspend fun updateMoment(moment: FamilyMoment)
    @Delete suspend fun deleteRecipe(recipe: Recipe)
    @Delete suspend fun deletePerson(person: Person)
    @Delete suspend fun deleteMoment(moment: FamilyMoment)
    @Query("DELETE FROM versions WHERE recipeId = :recipeId") suspend fun deleteVersions(recipeId: Long)
    @Query("DELETE FROM versions") suspend fun clearVersions()
    @Query("DELETE FROM recipes") suspend fun clearRecipes()
    @Query("DELETE FROM people") suspend fun clearPeople()
    @Query("DELETE FROM moments") suspend fun clearMoments()
    @Query("UPDATE recipes SET personId = NULL WHERE personId = :personId") suspend fun detachRecipes(personId: Long)
    @Query("DELETE FROM author") suspend fun clearAuthor()
}

@Database(entities = [Person::class, Recipe::class, RecipeVersion::class, FamilyMoment::class, AuthorProfile::class],
    version = 2, exportSchema = false)
@TypeConverters(PhotoConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): FamilyDao
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `author` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                    "`tagline` TEXT NOT NULL, `bio` TEXT NOT NULL, `photos` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "family-recipes.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
