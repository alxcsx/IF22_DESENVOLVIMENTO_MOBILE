package br.ufpe.cin.acs11.projetoif22.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import br.ufpe.cin.acs11.projetoif22.R
import br.ufpe.cin.acs11.projetoif22.Utilities
import br.ufpe.cin.acs11.projetoif22.data.Task
import br.ufpe.cin.acs11.projetoif22.data.TaskDatabase
import br.ufpe.cin.acs11.projetoif22.workers.TaskReminderWorker
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class AddTaskActivity : AppCompatActivity() {
    private lateinit var titleInput: TextInputEditText
    private lateinit var descriptionInput: TextInputEditText
    private lateinit var selectedDateTimeText: TextView
    private val calendar = Calendar.getInstance()
    private var taskId = 0
    private var isEditMode = false
    private var createdAt = Date()
    private var dateTimeSelected = false

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_task)

        titleInput = findViewById(R.id.titleInput)
        descriptionInput = findViewById(R.id.descriptionInput)
        selectedDateTimeText = findViewById(R.id.selectedDateTimeText)

        setupToolbar()
        loadTaskData(savedInstanceState)
        setupListeners()
    }

    private fun setupToolbar() {
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.add_task)
    }

    private fun loadTaskData(savedInstanceState: Bundle?) {
        savedInstanceState?.let { bundle ->
            taskId = bundle.getInt("taskId", 0)
            isEditMode = bundle.getBoolean("isEditMode", false)
            createdAt = Date(bundle.getLong("createdAt"))
            dateTimeSelected = bundle.getBoolean("dateTimeSelected", false)
            calendar.timeInMillis = bundle.getLong("calendarTime")
            titleInput.setText(bundle.getString("title"))
            descriptionInput.setText(bundle.getString("description"))
            updateDateTimeText()

            if (isEditMode) {
                supportActionBar?.title = getString(R.string.edit_task)
            }
            return
        }

        intent.extras?.let { bundle ->
            taskId = bundle.getInt("task_id", 0)
            if (taskId > 0) {
                isEditMode = true
                titleInput.setText(bundle.getString("task_title", ""))
                descriptionInput.setText(bundle.getString("task_description", ""))

                val deadline = bundle.getLong("task_deadline")
                calendar.timeInMillis = deadline
                dateTimeSelected = true
                updateDateTimeText()

                createdAt = Date(bundle.getLong("task_created_at"))
                supportActionBar?.title = getString(R.string.edit_task)
            }
        }
    }

    private fun setupListeners() {
        findViewById<Button>(R.id.dateTimeButton).setOnClickListener { showDateTimePicker() }
        findViewById<Button>(R.id.saveButton).setOnClickListener { saveTask() }
    }


    private fun showDateTimePicker() {
        DatePickerDialog(
            this,
            { _, year, month, day ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)
                showTimePicker()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker() {
        TimePickerDialog(
            this,
            { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                dateTimeSelected = true
                updateDateTimeText()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun updateDateTimeText() {
        if (dateTimeSelected) {
            selectedDateTimeText.text = Utilities.formatDate(calendar.time)
        } else {
            selectedDateTimeText.text = getString(R.string.no_date_selected)
        }
    }

    private fun saveTask() {
        val title = titleInput.text.toString().trim()
        val description = descriptionInput.text.toString().trim()

        if (title.isBlank()) {
            titleInput.error = getString(R.string.required_field)
            return
        }

        if (description.isBlank()) {
            descriptionInput.error = getString(R.string.required_field)
            return
        }

        if (!dateTimeSelected) {
            selectedDateTimeText.error = getString(R.string.required_field)
            return
        }

        lifecycleScope.launch {
            var task = Task(
                id = if (isEditMode) taskId else 0,
                title = title,
                description = description,
                deadline = calendar.time,
                isCompleted = false,
                createdAt = if (isEditMode) createdAt else Date()
            )

            val dao = TaskDatabase.getDatabase(this@AddTaskActivity).taskDao()
            if (isEditMode) {
                dao.updateTask(task)
            } else {
                val newId = dao.insertTask(task).toInt()
                task = task.copy(id = newId)
            }

            TaskReminderWorker.scheduleRemindersForTask(this@AddTaskActivity, task)

            finish()
        }
    }
}