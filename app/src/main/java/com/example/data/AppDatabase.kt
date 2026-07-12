package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// 1. Entities

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val filePath: String,
    val fileName: String,
    val isDirectory: Boolean,
    val addedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "recycle_bin")
data class RecycleEntity(
    @PrimaryKey val recyclePath: String, // Path inside the hidden recycle directory
    val originalPath: String,           // Original path before deletion
    val fileName: String,
    val isDirectory: Boolean,
    val size: Long,
    val deletedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_files")
data class RecentEntity(
    @PrimaryKey val filePath: String,
    val fileName: String,
    val isDirectory: Boolean,
    val size: Long,
    val openedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

// 2. DAOs

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedTimestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE filePath = :path)")
    fun isFavorite(path: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE filePath = :path")
    suspend fun deleteFavorite(path: String)
}

@Dao
interface RecycleDao {
    @Query("SELECT * FROM recycle_bin ORDER BY deletedTimestamp DESC")
    fun getAllRecycled(): Flow<List<RecycleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecycle(recycle: RecycleEntity)

    @Query("DELETE FROM recycle_bin WHERE recyclePath = :recyclePath")
    suspend fun deleteRecycle(recyclePath: String)

    @Query("DELETE FROM recycle_bin")
    suspend fun clearRecycleBin()
}

@Dao
interface RecentDao {
    @Query("SELECT * FROM recent_files ORDER BY openedTimestamp DESC LIMIT 30")
    fun getRecentFiles(): Flow<List<RecentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecent(recent: RecentEntity)

    @Query("DELETE FROM recent_files WHERE filePath = :path")
    suspend fun deleteRecent(path: String)
}

@Dao
interface SettingDao {
    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: SettingEntity)
}

// 3. Database

@Database(
    entities = [FavoriteEntity::class, RecycleEntity::class, RecentEntity::class, SettingEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun recycleDao(): RecycleDao
    abstract fun recentDao(): RecentDao
    abstract fun settingDao(): SettingDao
}
