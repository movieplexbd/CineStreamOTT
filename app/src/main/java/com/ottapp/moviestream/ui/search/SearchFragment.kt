package com.ottapp.moviestream.ui.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.ottapp.moviestream.R
import com.ottapp.moviestream.adapter.MovieGridAdapter
import com.ottapp.moviestream.data.model.Movie
import com.ottapp.moviestream.databinding.FragmentSearchBinding
import com.ottapp.moviestream.util.Constants
import com.ottapp.moviestream.util.hide
import com.ottapp.moviestream.util.show

class SearchFragment : Fragment() {

    companion object {
        private const val TAG = "SearchFragment"
    }

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding
    private val viewModel: SearchViewModel by viewModels()
    private var adapter: MovieGridAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return try {
            _binding = FragmentSearchBinding.inflate(inflater, container, false)
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
            val ctx = context ?: return
            adapter = MovieGridAdapter { openDetail(it) }
            binding?.rvResults?.layoutManager = GridLayoutManager(ctx, 3)
            binding?.rvResults?.adapter = adapter

            setupSearch()
            setupFilters()
            observeViewModel()
        } catch (e: Exception) {
            Log.e(TAG, "onViewCreated error: ${e.message}", e)
        }
    }

    private fun setupSearch() {
        binding?.etSearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                try {
                    viewModel.search(s.toString())
                    binding?.ivClear?.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                } catch (e: Exception) {
                    Log.e(TAG, "Search text changed error: ${e.message}")
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding?.ivClear?.setOnClickListener {
            binding?.etSearch?.setText("")
            adapter?.submitList(emptyList())
        }
    }

    private fun setupFilters() {
        val b = binding ?: return
        val filters = listOf(
            b.chipAll      to Constants.CAT_ALL,
            b.chipBangla   to Constants.CAT_BANGLA,
            b.chipHindi    to Constants.CAT_HINDI,
            b.chipTrending to Constants.CAT_TRENDING
        )
        filters.forEach { (chip, cat) ->
            chip.setOnClickListener {
                filters.forEach { (c, _) -> c.isSelected = false }
                chip.isSelected = true
                viewModel.setFilter(cat)
            }
        }
        b.chipAll.isSelected = true
    }

    private fun observeViewModel() {
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            val b = binding ?: return@observe
            if (loading) b.progressSearch.show() else b.progressSearch.hide()
        }

        viewModel.results.observe(viewLifecycleOwner) { movies ->
            val b = binding ?: return@observe
            adapter?.submitList(movies)
            try {
                val query = b.etSearch.text.toString()
                when {
                    query.isBlank()   -> { b.layoutEmpty.hide(); b.layoutSearchHint.show() }
                    movies.isEmpty()  -> { b.layoutEmpty.show(); b.layoutSearchHint.hide() }
                    else              -> { b.layoutEmpty.hide(); b.layoutSearchHint.hide() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Results observer error: ${e.message}")
            }
        }
    }

    private fun openDetail(movie: Movie) {
        if (!isAdded || _binding == null) return
        try {
            val bundle = bundleOf(Constants.EXTRA_MOVIE_ID to movie.id)
            findNavController().navigate(R.id.action_search_to_detail, bundle)
        } catch (e: Exception) {
            Log.e(TAG, "openDetail error: ${e.message}")
        }
    }

    override fun onDestroyView() {
        try { _binding?.rvResults?.adapter = null } catch (e: Exception) { }
        adapter = null
        _binding = null
        super.onDestroyView()
    }
}
