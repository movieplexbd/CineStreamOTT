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
import com.ottapp.moviestream.adapter.DownloadAdapter
import com.ottapp.moviestream.data.model.DownloadedMovie
import com.ottapp.moviestream.databinding.FragmentDownloadBinding
import com.ottapp.moviestream.ui.player.PlayerActivity
import com.ottapp.moviestream.util.Constants
import com.ottapp.moviestream.util.hide
import com.ottapp.moviestream.util.show

class DownloadFragment : Fragment() {

    private var _binding: FragmentDownloadBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DownloadViewModel by viewModels()
    private lateinit var adapter: DownloadAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDownloadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DownloadAdapter(
            onPlay   = { movie -> playOffline(movie) },
            onDelete = { movie -> confirmDelete(movie) }
        )
        binding.rvDownloads.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDownloads.adapter = adapter

        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadDownloads() // refresh on return
    }

    private fun observeViewModel() {
        viewModel.downloads.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            if (list.isEmpty()) {
                binding.layoutEmpty.show()
                binding.rvDownloads.hide()
                binding.cardStorage.hide()
            } else {
                binding.layoutEmpty.hide()
                binding.rvDownloads.show()
                binding.cardStorage.show()
            }
        }

        viewModel.storageUsed.observe(viewLifecycleOwner) { size ->
            binding.tvStorageUsed.text = "ব্যবহৃত জায়গা: $size"
        }
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
