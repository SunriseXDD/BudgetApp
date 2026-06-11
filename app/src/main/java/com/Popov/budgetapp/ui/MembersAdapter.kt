package com.Popov.budgetapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.Popov.budgetapp.data.AppUser
import com.Popov.budgetapp.databinding.ItemMemberBinding

class MembersAdapter(
    private val onRemove: (AppUser) -> Unit,
) : RecyclerView.Adapter<MembersAdapter.MemberViewHolder>() {

    private val items = mutableListOf<AppUser>()
    private var currentUid: String = ""
    private var ownerUids: Set<String> = emptySet()
    private var canRemoveMembers: Boolean = false

    fun submitList(
        newItems: List<AppUser>,
        currentUid: String,
        ownerUids: Set<String>,
        canRemoveMembers: Boolean,
    ) {
        items.clear()
        items.addAll(newItems.sortedBy { it.displayName.lowercase() })
        this.currentUid = currentUid
        this.ownerUids = ownerUids
        this.canRemoveMembers = canRemoveMembers
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
            binding.tvMemberNickname.text = item.displayName
            val isOwner = item.uid in ownerUids
            binding.tvMemberRole.visibility = if (isOwner) View.VISIBLE else View.GONE

            val removable = canRemoveMembers &&
                item.uid != currentUid &&
                !isOwner
            binding.btnRemoveMember.visibility = if (removable) View.VISIBLE else View.GONE
            binding.btnRemoveMember.setOnClickListener {
                if (removable) onRemove(item)
            }
        }
    }
}
