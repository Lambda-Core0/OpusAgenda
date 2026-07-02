package com.lambda.opusagenda.ui

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.lambda.opusagenda.R
import com.lambda.opusagenda.databinding.ItemTaskBinding
import com.lambda.opusagenda.util.TaskAttachmentKind
import com.lambda.opusagenda.util.TaskContentSupport
import com.lambda.opusagenda.util.TaskDateFormatter
import com.lambda.opusagenda.viewmodel.TaskListItem

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
        val item = items.removeAt(fromPosition)
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

            val hasTags = item.tags.isNotEmpty()
            binding.viewTags.visibility = if (hasTags) View.VISIBLE else View.GONE
            if (hasTags) {
                binding.viewTags.setTags(item.tags)
                binding.viewTags.setOnClickListener {
                    actions.onShowTags(item)
                }
            } else {
                binding.viewTags.setTags(emptyList())
                binding.viewTags.setOnClickListener(null)
            }

            binding.textBadge.visibility = when {
                item.isCategory -> View.VISIBLE
                item.pinned -> View.VISIBLE
                else -> View.GONE
            }
            binding.textBadge.text = when {
                item.isCategory -> context.getString(R.string.category_badge_label)
                else -> context.getString(R.string.pinned_label)
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

            val hasDescription = !item.description.isNullOrBlank()
            val hasLink = !item.link.isNullOrBlank()
            val hasAttachment = !item.attachmentUri.isNullOrBlank()
            val attachmentKind = TaskContentSupport.classifyAttachment(
                item.attachmentMimeType,
                item.attachmentName
            )


            binding.buttonDescription.visibility = if (hasDescription) View.VISIBLE else View.GONE
            binding.buttonLink.visibility = if (hasLink) View.VISIBLE else View.GONE
            binding.buttonAttachment.visibility = if (hasAttachment) View.VISIBLE else View.GONE
            if (hasAttachment) {
                binding.buttonAttachment.setImageResource(attachmentIconFor(attachmentKind))
                binding.buttonAttachment.setColorFilter(
                    ContextCompat.getColor(
                        context,
                        when (attachmentKind) {
                            TaskAttachmentKind.IMAGE -> R.color.terminal_dark_green
                            TaskAttachmentKind.VIDEO -> R.color.terminal_dark_green
                            TaskAttachmentKind.AUDIO -> R.color.terminal_dark_green
                            TaskAttachmentKind.FILE -> R.color.terminal_dark_green
                        }
                    )
                )
            }

            binding.layoutContentActions.visibility = if (!hasDescription && !hasLink && !hasAttachment) {
                View.GONE
            } else {
                View.VISIBLE
            }

            binding.checkCompleted.setOnCheckedChangeListener { _, checked ->
                actions.onToggleCompleted(item, checked)
            }
            binding.actionPin.setOnClickListener { actions.onTogglePinned(item) }
            binding.actionEdit.setOnClickListener { actions.onEdit(item) }
            binding.actionNewCategorySecondary.setOnClickListener { actions.onCreateSubcategory(item) }
            binding.actionNewTaskSecondary.setOnClickListener { actions.onCreateChildTask(item) }
            binding.actionDelete.setOnClickListener { actions.onDelete(item) }
            binding.buttonDescription.setOnClickListener { actions.onShowDescription(item) }
            binding.buttonLink.setOnClickListener { actions.onShowLink(item) }
            binding.buttonAttachment.setOnClickListener { actions.onShowAttachment(item) }
            binding.actionNewCategory.visibility = View.GONE
            binding.actionNewTask.visibility = View.GONE
            binding.layoutCategoryActions.visibility = if (item.isCategory) View.VISIBLE else View.GONE
            binding.root.setOnClickListener {
                if (item.isCategory) {
                    actions.onToggleExpanded(item)
                }
                // Removed edit on click for tasks to prevent misclicks on action buttons
            }
        }

        private fun attachmentIconFor(kind: TaskAttachmentKind): Int {
            return when (kind) {
                TaskAttachmentKind.IMAGE -> R.drawable.img_btn
                TaskAttachmentKind.VIDEO -> R.drawable.video_btn
                TaskAttachmentKind.AUDIO -> R.drawable.audio_btn
                TaskAttachmentKind.FILE -> R.drawable.file_btn
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
        fun onShowDescription(item: TaskListItem)
        fun onShowLink(item: TaskListItem)
        fun onShowAttachment(item: TaskListItem)
        fun onShowTags(item: TaskListItem)
    }
}
