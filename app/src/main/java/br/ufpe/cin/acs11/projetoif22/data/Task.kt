package br.ufpe.cin.acs11.projetoif22.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val description: String,
    val deadline: Date,
    val isCompleted: Boolean = false,
    val createdAt: Date = Date()
)