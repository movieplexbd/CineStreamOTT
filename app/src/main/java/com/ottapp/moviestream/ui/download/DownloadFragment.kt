package com.ottapp.moviestream.ui.download

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.ottapp.moviestream.adapter.ActiveDownloadAdapter
import com.ottapp.moviestream.adapter.DownloadAdapter
import com.ottapp.moviestream.data.model.DownloadedMovie
import com.ottapp.moviestream.databinding.FragmentDownloadBinding
import com.ottapp.moviestream.service.DownloadService
import com.ottapp.moviestream.ui.player.PlayerActivity
import com.ottapp.moviestream.util.Constants
import com.ottapp.moviestream.util.hide
import com.ottapp.moviestream.util.show

class DownloadFragment : Fragment() {

    private var _binding: FragmentDownloadBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DownloadViewModel by viewModels()
    private lateinit var completedAdapter: DownloadAdapter
    private lateinit var activeAdapter: ActiveDownloadAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDownloadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        completedAdapter = DownloadAdapter(
            onPlay   = { movie -> playOffline(movie) },
            onDelete = { movie -> confirmDelete(movie) }
        )
        binding.rvDownloads.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDownloads.adapter = completedAdapter

        activeAdapter = ActiveDownloadAdapter(onCancel = { movieId -> cancelDownload(movieId) })
        binding.rvActive.layoutManager = LinearLayoutManager(requireContext())
        binding.rvActive.adapter = activeAdapter

        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadDownloads()
    }

    private fun observeViewModel() {
        viewModel.activeDownloads.observe(viewLifecycleOwner) { map ->
            val list = map.values.toList()
            activeAdapter.submitList(list)
            if (list.isNotEmpty()) binding.sectionActive.show() else binding.sectionActive.hide()
            refreshEmpty()
        }

        viewModel.downloads.observe(viewLifecycleOwner) { list ->
            completedAdapter.submitList(list)
            if (list.isNotEmpty()) {
                binding.tvCompletedLabel.show()
                binding.rvDownloads.show()
                binding.cardStorage.show()
            } else {
                binding.tvCompletedLabel.hide()
                binding.rvDownloads.hide()
                binding.cardStorage.hide()
            }
            refreshEmpty()
        }

        viewModel.storageUsed.observe(viewLifecycleOwner) { size ->
            binding.tvStorageUsed.text = "ব্যবহৃত জায়গা: $size"
        }
    }

    private fun refreshEmpty() {
        val hasActive    = viewModel.activeDownloads.value?.isNotEmpty() == true
        val hasCompleted = viewModel.downloads.value?.isNotEmpty() == true
        if (hasActive || hasCompleted) binding.layoutEmpty.hide() else binding.layoutEmpty.show()
    }

    private fun cancelDownload(movieId: String) {
        val intent = Intent(requireContext(), DownloadService::class.java).apply {
            action = DownloadService.ACTION_CANCEL
            putExtra(DownloadService.EXTRA_MOVIE_ID_CANCEL, movieId)
        }
        requireContext().startService(intent)
    }

    private fun playOffline(movie: DownloadedMovie) {
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(Constants.EXTRA_MOVIE_ID,    movie.movieId)
            putExtra(Constants.EXTRA_MOVIE_TITLE, movie.title)
            putExtra(Constants.EXTRA_VIDEO_URL,   movie.localFilePath)
            putExtra(Constants.EXTRA_BANNER_URL,  movie.bannerImageUrl)
            putExtra(Constants.EXTRA_IS_LOCAL,    true)
        }
        startActivity(intent)
    }

    private fun confirmDelete(movie: DownloadedMovie) {
        AlertDialog.Builder(requireContext())
            .setTitle("ডিলিট করবেন?")
            .setMessage("\"${movie.title}\" ডাউনলোড মুছে ফেলতে চান?")
            .setPositiveButton("ডিলিট") { _, _ -> viewModel.deleteDownload(movie.movieId) }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
