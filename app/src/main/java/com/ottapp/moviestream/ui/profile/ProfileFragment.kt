package com.ottapp.moviestream.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import com.ottapp.moviestream.util.loadImageSafe
import java.text.SimpleDateFormat
import java.util.*

class ProfileFragment : Fragment() {

    companion object {
        private const val TAG = "ProfileFragment"
        private const val ADMIN_EMAIL = "a@gmail.com"
    }

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding
    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return try {
            _binding = FragmentProfileBinding.inflate(inflater, container, false)
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
            val authEmail = try {
                FirebaseAuth.getInstance().currentUser?.email
            } catch (e: Exception) { null }

            if (authEmail == ADMIN_EMAIL) binding?.btnAdmin?.visibility = View.VISIBLE

            viewModel.user.observe(viewLifecycleOwner) { user ->
                val b = binding ?: return@observe
                try {
                    if (user == null) {
                        if (authEmail == ADMIN_EMAIL) b.btnAdmin.visibility = View.VISIBLE
                        return@observe
                    }

                    b.tvName.text  = user.displayName.ifEmpty { "ব্যবহারকারী" }
                    b.tvEmail.text = user.email

                    if (user.photoUrl.isNotEmpty()) b.ivAvatar.loadImageSafe(user.photoUrl)

                    if (user.isPremium) {
                        b.tvPlanLabel.text = "প্রিমিয়াম সদস্য ⭐"
                        b.tvPlanLabel.setBackgroundResource(R.drawable.bg_premium_badge)
                        if (user.subscriptionExpiry > 0) {
                            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                            b.tvExpiry.text = "মেয়াদ শেষ: ${sdf.format(Date(user.subscriptionExpiry))}"
                            b.tvExpiry.visibility = View.VISIBLE
                        }
                    } else {
                        b.tvPlanLabel.text = "ফ্রি প্ল্যান"
                        b.tvPlanLabel.setBackgroundResource(R.drawable.bg_free_badge)
                        b.tvExpiry.visibility = View.GONE
                    }

                    if (user.email == ADMIN_EMAIL || authEmail == ADMIN_EMAIL) {
                        b.btnAdmin.visibility = View.VISIBLE
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "User observer error: ${e.message}")
                }
            }

            viewModel.storageUsed.observe(viewLifecycleOwner) { size ->
                binding?.tvStorage?.text = size
            }

            viewModel.signedOut.observe(viewLifecycleOwner) { signedOut ->
                if (signedOut && isAdded) {
                    try {
                        startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                    } catch (e: Exception) {
                        Log.e(TAG, "Navigate to login error: ${e.message}")
                    }
                }
            }

            setupButtons()
        } catch (e: Exception) {
            Log.e(TAG, "onViewCreated error: ${e.message}", e)
        }
    }

    private fun setupButtons() {
        val b = binding ?: return

        b.btnSignOut.setOnClickListener {
            try {
                AlertDialog.Builder(requireContext())
                    .setTitle("লগআউট করবেন?")
                    .setMessage("আপনি কি সত্যিই লগআউট করতে চান?")
                    .setPositiveButton("হ্যাঁ") { _, _ -> viewModel.signOut() }
                    .setNegativeButton("না", null)
                    .show()
            } catch (e: Exception) {
                Log.e(TAG, "Sign out dialog error: ${e.message}")
            }
        }

        b.btnAdmin.setOnClickListener {
            try {
                startActivity(Intent(requireContext(), AdminActivity::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Admin activity error: ${e.message}")
            }
        }

        b.btnClearCache.setOnClickListener {
            try {
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
            } catch (e: Exception) {
                Log.e(TAG, "Clear cache dialog error: ${e.message}")
            }
        }

        b.btnShareApp.setOnClickListener {
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "CineStream - বাংলা মুভি স্ট্রিমিং অ্যাপ ডাউনলোড করুন!")
                }
                startActivity(Intent.createChooser(shareIntent, "শেয়ার করুন"))
            } catch (e: Exception) {
                Log.e(TAG, "Share error: ${e.message}")
            }
        }

        b.btnContactSupport.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:support@movieplexbd.com")
                    putExtra(Intent.EXTRA_SUBJECT, "CineStream Support")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "ইমেইল অ্যাপ পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            }
        }

        b.btnPrivacyPolicy.setOnClickListener {
            try {
                AlertDialog.Builder(requireContext())
                    .setTitle("প্রাইভেসি পলিসি")
                    .setMessage("CineStream আপনার ডেটা সুরক্ষিত রাখে। আমরা কোনো ব্যক্তিগত তথ্য তৃতীয় পক্ষের সাথে শেয়ার করি না।\n\nআপনার ডেটা শুধুমাত্র অ্যাপের কার্যকারিতার জন্য ব্যবহার করা হয়।")
                    .setPositiveButton("বন্ধ করুন", null)
                    .show()
            } catch (e: Exception) {
                Log.e(TAG, "Privacy dialog error: ${e.message}")
            }
        }

        b.btnAbout.setOnClickListener {
            try {
                AlertDialog.Builder(requireContext())
                    .setTitle("CineStream সম্পর্কে")
                    .setMessage("Version 2.1\n\nCineStream - আপনার পছন্দের বাংলা, হিন্দি ও আন্তর্জাতিক মুভি দেখুন একটি জায়গায়।\n\nDeveloped by MoviePlexBD\n© 2024 সর্বস্বত্ব সংরক্ষিত")
                    .setPositiveButton("ঠিক আছে", null)
                    .show()
            } catch (e: Exception) {
                Log.e(TAG, "About dialog error: ${e.message}")
            }
        }
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
