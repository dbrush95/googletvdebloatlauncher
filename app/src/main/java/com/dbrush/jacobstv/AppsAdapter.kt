package com.dbrush.jacobstv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

class AppsAdapter(
    private val onOpen: (AppEntry) -> Unit,
    private val onHold: ((AppEntry) -> Unit)? = null,
) : BaseAdapter() {
    private val items = ArrayList<AppEntry>()

    fun setItems(next: List<AppEntry>) {
        items.clear()
        items.addAll(next)
        notifyDataSetChanged()
    }

    fun snapshot(): List<AppEntry> = items.toList()

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): AppEntry = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        val entry = items[position]
        view.findViewById<ImageView>(R.id.icon).setImageDrawable(entry.icon)
        view.findViewById<TextView>(R.id.label).text = entry.label
        view.setOnClickListener { onOpen(entry) }
        if (onHold != null) {
            view.isLongClickable = true
            view.setOnLongClickListener {
                onHold.invoke(entry)
                true
            }
        }
        return view
    }
}
