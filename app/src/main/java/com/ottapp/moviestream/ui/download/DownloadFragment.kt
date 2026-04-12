package com.ottapp.moviestream.ui.download

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.util.Log
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
import com.ottapp.moviestream.util.toReadableSize

class DownloadFragment : Fragment() {

    companion object {
        private const val TAG = "DownloadFragment"
    }

    private var _binding: FragmentDownloadBinding? = null
    private val binding get() = _binding
    private val viewModel: DownloadViewModel by viewModels()

    private var activeAdapter: ActiveDownloadAdapter? = null
    private var downloadAdapter: DownloadAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return try {
            _binding = FragmentDownloadBinding.inflate(inflater, container, false)
            _binding?.root
        } catch (e: Exception) {
            Log.e(TAG, "Inflate error: ${e.message}", e)
            null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (_binding == null) return

        try {
            setupAdapters()
            observeViewModel()
            updateFreeStorage()
        } catch (e: Exception) {
            Log.e(TAG, "onViewCreated error: ${e.message}", e)
        }
    }

    private fun setupAdapters() {
        val ctx = context ?: return
        activeAdapter = ActiveDownloadAdapter { movieId -> cancelDownload(movieId) }
        binding?.rvActive?.apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = activeAdapter
            isNestedScrollingEnabled = false
        }

        downloadAdapter = DownloadAdapter(
            onPlay   = { movie -> playOffline(movie) },
            onDelete = { movie -> confirmDelete(movie) }
        )
        binding?.rvDownloads?.apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = downloadAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun observeViewModel() {
        viewModel.activeDownloads.observe(viewLifecycleOwner) { active ->
            val b = binding ?: return@observe
            try {
                if (active.isNotEmpty()) {
                    activeAdapter?.submitList(active.values.toList())
                    b.sectionActive.show()
                } else {
                    b.sectionActive.hide()
                }
                refreshEmpty()
            } catch (e: Exception) {
                Log.e(TAG, "Active downloads observer error: ${e.message}")
            }
        }

        viewModel.downloads.observe(viewLifecycleOwner) { downloads ->
            val b = binding ?: return@observe
            try {
                downloadAdapter?.submitList(downloads)
                if (downloads.isNotEmpty()) {
                    b.tvCompletedLabel.text = "সংরক্ষিত মুভি (${downloads.size}টি)"
                    b.sectionCompletedHeader.show()
                    b.cardStorage.show()
                    b.tvDownloadCount.text = downloads.size.toString()
                } else {
                    b.sectionCompletedHeader.hide()
                    b.cardStorage.visibility = View.GONE
                }
                refreshEmpty()
            } catch (e: Exception) {
                Log.e(TAG, "Downloads observer error: ${e.message}")
            }
        }

        viewModel.storageUsed.observe(viewLifecycleOwner) { size ->
            val b = binding ?: return@observe
            b.tvStorageUsed.text = size
            b.cardStorage.show()
        }
    }

    private fun updateFreeStorage() {
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val free = stat.availableBlocksLong * stat.blockSizeLong
            binding?.tvStorageFree?.text = free.toReadableSize()
        } catch (e: Exception) {
            binding?.tvStorageFree?.text = "N/A"
        }
    }

    private fun refreshEmpty() {
        val b = binding ?: return
        val hasActive    = viewModel.activeDownloads.value?.isNotEmpty() == true
        val hasCompleted = viewModel.downloads.value?.isNotEmpty() == true
        if (hasActive || hasCompleted) b.layoutEmpty.hide() else b.layoutEmpty.show()
    }

    private fun cancelDownload(movieId: String) {
        try {
            val ctx = context ?: return
            val intent = Intent(ctx, DownloadService::class.java).apply {
                action = DownloadService.ACTION_CANCEL
                putExtra(DownloadService.EXTRA_MOVIE_ID_CANCEL, movieId)
            }
            ctx.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "cancelDownload error: ${e.message}")
        }
    }

    private fun playOffline(movie: DownloadedMovie) {
        try {
            val ctx = context ?: return
            val intent = Intent(ctx, PlayerActivity::class.java).apply {
                putExtra(Constants.EXTRA_MOVIE_ID,    movie.movieId)
                putExtra(Constants.EXTRA_MOVIE_TITLE, movie.title)
                putExtra(Constants.EXTRA_VIDEO_URL,   movie.localFilePath)
                putExtra(Constants.EXTRA_BANNER_URL,  movie.bannerImageUrl)
                putExtra(Constants.EXTRA_IS_LOCAL,    true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "playOffline error: ${e.message}")
        }
    }

    private fun confirmDelete(movie: DownloadedMovie) {
        try {
            AlertDialog.Builder(requireContext())
                .setTitle("ডিলিট করবেন?")
                .setMessage("\"${movie.title}\" ডাউনলোড মুছে ফেলতে চান?")
                .setPositiveButton("ডিলিট") { _, _ ->
                    viewModel.deleteDownload(movie.movieId)
                    updateFreeStorage()
                }
                .setNegativeButton("বাতিল", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "confirmDelete error: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            viewModel.loadDownloads()
            updateFreeStorage()
        } catch (e: Exception) {
            Log.e(TAG, "onResume error: ${e.message}")
        }
    }

    override fun onDestroyView() {
        activeAdapter = null
        downloadAdapter = null
        _binding = null
        super.onDestroyView()
    }
}
