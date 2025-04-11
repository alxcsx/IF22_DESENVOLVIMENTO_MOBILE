package br.ufpe.cin.acs11.projetoif22.data

import androidx.lifecycle.LiveData
import androidx.room.*
import java.util.Date

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY deadline ASC")
    fun getAllTasks(): LiveData<List<Task>>

    @Query("SELECT * FROM tasks ORDER BY deadline ASC")
    fun getAllTasksByDeadline(): LiveData<List<Task>>

    @Query("SELECT * FROM tasks ORDER BY createdAt ASC")
    fun getAllTasksByCreation(): LiveData<List<Task>>

    @Insert
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("""SELECT * FROM tasks WHERE title LIKE :searchQuery OR description LIKE :searchQuery OR id LIKE :searchQueryId ORDER BY deadline ASC""")
    fun searchTasks(searchQuery: String, searchQueryId: String): LiveData<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: Int): Task?

    @Query("SELECT * FROM tasks ORDER BY deadline ASC")
    suspend fun getAllTasksSync(): List<Task>
}