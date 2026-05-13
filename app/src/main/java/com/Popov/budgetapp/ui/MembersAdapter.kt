package com.Popov.budgetapp.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.Popov.budgetapp.data.AppUser
import com.Popov.budgetapp.databinding.ItemMemberBinding

class MembersAdapter : RecyclerView.Adapter<MembersAdapter.MemberViewHolder>() {

    private val items = mutableListOf<AppUser>()

    fun submitList(newItems: List<AppUser>) {
        items.clear()
        items.addAll(newItems.sortedBy { it.email })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class MemberViewHolder(private val binding: ItemMemberBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AppUser) {
            binding.tvMemberEmail.text = item.email.ifBlank { "UID: ${item.uid}" }
        }
    }
}
