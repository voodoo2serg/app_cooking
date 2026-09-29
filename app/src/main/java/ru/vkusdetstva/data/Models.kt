package ru.vkusdetstva.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import org.json.JSONArray

@Entity(tableName = "people")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val relation: String = "",
    val years: String = "",
    val story: String = "",
    val photos: List<String> = emptyList()
)

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val personId: Long? = null,
    val story: String = "",
    val ingredients: String = "",
    val steps: String = "",
    val notes: String = "",
    val photos: List<String> = emptyList(),
    val audioPath: String? = null,
    val timesCooked: Int = 0,
    val taste: Int = 0,
    val ease: Int = 0,
    val memory: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val likes: Int = 0
)

/** Единственная строка (id = 1) — владелец книги. Рецепты без personId выходят от его лица. */
@Entity(tableName = "author")
data class AuthorProfile(
    @PrimaryKey val id: Long = 1L,
    val name: String = "",
    val tagline: String = "",
    val bio: String = "",
    val photos: List<String> = emptyList()
)

@Entity(tableName = "versions")
data class RecipeVersion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val personName: String,
    val change: String,
    val note: String = "",
    val photos: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "moments")
data class FamilyMoment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val story: String = "",
    val people: String = "",
    val photos: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val likes: Int = 0
)

class PhotoConverter {
    @TypeConverter fun fromList(value: List<String>): String = JSONArray(value).toString()
    @TypeConverter fun toList(value: String): List<String> {
        val array = JSONArray(value)
        return (0 until array.length()).map(array::getString)
    }
}
