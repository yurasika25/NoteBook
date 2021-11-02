package com.notes.notebook.adapter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.notes.notebook.R
import com.notes.notebook.`fun`.appCompatActivity
import com.notes.notebook.db.ListItem
import com.notes.notebook.db.MyDbManager
import com.notes.notebook.db.MyIntentConstants.I_DESK_KEY
import com.notes.notebook.db.MyIntentConstants.I_ID_KEY
import com.notes.notebook.db.MyIntentConstants.I_TITLE_KEY
import com.notes.notebook.fragment.FragmentAddNote
import kotlinx.android.synthetic.main.rs_item.view.*
import java.util.*
import kotlin.collections.ArrayList

class NoteBookAdapter : RecyclerView.Adapter<NoteBookAdapter.MyHolder>() {
    private val listMain: ArrayList<ListItem> = ArrayList()

    class MyHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    override fun onBindViewHolder(holder: MyHolder, position: Int) {
        val itemList = listMain[position]
        holder.itemView.tvTitle.text = itemList.title
        holder.itemView.id_time.text = itemList.time

        holder.itemView.customContainer.setOnClickListener {

            val fragment: Fragment = FragmentAddNote()
            val bundle = Bundle()
            bundle.putString(I_TITLE_KEY, itemList.title)
            bundle.putString(I_DESK_KEY, itemList.desc)
            bundle.putInt(I_ID_KEY, itemList.id)
            fragment.arguments = bundle
            val fm = appCompatActivity.supportFragmentManager
            val ft = fm.beginTransaction()
            ft.replace(R.id.mainContainer, fragment)
            ft.addToBackStack(null)
            ft.commit()
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
        listMain.sortBy { it.time}
        listMain.addAll(listItems)
        notifyDataSetChanged()
    }

    fun removeItem(post: Int, dbManager: MyDbManager) {
        dbManager.removeItemFromDb(listMain[post].id.toString())
        listMain.removeAt(post)
        notifyItemRangeChanged(0, listMain.size)
        notifyItemRemoved(post)
    }
}
