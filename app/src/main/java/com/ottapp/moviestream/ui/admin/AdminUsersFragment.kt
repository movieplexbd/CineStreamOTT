package com.ottapp.moviestream.ui.admin

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.auth.FirebaseAuth
import com.ottapp.moviestream.data.model.User
import com.ottapp.moviestream.data.repository.UserRepository
import com.ottapp.moviestream.databinding.FragmentAdminUsersBinding
import com.ottapp.moviestream.util.Constants
import com.ottapp.moviestream.util.toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AdminUsersFragment : Fragment() {

    private var _binding: FragmentAdminUsersBinding? = null
    private val binding get() = _binding!!
    private val userRepo = UserRepository()
    private lateinit var adapter: AdminUserAdapter
    private var allUsers: List<User> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AdminUserAdapter(
            onBlock = { user -> confirmBlock(user) },
            onMakePremium = { user -> confirmPremium(user) },
            onExtend = { user -> showExtendDialog(user) },
            onResetPassword = { user -> confirmResetPassword(user) }
        )
        binding.rvUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsers.adapter = adapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { filterUsers(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadUsers()
    }

    private fun loadUsers() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val db = FirebaseDatabase.getInstance("https://movies-bee24-default-rtdb.firebaseio.com").reference
                val snapshot = db.child(Constants.DB_USERS).get().await()
                val users = mutableListOf<User>()
                for (child in snapshot.children) {
                    val data = child.value as? Map<*, *> ?: continue
                    users.add(User(
                        uid = child.key ?: "",
                        email = data["email"]?.toString() ?: "",
                        displayName = data["displayName"]?.toString() ?: "",
                        photoUrl = data["photoUrl"]?.toString() ?: "",
                        subscriptionStatus = data["subscriptionStatus"]?.toString() ?: User.PLAN_FREE,
                        subscriptionExpiry = data["subscriptionExpiry"]?.toString()?.toLongOrNull() ?: 0L
                    ))
                }
                allUsers = users
                adapter.submitList(allUsers)
                binding.tvCount.text = "মোট ${allUsers.size} জন ইউজার"
            } catch (e: Exception) {
                requireContext().toast("লোড করতে সমস্যা: ${e.message}")
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun filterUsers(query: String) {
        val q = query.lowercase().trim()
        val filtered = if (q.isEmpty()) allUsers
        else allUsers.filter {
            it.displayName.lowercase().contains(q) || it.email.lowercase().contains(q)
        }
        adapter.submitList(filtered)
        binding.tvCount.text = "মোট ${filtered.size} জন ইউজার"
    }

    private fun confirmBlock(user: User) {
        val isBlocked = user.subscriptionStatus == Constants.SUB_BLOCKED
        val title = if (isBlocked) "আনব্লক করবেন?" else "ব্লক করবেন?"
        val msg = if (isBlocked) "ইউজারকে আনব্লক করা হবে।" else "ইউজারকে ব্লক করা হবে।"
        
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton("হ্যাঁ") { _, _ -> 
                updateUserStatus(user.uid, if (isBlocked) Constants.SUB_FREE else Constants.SUB_BLOCKED, 0L)
            }
            .setNegativeButton("না", null)
            .show()
    }

    private fun confirmPremium(user: User) {
        AlertDialog.Builder(requireContext())
            .setTitle("প্রিমিয়াম করবেন?")
            .setMessage("ইউজারকে ৩০ দিনের প্রিমিয়াম এক্সেস দেওয়া হবে।")
            .setPositiveButton("হ্যাঁ") { _, _ -> 
                val expiry = System.currentTimeMillis() + Constants.SUBSCRIPTION_DURATION_MS
                updateUserStatus(user.uid, Constants.SUB_PREMIUM, expiry)
            }
            .setNegativeButton("না", null)
            .show()
    }

    private fun showExtendDialog(user: User) {
        val options = arrayOf("7 Days", "30 Days", "90 Days", "365 Days")
        val days = longArrayOf(7, 30, 90, 365)
        
        AlertDialog.Builder(requireContext())
            .setTitle("Extend Subscription")
            .setItems(options) { _, which ->
                val currentExpiry = if (user.subscriptionExpiry > System.currentTimeMillis()) user.subscriptionExpiry else System.currentTimeMillis()
                val newExpiry = currentExpiry + (days[which] * 24 * 60 * 60 * 1000L)
                updateUserStatus(user.uid, Constants.SUB_PREMIUM, newExpiry)
            }
            .show()
    }

    private fun confirmResetPassword(user: User) {
        AlertDialog.Builder(requireContext())
            .setTitle("Reset Password?")
            .setMessage("Send password reset email to ${user.email}?")
            .setPositiveButton("Send") { _, _ ->
                FirebaseAuth.getInstance().sendPasswordResetEmail(user.email)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) requireContext().toast("Reset email sent")
                        else requireContext().toast("Failed: ${task.exception?.message}")
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateUserStatus(uid: String, status: String, expiry: Long) {
        lifecycleScope.launch {
            try {
                userRepo.updateSubscription(uid, status, expiry)
                requireContext().toast("আপডেট সফল হয়েছে")
                loadUsers()
            } catch (e: Exception) {
                requireContext().toast("আপডেট করতে সমস্যা: ${e.message}")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
