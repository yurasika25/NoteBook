package com.notes.easynotebook.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.coroutineScope
import com.notes.easynotebook.R
import com.notes.easynotebook.base.BaseFragment
import com.notes.easynotebook.databinding.FrgAddPasswordBinding
import com.notes.easynotebook.db.SharedPref
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FragmentAddPassword : BaseFragment() {

    private var shake: Animation? = null

    private var _binding: FrgAddPasswordBinding? = null
    private val binding get() = _binding!!

    private var isFirstAttempt = true

    private val viewModel by lazy { PasswordViewModel() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FrgAddPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            showKeyBoard()
            etOtp1.requestFocus()
            focusNextEdit(etOtp1, etOtp2)
            focusNextEdit(etOtp2, etOtp3)
            focusNextEdit(etOtp3, etOtp4)
            focusNextEdit(etOtp4, null)

            etOtp1.setOnKeyListener(GenericKeyEvent(etOtp1, null))
            etOtp2.setOnKeyListener(GenericKeyEvent(etOtp2, etOtp1))
            etOtp3.setOnKeyListener(GenericKeyEvent(etOtp3, etOtp2))
            etOtp4.setOnKeyListener(GenericKeyEvent(etOtp4, etOtp3))

            etOtp1.isCursorVisible = false
            etOtp2.isCursorVisible = false
            etOtp3.isCursorVisible = false
            etOtp4.isCursorVisible = false

            etOtp4.doOnTextChanged { _, _, _, _ ->
                if (etOtp4.text?.isNotEmpty() == true) {
                    passwordCode()
                    isFirstAttempt = false
                }
            }

            if (SharedPref.readPassword(requireContext())?.isNotEmpty() == true) {
                tvTitlePassword.text = getString(R.string.add_new_passcode)
            }

            tbMain.setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    class GenericKeyEvent internal constructor(
        private val currentView: EditText?,
        private val previousView: EditText?
    ) : View.OnKeyListener {
        override fun onKey(p0: View?, keyCode: Int, event: KeyEvent?): Boolean {
            if (event!!.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DEL && currentView?.id != R.id.et_otp_1 && currentView?.text!!.isEmpty()) {
                previousView!!.text = null
                previousView.requestFocus()
                return true
            }
            return false
        }
    }

    private fun focusNextEdit(et: EditText, etFocus: EditText?) {
        et.doOnTextChanged { _, _, _, _ ->
            if (et.text.isNotEmpty()) {
                etFocus?.requestFocus()
                binding.tvNotEqualsPassword.isVisible = false
            }
        }
    }

    private fun equalsPassword() {
        binding.apply {
            if (viewModel.password == viewModel.passwordRepeat) {
                SharedPref.setPassword(requireContext(), viewModel.password)

                lifecycle.coroutineScope.launch { Dispatchers.IO
                    setBackgroundInput(etOtp1, R.drawable.password_input_correct)
                }
                lifecycle.coroutineScope.launch { Dispatchers.IO
                    delay(60)
                    setBackgroundInput(etOtp2, R.drawable.password_input_correct)
                }
                lifecycle.coroutineScope.launch { Dispatchers.IO
                    delay(120)
                    setBackgroundInput(etOtp3, R.drawable.password_input_correct)
                }
                lifecycle.coroutineScope.launch { Dispatchers.IO
                    delay(180)
                    setBackgroundInput(etOtp4, R.drawable.password_input_correct)
                }

                lifecycle.coroutineScope.launch {
                    Dispatchers.IO
                    delay(550)
                    hideKeyBoard()
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            } else {
                anim()
                vibratePhone(20)
                setBackgroundInput(etOtp1, R.drawable.password_input_error)
                setBackgroundInput(etOtp2, R.drawable.password_input_error)
                setBackgroundInput(etOtp3, R.drawable.password_input_error)
                setBackgroundInput(etOtp4, R.drawable.password_input_error)
                binding.tvNotEqualsPassword.isVisible = true
                clearPassword()
                binding.etOtp1.requestFocus()
                lifecycle.coroutineScope.launch {
                    Dispatchers.IO
                    delay(4000)
                    binding.tvNotEqualsPassword.isVisible = false
                }
                lifecycle.coroutineScope.launch {
                    Dispatchers.IO
                    delay(1000)
                    setBackgroundInput(etOtp1, R.drawable.password_input)
                    setBackgroundInput(etOtp2, R.drawable.password_input)
                    setBackgroundInput(etOtp3, R.drawable.password_input)
                    setBackgroundInput(etOtp4, R.drawable.password_input)
                }
            }
        }
    }

    private fun anim() {
        shake = AnimationUtils.loadAnimation(requireContext(), R.anim.shake)
        binding.llPasscode.animation = shake
    }

    private fun setBackgroundInput(et: EditText, background: Int) {
        et.setBackgroundResource(background)
    }

    private fun passwordCode() {
        binding.apply {
            if (isFirstAttempt) {
                viewModel.password = enterPassword()
                lifecycle.coroutineScope.launch {
                    Dispatchers.IO
                    delay(300)
                    tvTitlePassword.text = getString(R.string.re_enter_passcode)
                    titleSubPassword.text = getString(R.string.message_reinstall_app)
                    etOtp1.requestFocus()
                    clearPassword()
                }
            } else {
                viewModel.passwordRepeat = enterPassword()
                equalsPassword()
            }
        }
    }

    private fun clearPassword() {
        binding.apply {
            etOtp1.text = null
            etOtp2.text = null
            etOtp3.text = null
            etOtp4.text = null
        }
    }

    override fun onPause() {
        super.onPause()
        hideKeyBoard()
    }

    private fun FrgAddPasswordBinding.enterPassword() =
        etOtp1.text.toString() + etOtp2.text.toString() + etOtp3.text.toString() + etOtp4.text.toString()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}