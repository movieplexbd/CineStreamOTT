package com.ottapp.moviestream.ui.profile

  import android.content.Intent
  import android.os.Bundle
  import android.view.LayoutInflater
  import android.view.View
  import android.view.ViewGroup
  import androidx.fragment.app.Fragment
  import androidx.fragment.app.viewModels
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

          viewModel.user.observe(viewLifecycleOwner) { user ->
              if (user == null) return@observe
              binding.tvName.text  = user.displayName.ifEmpty { "ব্যবহারকারী" }
              binding.tvEmail.text = user.email

              if (user.photoUrl.isNotEmpty()) binding.ivAvatar.loadImage(user.photoUrl)

              if (user.isPremium) {
                  binding.tvPlanLabel.text = "প্রিমিয়াম সদস্য"
                  binding.tvPlanLabel.setBackgroundResource(R.drawable.bg_premium_badge)
                  if (user.subscriptionExpiry > 0) {
                      val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                      binding.tvExpiry.text = "মেয়াদ: ${sdf.format(Date(user.subscriptionExpiry))}"
                      binding.tvExpiry.visibility = View.VISIBLE
                  }
              } else {
                  binding.tvPlanLabel.text = "ফ্রি প্ল্যান"
                  binding.tvPlanLabel.setBackgroundResource(R.drawable.bg_free_badge)
                  binding.tvExpiry.visibility = View.GONE
              }

              // Admin button - only show for admin email
              if (user.email == ADMIN_EMAIL) {
                  binding.btnAdmin.visibility = View.VISIBLE
              } else {
                  binding.btnAdmin.visibility = View.GONE
              }
          }

          viewModel.storageUsed.observe(viewLifecycleOwner) { size ->
              binding.tvStorage.text = "ডাউনলোড স্টোরেজ: $size"
          }

          viewModel.signedOut.observe(viewLifecycleOwner) { signedOut ->
              if (signedOut) {
                  startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                  })
              }
          }

          binding.btnSignOut.setOnClickListener { viewModel.signOut() }

          binding.btnAdmin.setOnClickListener {
              startActivity(Intent(requireContext(), AdminActivity::class.java))
          }
      }

      override fun onDestroyView() { _binding = null; super.onDestroyView() }
  }
  