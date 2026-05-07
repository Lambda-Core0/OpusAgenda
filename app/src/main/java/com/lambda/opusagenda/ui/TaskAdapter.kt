package com.lambda.opusagenda.ui

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.lambda.opusagenda.R
import com.lambda.opusagenda.databinding.ItemTaskBinding
import com.lambda.opusagenda.util.TaskDateFormatter
import com.lambda.opusagenda.viewmodel.TaskListItem
import java.util.Collections

/**
 * Adaptador jerarquico principal de tareas y categorias.
 */
class TaskAdapter(
    private val actions: TaskItemActions
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private val items = mutableListOf<TaskListItem>()

    init {
        setHasStableIds(true)
    }

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long = items[position].id.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(items[position])
    }

    fun submitItems(newItems: List<TaskListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun previewMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition !in items.indices || toPosition !in items.indices) return
        // Perform a move (remove + insert) so the adapter snapshot represents the dragged
        // item being inserted at the target position instead of a simple swap.
        val item = items.removeAt(fromPosition)
        // If removing an earlier index, the target index shifts by -1 when inserting.
        val insertIndex = if (fromPosition < toPosition) toPosition else toPosition
        items.add(insertIndex, item)
        notifyItemMoved(fromPosition, insertIndex)
    }

    fun getItemAt(position: Int): TaskListItem? = items.getOrNull(position)

    fun snapshot(): List<TaskListItem> = items.toList()

    inner class TaskViewHolder(
        private val binding: ItemTaskBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val basePaddingStart = binding.root.paddingStart
        private val basePaddingTop = binding.root.paddingTop
        private val basePaddingEnd = binding.root.paddingEnd
        private val basePaddingBottom = binding.root.paddingBottom

        fun bind(item: TaskListItem) {
            val context = binding.root.context
            val dueDelta = TaskDateFormatter.daysUntil(item.dueDate)
            val metaColor = when {
                item.isCategory -> R.color.terminal_text_muted
                item.completed -> R.color.terminal_text_muted
                dueDelta != null && dueDelta < 0 -> R.color.terminal_red
                dueDelta != null && dueDelta <= 1 -> R.color.terminal_yellow
                else -> R.color.terminal_text_muted
            }

            val indentPx = (item.depth * 18 * context.resources.displayMetrics.density).toInt()
            binding.root.setPadding(
                basePaddingStart + indentPx,
                basePaddingTop,
                basePaddingEnd,
                basePaddingBottom
            )

            binding.checkCompleted.setOnCheckedChangeListener(null)
            binding.checkCompleted.visibility = if (item.isCategory) View.GONE else View.VISIBLE
            binding.checkCompleted.isChecked = item.completed

            binding.textExpandIndicator.visibility = if (item.isCategory) View.VISIBLE else View.GONE
            binding.textExpandIndicator.text = if (item.expanded) "[-]" else "[+]"
            binding.textTask.text = item.text
            binding.textMeta.text = item.metaLine
            binding.textMeta.setTextColor(ContextCompat.getColor(context, metaColor))

            binding.textBadge.visibility = when {
                item.isCategory -> View.VISIBLE
                item.pinned -> View.VISIBLE
                else -> View.GONE
            }
            binding.textBadge.text = when {
                item.isCategory -> context.getString(R.string.category_badge_label)
                else -> context.getString(R.string.task_pin_label)
            }
            binding.textBadge.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (item.isCategory) R.color.terminal_green else R.color.terminal_yellow
                )
            )

            val flags = if (!item.isCategory && item.completed) {
                binding.textTask.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.textTask.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            binding.textTask.paintFlags = flags
            binding.root.alpha = if (!item.isCategory && item.completed) 0.55f else 1f

            binding.actionPin.visibility = if (item.isCategory) View.GONE else View.VISIBLE
            binding.actionEdit.text = context.getString(
                if (item.isCategory) R.string.category_edit_label else R.string.task_edit_label
            )

            binding.checkCompleted.setOnCheckedChangeListener { _, checked ->
                actions.onToggleCompleted(item, checked)
            }
            binding.actionPin.setOnClickListener { actions.onTogglePinned(item) }
            binding.actionEdit.setOnClickListener { actions.onEdit(item) }
            binding.actionNewCategory.setOnClickListener { actions.onCreateSubcategory(item) }
            binding.actionNewTask.setOnClickListener { actions.onCreateChildTask(item) }
            binding.actionDelete.setOnClickListener { actions.onDelete(item) }
            binding.actionNewCategory.visibility = if (item.isCategory) View.VISIBLE else View.GONE
            binding.actionNewTask.visibility = if (item.isCategory) View.VISIBLE else View.GONE
            binding.root.setOnClickListener {
                if (item.isCategory) {
                    actions.onToggleExpanded(item)
                } else {
                    actions.onEdit(item)
                }
            }
        }
    }

    interface TaskItemActions {
        fun onToggleCompleted(item: TaskListItem, completed: Boolean)
        fun onTogglePinned(item: TaskListItem)
        fun onEdit(item: TaskListItem)
        fun onDelete(item: TaskListItem)
        fun onToggleExpanded(item: TaskListItem)
        fun onCreateSubcategory(item: TaskListItem)
        fun onCreateChildTask(item: TaskListItem)
    }
}
