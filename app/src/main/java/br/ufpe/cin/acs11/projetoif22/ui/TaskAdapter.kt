package br.ufpe.cin.acs11.projetoif22.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import br.ufpe.cin.acs11.projetoif22.R
import br.ufpe.cin.acs11.projetoif22.Utilities
import br.ufpe.cin.acs11.projetoif22.data.Task
import java.util.Calendar
import java.util.Locale

sealed class TaskListElement {
    data class Header(val title: String) : TaskListElement()
    data class TaskItem(val task: Task) : TaskListElement()
}

class TaskAdapter(
    private val items: List<TaskListElement>,
    private val onTaskDeleted: (Task) -> Unit,
    private val onTaskChecked: (Task, Boolean) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_TASK = 0
        private const val VIEW_TYPE_HEADER = 1
    }

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val taskId: TextView = itemView.findViewById(R.id.taskId)
        val taskTitle: TextView = itemView.findViewById(R.id.taskTitle)
        val taskDescription: TextView = itemView.findViewById(R.id.taskDescription)
        val taskDeadline: TextView = itemView.findViewById(R.id.taskDeadline)
        val statusBadge: TextView = itemView.findViewById(R.id.statusBadge)
        val editButton: ImageButton = itemView.findViewById(R.id.editButton)
        val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
    }

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val headerText: TextView = itemView as TextView
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is TaskListElement.Header -> VIEW_TYPE_HEADER
            is TaskListElement.TaskItem -> VIEW_TYPE_TASK
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_TASK -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.task_list_item, parent, false)
                TaskViewHolder(view)
            }
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.section_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> throw IllegalArgumentException()
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is TaskViewHolder -> {
                val task = items[position] as TaskListElement.TaskItem
                bindTaskViewHolder(holder, task)
            }
            is HeaderViewHolder -> {
                val header = items[position] as TaskListElement.Header
                bindHeaderViewHolder(holder, header)
            }
        }
    }

    private fun bindHeaderViewHolder(holder: HeaderViewHolder, header: TaskListElement.Header) {
        holder.headerText.text = header.title
    }

    private fun bindTaskViewHolder(holder: TaskViewHolder, taskElement: TaskListElement.TaskItem) {
        val task = taskElement.task;

        holder.taskId.text = String.format(Locale.getDefault(),"TASK-%03d", task.id)
        holder.taskTitle.text = task.title

        if (task.isCompleted) {
            holder.taskTitle.paintFlags = holder.taskTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            holder.taskDescription.paintFlags = holder.taskDescription.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            holder.editButton.isEnabled = false;
        } else {
            holder.taskTitle.paintFlags = holder.taskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.taskDescription.paintFlags = holder.taskDescription.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.editButton.isEnabled = true;
        }

        holder.taskDescription.text = task.description
        holder.taskDeadline.text = holder.itemView.context.getString(R.string.deadline, Utilities.formatDate(task.deadline))

        updateStatusBadge(holder, task)

        holder.editButton.setOnClickListener { editTask(holder.itemView.context, task) }
        holder.deleteButton.setOnClickListener { showDeleteConfirmationDialog(holder.itemView.context, task) }
        holder.itemView.setOnClickListener { onTaskChecked(task, !task.isCompleted) }
        holder.itemView.setOnClickListener { onTaskChecked(task, !task.isCompleted) }
    }

    private fun updateStatusBadge(holder: TaskViewHolder, task: Task) {
        val context = holder.itemView.context
        val currentTime = Calendar.getInstance().time
        val statusBadge = holder.statusBadge

        if (task.isCompleted) {
            statusBadge.text = context.getString(R.string.task_completed_badge_text)
            statusBadge.setBackgroundResource(R.drawable.badge_background_completed)
            return
        }

        val currentDate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val taskDate = Calendar.getInstance().apply {
            time = task.deadline
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when {
            task.deadline.before(currentTime) -> {
                statusBadge.text = context.getString(R.string.status_overdue)
                statusBadge.setBackgroundResource(R.drawable.badge_background_overdue)
            }
            taskDate.get(Calendar.DAY_OF_YEAR) == currentDate.get(Calendar.DAY_OF_YEAR) &&
                    taskDate.get(Calendar.YEAR) == currentDate.get(Calendar.YEAR) -> {
                statusBadge.text = context.getString(R.string.status_today)
                statusBadge.setBackgroundResource(R.drawable.badge_background_today)
            }
            else -> {
                statusBadge.text = context.getString(R.string.status_on_time)
                statusBadge.setBackgroundResource(R.drawable.badge_background)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    private fun editTask(context: Context, task: Task) {
        val intent = Intent(context, AddTaskActivity::class.java).apply {
            putExtra("task_id", task.id)
            putExtra("task_title", task.title)
            putExtra("task_description", task.description)
            putExtra("task_deadline", task.deadline.time)
            putExtra("task_created_at", task.createdAt.time)
            putExtra("task_completed", task.isCompleted)
        }
        context.startActivity(intent)
    }

    private fun showDeleteConfirmationDialog(context: Context, task: Task) {
        AlertDialog.Builder(context)
            .setTitle(R.string.delete_task)
            .setMessage(R.string.delete_task_confirmation)
            .setPositiveButton(R.string.yes) { _, _ -> onTaskDeleted(task) }
            .setNegativeButton(R.string.no, null)
            .show()
    }
}