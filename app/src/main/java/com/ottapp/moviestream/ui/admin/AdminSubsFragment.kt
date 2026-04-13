package com.ottapp.moviestream.ui.admin

import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.FirebaseDatabase
import com.ottapp.moviestream.data.repository.UserRepository
import com.ottapp.moviestream.databinding.FragmentAdminSubsBinding
import com.ottapp.moviestream.util.Constants
import com.ottapp.moviestream.util.toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class SubscriptionRequest(
    val uid: String,
    val transactionId: String,
    val deviceId: String,
    val status: String,
    val submittedAt: Long,
    val expiry: Long
)

class AdminSubsFragment : Fragment() {

    private var _binding: FragmentAdminSubsBinding? = null
    private val binding get() = _binding!!
    private val userRepo = UserRepository()
    private lateinit var adapter: AdminSubAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminSubsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AdminSubAdapter(
            onApprove = { sub -> confirmApprove(sub) },
            onReject = { sub -> confirmReject(sub) }
        )
        binding.rvSubs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSubs.adapter = adapter

        loadSubs()
    }

    private fun loadSubs() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val db = FirebaseDatabase.getInstance("https://movies-bee24-default-rtdb.firebaseio.com").reference
                val snapshot = db.child(Constants.DB_SUBSCRIPTIONS).get().await()
                val subs = mutableListOf<SubscriptionRequest>()
                for (child in snapshot.children) {
                    val data = child.value as? Map<*, *> ?: continue
                    if (data["status"] == "PENDING") {
                        subs.add(SubscriptionRequest(
                            uid = child.key ?: "",
                            transactionId = data["transactionId"]?.toString() ?: "",
                            deviceId = data["deviceId"]?.toString() ?: "",
                            status = data["status"]?.toString() ?: "",
                            submittedAt = data["submittedAt"]?.toString()?.toLongOrNull() ?: 0L,
                            expiry = data["expiry"]?.toString()?.toLongOrNull() ?: 0L
                        ))
                    }
                }
                adapter.submitList(subs)
            } catch (e: Exception) {
                requireContext().toast("লোড করতে সমস্যা: ${e.message}")
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun confirmApprove(sub: SubscriptionRequest) {
        AlertDialog.Builder(requireContext())
            .setTitle("অ্যাপ্রুভ করবেন?")
            .setMessage("ইউজারকে ৩০ দিনের প্রিমিয়াম এক্সেস দেওয়া হবে।")
            .setPositiveButton("হ্যাঁ") { _, _ -> approveSub(sub) }
            .setNegativeButton("না", null)
            .show()
    }

    private fun confirmReject(sub: SubscriptionRequest) {
        AlertDialog.Builder(requireContext())
            .setTitle("রিজেক্ট করবেন?")
            .setMessage("ইউজারকে ফ্রি প্ল্যানে ফেরত পাঠানো হবে।")
            .setPositiveButton("হ্যাঁ") { _, _ -> rejectSub(sub) }
            .setNegativeButton("না", null)
            .show()
    }

    private fun approveSub(sub: SubscriptionRequest) {
        lifecycleScope.launch {
            try {
                val db = FirebaseDatabase.getInstance("https://movies-bee24-default-rtdb.firebaseio.com").reference
                val expiry = System.currentTimeMillis() + Constants.SUBSCRIPTION_DURATION_MS
                
                // Update user status
                userRepo.updateSubscription(sub.uid, Constants.SUB_PREMIUM, expiry)
                
                // Update subscription record
                db.child(Constants.DB_SUBSCRIPTIONS).child(sub.uid).updateChildren(mapOf(
                    "status" to "APPROVED",
                    "expiry" to expiry
                )).await()
                
                requireContext().toast("অ্যাপ্রুভ সফল হয়েছে")
                loadSubs()
            } catch (e: Exception) {
                requireContext().toast("অ্যাপ্রুভ করতে সমস্যা: ${e.message}")
            }
        }
    }

    private fun rejectSub(sub: SubscriptionRequest) {
        lifecycleScope.launch {
            try {
                val db = FirebaseDatabase.getInstance("https://movies-bee24-default-rtdb.firebaseio.com").reference
                
                // Update user status
                userRepo.updateSubscription(sub.uid, Constants.SUB_FREE, 0L)
                
                // Update subscription record
                db.child(Constants.DB_SUBSCRIPTIONS).child(sub.uid).updateChildren(mapOf(
                    "status" to "REJECTED"
                )).await()
                
                requireContext().toast("রিজেক্ট সফল হয়েছে")
                loadSubs()
            } catch (e: Exception) {
                requireContext().toast("রিজেক্ট করতে সমস্যা: ${e.message}")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
