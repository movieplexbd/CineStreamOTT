package com.ottapp.moviestream.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.firebase.auth.FirebaseAuth
import com.ottapp.moviestream.LoginActivity
import com.ottapp.moviestream.R
import com.ottapp.moviestream.databinding.FragmentProfileBinding
import com.ottapp.moviestream.ui.admin.AdminActivity
import com.ottapp.moviestream.util.loadImage
import java.text.SimpleDateFormat
import java.util.*

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()

    companion object {
        private const val ADMIN_EMAIL = "a@gmail.com"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val authEmail = FirebaseAuth.getInstance().currentUser?.email
        if (authEmail == ADMIN_EMAIL) binding.btnAdmin.visibility = View.VISIBLE

        viewModel.user.observe(viewLifecycleOwner) { user ->
            if (user == null) {
                if (authEmail == ADMIN_EMAIL) binding.btnAdmin.visibility = View.VISIBLE
                return@observe
            }

            binding.tvName.text  = user.displayName.ifEmpty { "ব্যবহারকারী" }
            binding.tvEmail.text = user.email

            if (user.photoUrl.isNotEmpty()) binding.ivAvatar.loadImage(user.photoUrl)

            if (user.isPremium) {
                binding.tvPlanLabel.text = "প্রিমিয়াম সদস্য ⭐"
                binding.tvPlanLabel.setBackgroundResource(R.drawable.bg_premium_badge)
                if (user.subscriptionExpiry > 0) {
                    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    binding.tvExpiry.text = "মেয়াদ শেষ: ${sdf.format(Date(user.subscriptionExpiry))}"
                    binding.tvExpiry.visibility = View.VISIBLE
                }
            } else {
                binding.tvPlanLabel.text = "ফ্রি প্ল্যান"
                binding.tvPlanLabel.setBackgroundResource(R.drawable.bg_free_badge)
                binding.tvExpiry.visibility = View.GONE
            }

            if (user.email == ADMIN_EMAIL || authEmail == ADMIN_EMAIL) {
                binding.btnAdmin.visibility = View.VISIBLE
            }
        }

        viewModel.storageUsed.observe(viewLifecycleOwner) { size ->
            binding.tvStorage.text = size
        }

        viewModel.signedOut.observe(viewLifecycleOwner) { signedOut ->
            if (signedOut) {
                startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            }
        }

        // Buttons
        binding.btnSignOut.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("লগআউট করবেন?")
                .setMessage("আপনি কি সত্যিই লগআউট করতে চান?")
                .setPositiveButton("হ্যাঁ") { _, _ -> viewModel.signOut() }
                .setNegativeButton("না", null)
                .show()
        }

        binding.btnAdmin.setOnClickListener {
            startActivity(Intent(requireContext(), AdminActivity::class.java))
        }

        binding.btnClearCache.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("ক্যাশ মুছবেন?")
                .setMessage("অ্যাপের সব ক্যাশ ডেটা মুছে ফেলা হবে।")
                .setPositiveButton("মুছুন") { _, _ ->
                    try {
                        requireContext().cacheDir.deleteRecursively()
                        Toast.makeText(requireContext(), "ক্যাশ মুছা হয়েছে", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("বাতিল", null)
                .show()
        }

        binding.btnShareApp.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "CineStream - বাংলা মুভি স্ট্রিমিং অ্যাপ ডাউনলোড করুন!")
            }
            startActivity(Intent.createChooser(shareIntent, "শেয়ার করুন"))
        }

        binding.btnContactSupport.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:support@movieplexbd.com")
                putExtra(Intent.EXTRA_SUBJECT, "CineStream Support")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "ইমেইল অ্যাপ পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnPrivacyPolicy.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("প্রাইভেসি পলিসি")
                .setMessage("CineStream আপনার ডেটা সুরক্ষিত রাখে। আমরা কোনো ব্যক্তিগত তথ্য তৃতীয় পক্ষের সাথে শেয়ার করি না।\n\nআপনার ডেটা শুধুমাত্র অ্যাপের কার্যকারিতার জন্য ব্যবহার করা হয়।")
                .setPositiveButton("বন্ধ করুন", null)
                .show()
        }

        binding.btnAbout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("CineStream সম্পর্কে")
                .setMessage("Version 2.0\n\nCineStream - আপনার পছন্দের বাংলা, হিন্দি ও আন্তর্জাতিক মুভি দেখুন একটি জায়গায়।\n\nDeveloped by MoviePlexBD\n© 2024 সর্বস্বত্ব সংরক্ষিত")
                .setPositiveButton("ঠিক আছে", null)
                .show()
        }
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
