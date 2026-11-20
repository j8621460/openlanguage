package com.palabrasfuncionales.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.palabrasfuncionales.app.data.Entry
import com.palabrasfuncionales.app.databinding.ItemEntryBinding

class EntryAdapter(private val onClick: (Entry) -> Unit) :
    ListAdapter<Entry, EntryAdapter.Holder>(DIFF) {

    var showSection: Boolean = true

    class Holder(val b: ItemEntryBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val e = getItem(position)
        with(holder.b) {
            word.text = e.word
            translit.text = e.translit
            translit.isVisible = !e.translit.isNullOrBlank()
            meaning.text = e.spanish
            section.text = e.section
            section.isVisible = showSection
            hasNote.isInvisible = e.note.isNullOrBlank()
            root.setOnClickListener { onClick(e) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Entry>() {
            override fun areItemsTheSame(a: Entry, b: Entry) = a.id == b.id && a.word == b.word
            override fun areContentsTheSame(a: Entry, b: Entry) = a == b
        }
    }
}
