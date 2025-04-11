package br.ufpe.cin.acs11.projetoif22

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.ufpe.cin.acs11.projetoif22.data.Task
import br.ufpe.cin.acs11.projetoif22.data.TaskDao
import br.ufpe.cin.acs11.projetoif22.data.TaskDatabase
import br.ufpe.cin.acs11.projetoif22.ui.AddTaskActivity
import br.ufpe.cin.acs11.projetoif22.ui.TaskAdapter
import br.ufpe.cin.acs11.projetoif22.ui.TaskListElement
import br.ufpe.cin.acs11.projetoif22.workers.TaskReminderWorker
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

enum class SortingMethod {
    DEADLINE,
    CREATION
}

class MainActivity : AppCompatActivity() {
    private lateinit var taskDao: TaskDao
    private lateinit var recyclerView: RecyclerView
    private var currentSortMethod: SortingMethod? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        loadThemePreference()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        taskDao = TaskDatabase.getDatabase(this).taskDao()
        recyclerView = findViewById(R.id.taskRecyclerView)

        setSupportActionBar(findViewById(R.id.toolbar))
        setupCreateTaskButton(findViewById(R.id.addTaskFab))
        setupRecyclerView()
        observeTasks()
        scheduleTaskReminders()
    }

    private fun setupCreateTaskButton(addTaskButton: FloatingActionButton) =
        addTaskButton.setOnClickListener {
            startActivity(Intent(this, AddTaskActivity::class.java))
        }

    private fun setupRecyclerView() =
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = makeNewAdapter(emptyList())
        }


    private fun observeTasks() = taskDao.getAllTasks().observe(this, ::updateTaskList)

    private fun performSearch(query: String) {
        val searchQuery = "%$query%".replace("TASK-", "")
        taskDao.searchTasks(searchQuery, searchQuery).observe(this, ::updateTaskList)
    }

    private fun applySorting(sortMethod: SortingMethod) = when (sortMethod) {
        SortingMethod.DEADLINE -> taskDao.getAllTasksByDeadline().observe(this, ::updateTaskList)
        SortingMethod.CREATION -> taskDao.getAllTasksByCreation().observe(this, ::updateTaskList)
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as androidx.appcompat.widget.SearchView

        setupSearchView(searchView)
        setupSearchItemExpandListener(searchItem)

        return true
    }

    private fun setupSearchView(searchView: androidx.appcompat.widget.SearchView) {
        searchView.queryHint = getString(R.string.search_hint)
        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.takeIf(String::isNotBlank)?.let(::performSearch)
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                if (!newText.isNullOrBlank() && newText.length >= 2) performSearch(newText)
                else currentSortMethod?.let(::applySorting) ?: observeTasks()
                return true
            }
        })
    }
    private fun setupSearchItemExpandListener(searchItem: MenuItem) {
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                currentSortMethod?.let { applySorting(it) } ?: observeTasks()
                return true
            }
        })
    }

    private fun updateTaskList(tasks: List<Task>) {
        val sectionsWithHeaders = tasks.groupBy { it.isCompleted }.toSortedMap().flatMap { (isCompleted, taskList) ->
            if (taskList.isNotEmpty()) {
                val header = if (isCompleted) getString(R.string.header_completed_tasks) else getString(R.string.header_pending_tasks)
                listOf(TaskListElement.Header(header)) + taskList.map { TaskListElement.TaskItem(it) }
            }
            else emptyList()
        }

        recyclerView.adapter = makeNewAdapter(sectionsWithHeaders)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        currentSortMethod = when (item.itemId) {
            R.id.sort_by_deadline -> SortingMethod.DEADLINE
            R.id.sort_by_creation -> SortingMethod.CREATION
            else -> null
        }
        return currentSortMethod?.let { applySorting(it); return true } ?: super.onOptionsItemSelected(item)
    }


    private fun makeNewAdapter(sections: List<TaskListElement>) = TaskAdapter(
        items = sections,
        onTaskDeleted = { task ->
            lifecycleScope.launch {
                taskDao.deleteTask(task)
                TaskReminderWorker.cancelReminderForTask(this@MainActivity, task.id)
            }
        },
        onTaskChecked = { task, isCompleted ->
            lifecycleScope.launch {
                val updatedTask = task.copy(isCompleted = isCompleted)
                taskDao.updateTask(updatedTask)

                if (isCompleted) TaskReminderWorker.cancelReminderForTask(this@MainActivity, task.id)
                else TaskReminderWorker.scheduleRemindersForTask(this@MainActivity, updatedTask)
            }
        }
    )

    private fun scheduleTaskReminders() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
        TaskReminderWorker.scheduleAllReminders(this)
    }

    private fun loadThemePreference() {
        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val savedNightMode = sharedPrefs.getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedNightMode)
    }


}