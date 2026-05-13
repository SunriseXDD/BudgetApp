package com.Popov.budgetapp.ui

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.Popov.budgetapp.R
import com.Popov.budgetapp.data.TransactionItem
import com.Popov.budgetapp.data.TransactionType
import com.Popov.budgetapp.databinding.ItemTransactionBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionAdapter(
    private val currentUid: () -> String,
    private val onClick: (TransactionItem) -> Unit,
    private val onLongClick: (TransactionItem) -> Unit,
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    private val items = mutableListOf<TransactionItem>()
    private val numberFormat = NumberFormat.getNumberInstance(Locale("ru", "RU"))
    private val dateFormat = SimpleDateFormat("d MMMM", Locale("ru", "RU"))

    fun submitList(newItems: List<TransactionItem>) {
        items.clear()
        items.addAll(newItems.sortedByDescending { it.createdAt })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class TransactionViewHolder(private val binding: ItemTransactionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TransactionItem) {
            val ctx = binding.root.context
            binding.tvTransactionTitle.text = item.title

            val uid = currentUid()
            val author = when {
                item.createdBy.isBlank() -> ""
                item.createdBy == uid -> "Вы"
                item.createdBy.length <= 8 -> item.createdBy
                else -> item.createdBy.take(6) + "…"
            }
            binding.tvTransactionSubtitle.text = if (author.isNotEmpty()) {
                "${item.category} · $author"
            } else {
                item.category
            }

            val dot = GradientDrawable()
            dot.shape = GradientDrawable.OVAL
            dot.setColor(categoryColor(ctx, item.category))
            binding.viewCategoryDot.background = dot

            val amountStr = numberFormat.format(kotlin.math.round(item.amount).toLong())
            if (item.type == TransactionType.INCOME) {
                binding.tvTransactionAmount.text = "+ $amountStr ₽"
                binding.tvTransactionAmount.setTextColor(ContextCompat.getColor(ctx, R.color.income_text_green))
            } else {
                binding.tvTransactionAmount.text = "− $amountStr ₽"
                binding.tvTransactionAmount.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            }

            binding.tvTransactionDate.text = dateFormat.format(Date(item.createdAt))

            binding.root.setOnClickListener { onClick(item) }
            binding.root.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }

        private fun categoryColor(ctx: android.content.Context, category: String): Int {
            val palette = intArrayOf(
                R.color.donut_1,
                R.color.donut_2,
                R.color.donut_3,
                R.color.donut_4,
                R.color.donut_5,
            )
            val idx = kotlin.math.abs(category.hashCode()) % palette.size
            return ContextCompat.getColor(ctx, palette[idx])
        }
    }
}
