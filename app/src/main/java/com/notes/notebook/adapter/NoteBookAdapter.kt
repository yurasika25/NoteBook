package com.notes.notebook.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.notes.notebook.R
import com.notes.notebook.db.ListItem
import com.notes.notebook.db.MyDbManager
import kotlinx.android.synthetic.main.rs_item.view.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class NoteBookAdapter(private val onItemClicked:(ListItem) -> Unit) : RecyclerView.Adapter<NoteBookAdapter.MyHolder>() {
    private val listMain: ArrayList<ListItem> = ArrayList()

    private val formatter = SimpleDateFormat("dd.MM.yy kk:mm", Locale.getDefault())

    class MyHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    override fun onBindViewHolder(holder: MyHolder, position: Int) {
        val itemList = listMain[position]
        holder.itemView.tvTitle.text = itemList.title
        holder.itemView.id_time.text = formatter.format(itemList.time)

        holder.itemView.customContainer.setOnClickListener {
            onItemClicked(itemList)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyHolder {
        val inflater = LayoutInflater.from(parent.context)
        return MyHolder(inflater.inflate(R.layout.rs_item, parent, false))
    }

    override fun getItemCount(): Int {
        return listMain.size
    }

    fun updateAdapter(listItems: List<ListItem>) {
        listMain.clear()
        listMain.addAll(listItems)
        notifyDataSetChanged()
    }

    fun removeItem(post: Int, dbManager: MyDbManager) {
        dbManager.removeItemFromDb(listMain[post].id)
        listMain.removeAt(post)
        notifyItemRemoved(post)
    }
}
