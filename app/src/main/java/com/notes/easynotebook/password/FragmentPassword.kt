package com.notes.easynotebook.password

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import com.notes.easynotebook.R
import com.notes.easynotebook.base.BaseFragment
import com.notes.easynotebook.databinding.FrgPasswordBinding
import com.notes.easynotebook.db.SharedPref
import com.notes.easynotebook.main.MainActivity

class FragmentPassword : BaseFragment() {

    private var _binding: FrgPasswordBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FrgPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        typePassword()
        binding.icEditPassword.setOnClickListener {
            showPasswordDialog()
        }
    }

    private fun showPasswordDialog() {
        val dialogBuilder = AlertDialog.Builder(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.alert_password_dialog, null)
        val editTextPassword = dialogView.findViewById<EditText>(R.id.titlePasswordDialog)
        val btnSave = dialogView.findViewById<FrameLayout>(R.id.btnSavePassword)
        val btnCancel = dialogView.findViewById<FrameLayout>(R.id.btnCancelPassword)

        dialogBuilder.setView(dialogView)
        val alertDialog = dialogBuilder.create()
        alertDialog.show()
        alertDialog.setCanceledOnTouchOutside(false)

        btnSave.setOnClickListener {
            val textPassword: String = editTextPassword.text.toString()
            if (editTextPassword.text.isEmpty() || editTextPassword.text.length < 4) {
                showShortToast("Enter 4 digits")
                vibratePhone()
            } else {
                SharedPref.setPassword(requireContext(), textPassword)
                alertDialog.cancel()
                showShortToast("Password is saved")
            }
        }
        btnCancel.setOnClickListener {
            alertDialog.cancel()
        }
        editTextPassword.hint = "Create password"
    }

    private fun typePassword() {
        binding.apply {
            btn1.setOnClickListener { setTextField("1") }
            btn2.setOnClickListener { setTextField("2") }
            btn3.setOnClickListener { setTextField("3") }
            btn4.setOnClickListener { setTextField("4") }
            btn5.setOnClickListener { setTextField("5") }
            btn6.setOnClickListener { setTextField("6") }
            btn7.setOnClickListener { setTextField("7") }
            btn8.setOnClickListener { setTextField("8") }
            btn9.setOnClickListener { setTextField("9") }
            btn0.setOnClickListener { setTextField("0") }

            deleteBtn.setOnClickListener {
                val str = tvPassword.text.toString()
                if (str.isNotEmpty()) {
                    tvPassword.text = str.substring(0, str.length - 1)
                }
            }

            tvPassword.doOnTextChanged { _, _, _, _ ->
                if (tvPassword.text.toString() == SharedPref.readPassword(requireContext())) {
                    (requireActivity() as MainActivity).goToMainFragment()
                } else if (tvPassword.text.length > 3 && tvPassword.text.toString() != SharedPref.readPassword(requireContext())) {
                    showShortToast("Password is incorrect!")
                    vibratePhone()

                }
            }
        }
    }

    private fun setTextField(text: String) {
        binding.apply {
            if (binding.tvPassword.text != "") tvPassword.text = tvPassword.text
            tvPassword.append(text)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}