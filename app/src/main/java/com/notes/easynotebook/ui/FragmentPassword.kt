package com.notes.easynotebook.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    }

    private fun typePassword() {
        binding.apply {
            btn1.setOnClickListener { setTextField(getString(R.string._1)) }
            btn2.setOnClickListener { setTextField(getString(R.string._2)) }
            btn3.setOnClickListener { setTextField(getString(R.string._3)) }
            btn4.setOnClickListener { setTextField(getString(R.string._4)) }
            btn5.setOnClickListener { setTextField(getString(R.string._5)) }
            btn6.setOnClickListener { setTextField(getString(R.string._6)) }
            btn7.setOnClickListener { setTextField(getString(R.string._7)) }
            btn8.setOnClickListener { setTextField(getString(R.string._8)) }
            btn9.setOnClickListener { setTextField(getString(R.string._9)) }
            btn0.setOnClickListener { setTextField(getString(R.string._0)) }

            deleteBtn.setOnClickListener {
                val str = tvPassword.text.toString()
                if (str.isNotEmpty()) {
                    tvPassword.text = str.dropLast(1)
                }
            }

            tvPassword.doOnTextChanged { _, _, _, _ ->
                if (tvPassword.text.toString() == SharedPref.readPassword(requireContext())) {
                    (requireActivity() as MainActivity).goToMainFragment()
                } else if (tvPassword.text.length > 3 && tvPassword.text.toString() != SharedPref.readPassword(
                        requireContext()
                    )
                ) {
                    showShortToast(getString(R.string.incorrect_passcode))
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