package com.example.notebook

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.example.notebook.db.MyDbManger
import com.example.notebook.db.MyIntentConstants
import kotlinx.android.synthetic.main.activity_edit.*
import java.text.SimpleDateFormat
import java.util.*

class EditActivity : AppCompatActivity() {

    var id = 0
    var isEditState = false
    val myDbManger = MyDbManger(this)
    var tempImageUri = "empty"
    val imageRequestCode = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)
        getMyIntents()
    }

    override fun onDestroy() {
        super.onDestroy()
        myDbManger.closeDb()
    }

    override fun onResume() {
        super.onResume()
        myDbManger.openDb()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == imageRequestCode) {

            tempImageUri = data?.data.toString()
            contentResolver.takePersistableUriPermission(
                data?.data!!,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }


    fun onClickSave(view: View) {
        val myTitle = editTitle.text.toString()
        val myDesk = editText.text.toString()

        if (myTitle != "" && myDesk != "") {
            if (isEditState) {
                myDbManger.updateItem(myTitle, myDesk, id, getTime())
            } else {
                myDbManger.insertToDb(myTitle, myDesk, getTime())
            }

            finish()
        }
    }


    fun onClickWriteText(view: View) {
        editTitle.isEnabled = true
        editText.isEnabled = true

        floatBtnSave.visibility = View.VISIBLE


        bt_text_write.visibility = View.GONE

        if (tempImageUri == "empty") return

    }

    fun getMyIntents() {
        bt_text_write.visibility = View.GONE
        val i = intent

        if (i != null) {

            if (i.getStringExtra(MyIntentConstants.I_TITLE_KEY) != null) {

                floatBtnSave.visibility = View.GONE
                editTitle.setText(i.getStringExtra(MyIntentConstants.I_TITLE_KEY))
                isEditState = true
                editTitle.isEnabled = false
                editText.isEnabled = false
                bt_text_write.visibility = View.VISIBLE
                editText.setText(i.getStringExtra(MyIntentConstants.I_DESK_KEY))
                id = i.getIntExtra(MyIntentConstants.I_ID_KEY, 0)
            }
        }
    }
}

private fun getTime(): String {
    val time = Calendar.getInstance().time
    val formatter = SimpleDateFormat("dd.MM.yy kk:mm", Locale.getDefault())
    return formatter.format(time)
}
