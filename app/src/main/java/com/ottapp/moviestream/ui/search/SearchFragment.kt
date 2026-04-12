package com.ottapp.moviestream.ui.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.button.MaterialButton
import com.ottapp.moviestream.R
import com.ottapp.moviestream.adapter.MovieGridAdapter
import com.ottapp.moviestream.data.model.Movie
import com.ottapp.moviestream.databinding.FragmentSearchBinding
import com.ottapp.moviestream.util.Constants
import com.ottapp.moviestream.util.hide
import com.ottapp.moviestream.util.show

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SearchViewModel by viewModels()
    private lateinit var adapter: MovieGridAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = MovieGridAdapter { openDetail(it) }
        binding.rvResults.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvResults.adapter = adapter

        setupSearch()
        setupFilters()
        observeViewModel()
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.search(s.toString())
                binding.ivClear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.ivClear.setOnClickListener {
            binding.etSearch.setText("")
            adapter.submitList(emptyList())
        }
    }

    private fun setupFilters() {
        val chips = listOf(
            binding.chipAll      to Constants.CAT_ALL,
            binding.chipBangla   to Constants.CAT_BANGLA,
            binding.chipHindi    to Constants.CAT_HINDI,
            binding.chipTrending to Constants.CAT_TRENDING
        )
        chips.forEach { (chip, cat) ->
            chip.setOnClickListener {
                chips.forEach { (c, _) -> setChipSelected(c, false) }
                setChipSelected(chip, true)
                viewModel.setFilter(cat)
            }
        }
        setChipSelected(binding.chipAll, true)
    }

    private fun setChipSelected(chip: MaterialButton, selected: Boolean) {
        if (selected) {
            chip.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red))
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            chip.strokeWidth = 0
        } else {
            chip.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface2))
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.t2))
            chip.strokeWidth = 2
        }
    }

    private fun observeViewModel() {
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            if (loading) binding.progressSearch.show() else binding.progressSearch.hide()
        }

        viewModel.results.observe(viewLifecycleOwner) { movies ->
            adapter.submitList(movies)
            val query = binding.etSearch.text.toString()
            when {
                query.isBlank()  -> { binding.layoutEmpty.hide(); binding.layoutSearchHint.show() }
                movies.isEmpty() -> { binding.layoutEmpty.show(); binding.layoutSearchHint.hide() }
                else             -> { binding.layoutEmpty.hide(); binding.layoutSearchHint.hide() }
            }
        }
    }

    private fun openDetail(movie: Movie) {
        if (!isAdded || _binding == null) return
        try {
            val bundle = android.os.Bundle().apply { putString(Constants.EXTRA_MOVIE_ID, movie.id) }
            findNavController().navigate(R.id.action_search_to_detail, bundle)
        } catch (e: Exception) { }
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
