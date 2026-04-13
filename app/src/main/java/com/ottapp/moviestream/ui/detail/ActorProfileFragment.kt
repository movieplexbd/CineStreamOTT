package com.ottapp.moviestream.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.ottapp.moviestream.R
import com.ottapp.moviestream.data.repository.ActorRepository
import com.ottapp.moviestream.data.repository.MovieRepository
import com.ottapp.moviestream.databinding.FragmentActorProfileBinding
import com.ottapp.moviestream.ui.movies.MovieAdapter
import com.ottapp.moviestream.util.loadImage
import com.ottapp.moviestream.util.toast
import kotlinx.coroutines.launch

class ActorProfileFragment : Fragment() {

    private var _binding: FragmentActorProfileBinding? = null
    private val binding get() = _binding!!
    private val actorRepo = ActorRepository()
    private val movieRepo = MovieRepository()
    private lateinit var movieAdapter: MovieAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentActorProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val actorId = arguments?.getString("actor_id") ?: return

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        movieAdapter = MovieAdapter { movie ->
            val bundle = Bundle().apply { putString("extra_movie_id", movie.id) }
            findNavController().navigate(R.id.movieDetailFragment, bundle)
        }

        binding.rvMovies.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvMovies.adapter = movieAdapter

        loadActorData(actorId)
    }

    private fun loadActorData(actorId: String) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val actor = actorRepo.getActorById(actorId)
                if (actor != null) {
                    binding.tvActorName.text = actor.name
                    binding.ivActor.loadImage(actor.imageUrl)
                    
                    val allMovies = movieRepo.getAllMovies()
                    val actorMovies = allMovies.filter { it.actorIds.contains(actorId) }
                    
                    movieAdapter.submitList(actorMovies)
                    binding.tvMovieCount.text = "${actorMovies.size} টি মুভি"
                }
            } catch (e: Exception) {
                requireContext().toast("লোড করতে সমস্যা হয়েছে")
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
