package com.notes.easynotebook.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.notes.easynotebook.R
import com.notes.easynotebook.db.DbListItemNoteBook
import com.notes.easynotebook.db.DbManagerNoteBook
import kotlinx.android.synthetic.main.rs_item.view.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class NoteBookAdapter(private val onItemClicked: (DbListItemNoteBook) -> Unit) :
    RecyclerView.Adapter<NoteBookAdapter.MyHolder>() {
    private val listMainDbListItemNoteBook: ArrayList<DbListItemNoteBook> = ArrayList()

    private val formatter = SimpleDateFormat("dd.MM.yy kk:mm", Locale.getDefault())

    class MyHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    override fun onBindViewHolder(holder: MyHolder, position: Int) {
        val itemList = listMainDbListItemNoteBook[position]
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
        return listMainDbListItemNoteBook.size
    }

    fun updateAdapter(dbListItemNoteBooks: List<DbListItemNoteBook>) {
        listMainDbListItemNoteBook.clear()
        listMainDbListItemNoteBook.addAll(dbListItemNoteBooks)
        notifyDataSetChanged()
    }

    fun removeItem(post: Int, dbManagerNoteBook: DbManagerNoteBook) {
        dbManagerNoteBook.removeItemFromDb(listMainDbListItemNoteBook[post].id)
        listMainDbListItemNoteBook.removeAt(post)
        notifyItemRemoved(post)
    }
}
