package com.automate123.videshorts.screen.main

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.work.await
import com.automate123.videshorts.databinding.FragmentCameraBinding
import com.automate123.videshorts.extension.isGranted
import com.automate123.videshorts.service.PermProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class CameraFragment : Fragment() {

    @Inject
    lateinit var permProvider: PermProvider

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var binding: FragmentCameraBinding

    private lateinit var cameraProvider: ProcessCameraProvider

    private lateinit var videoCapture: VideoCapture<Recorder>

    private var recording: Recording? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            permProvider.grantedPerms
                .filter { it.contains(Manifest.permission.CAMERA) }
                .collect {
                    if (!::cameraProvider.isInitialized) {
                        cameraProvider = ProcessCameraProvider.getInstance(requireContext())
                            .await()
                        startCamera()
                    }
                }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.controller.recordFile
                .collect {
                    if (it != null) {
                        startRecording(it)
                    } else {
                        stopRecording()
                    }
                }
        }
    }

    private fun startCamera() {
        cameraProvider.unbindAll()

        val preview = Preview.Builder()
            .build()
        preview.setSurfaceProvider(binding.preview.surfaceProvider)

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        try {
            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                videoCapture
            )
            viewModel.controller.isCameraBound = true
        } catch (e: Throwable) {
            Timber.e(e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRecording(file: File) {
        val context = requireContext()
        stopRecording()
        recording = videoCapture.output
            .prepareRecording(context, FileOutputOptions.Builder(file)
                .build())
            .apply {
                if (context.isGranted(Manifest.permission.RECORD_AUDIO)) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(context)) {
                viewModel.controller.onRecordEvent(it)
            }
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
    }

    override fun onStop() {
        viewModel.controller.stopRecord()
        super.onStop()
    }

    override fun onDestroyView() {
        if (::cameraProvider.isInitialized) {
            cameraProvider.unbindAll()
        }
        super.onDestroyView()
    }
}