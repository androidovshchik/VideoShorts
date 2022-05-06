package com.automate123.videshorts.screen.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.automate123.videshorts.databinding.FragmentControlsBinding
import kotlinx.coroutines.launch

class ControlsFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var binding: FragmentControlsBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        binding = FragmentControlsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.fabForward.setOnClickListener {
            viewModel.controller.recordNext()
        }
        binding.ivRetry.setOnClickListener {
            viewModel.controller.recordAgain()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.preview.collect {
                binding.ivThumb.setImageBitmap(it)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.controller.recording.collect {
                if (it) {
                    binding.fabForward.isEnabled = false
                    binding.ivRetry.isEnabled = true
                } else {
                    binding.fabForward.isEnabled = true
                    binding.ivRetry.isEnabled = viewModel.controller.position > 0
                }
            }
        }
    }
}