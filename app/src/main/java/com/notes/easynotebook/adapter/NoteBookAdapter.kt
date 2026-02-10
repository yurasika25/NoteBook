package com.notes.easynotebook.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.notes.easynotebook.databinding.RsItemBinding
import com.notes.easynotebook.db.DbListItemNoteBook
import com.notes.easynotebook.db.DbManagerNoteBook
import com.notes.easynotebook.core.time.DateFormatter.formatter
import java.util.*

class NoteBookAdapter(val onItemClicked: (DbListItemNoteBook) -> Unit) :
    RecyclerView.Adapter<NoteBookAdapter.MyHolder>() {
    private val listMainDbListItemNoteBook: ArrayList<DbListItemNoteBook> = ArrayList()


    inner class MyHolder(val viewBinding: RsItemBinding) : RecyclerView.ViewHolder(viewBinding.root)

    override fun onBindViewHolder(holder: MyHolder, position: Int) {
        val itemList = listMainDbListItemNoteBook[position]
        holder.viewBinding.tvTitle.text = itemList.title
        holder.viewBinding.idTime.text = formatter.format(itemList.time)

        holder.viewBinding.customContainer.setOnClickListener {
            onItemClicked(itemList)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyHolder {
        val viewBinding = RsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MyHolder(viewBinding)
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
