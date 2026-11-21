package com.palabrasfuncionales.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.palabrasfuncionales.app.R
import com.palabrasfuncionales.app.databinding.ItemLessonBinding
import com.palabrasfuncionales.app.learn.Lesson

data class LessonRow(val lesson: Lesson, val seen: Int, val mastered: Int, val isNext: Boolean)

class LessonAdapter(private val onClick: (Lesson) -> Unit) :
    ListAdapter<LessonRow, LessonAdapter.Holder>(DIFF) {

    class Holder(val b: ItemLessonBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemLessonBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = getItem(position)
        val l = row.lesson
        val ctx = holder.b.root.context
        with(holder.b) {
            title.text = l.section
            part.text = ctx.getString(R.string.lesson_part, l.part, l.parts, l.entries.size)
            preview.text = l.entries.take(4).joinToString("  ·  ") { it.word }
            progress.max = l.entries.size
            progress.setProgressCompat(row.seen, false)
            status.text = when {
                row.mastered == l.entries.size -> ctx.getString(R.string.lesson_mastered)
                row.seen == 0 -> ctx.getString(R.string.lesson_new)
                else -> ctx.getString(R.string.lesson_seen, row.seen, l.entries.size)
            }
            nextBadge.isVisible = row.isNext
            root.setOnClickListener { onClick(l) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<LessonRow>() {
            override fun areItemsTheSame(a: LessonRow, b: LessonRow) = a.lesson.key == b.lesson.key
            override fun areContentsTheSame(a: LessonRow, b: LessonRow) =
                a.seen == b.seen && a.mastered == b.mastered && a.isNext == b.isNext && a.lesson.key == b.lesson.key
        }
    }
}
