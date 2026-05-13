package com.Popov.budgetapp.ui

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.Popov.budgetapp.R
import com.Popov.budgetapp.data.Budget
import com.Popov.budgetapp.databinding.ItemBudgetBinding
import java.text.NumberFormat
import java.util.Locale

class BudgetAdapter(
    private val onSelect: (Budget) -> Unit,
    private val onDelete: (Budget) -> Unit
) : RecyclerView.Adapter<BudgetAdapter.BudgetViewHolder>() {

    private val items = mutableListOf<Budget>()

    @Suppress("UNUSED_PARAMETER")
    fun submitList(newItems: List<Budget>, selectedId: String) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetViewHolder {
        val binding = ItemBudgetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BudgetViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: BudgetViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class BudgetViewHolder(private val binding: ItemBudgetBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Budget) {
            binding.tvBudgetName.text = item.name
            binding.ivBudgetIcon.setImageResource(iconByCategory(item.category))
            binding.tvParticipants.text = participantsLabel(item.members.size)
            binding.tvAmount.text = formatRub(item.limit)

            val palette = paletteFor(item.category)
            applySolidColor(binding.root, palette.card)
            applySolidColor(binding.flIconSlot, palette.slot)

            binding.root.setOnClickListener { onSelect(item) }
            binding.btnBudgetMenu.setOnClickListener { anchor ->
                PopupMenu(anchor.context, anchor).apply {
                    inflate(R.menu.budget_item_menu)
                    setOnMenuItemClickListener { menuItem ->
                        if (menuItem.itemId == R.id.action_delete_budget) {
                            onDelete(item)
                            true
                        } else {
                            false
                        }
                    }
                    show()
                }
            }
        }

        private fun applySolidColor(view: View, colorRes: Int) {
            val drawable = view.background.mutate()
            if (drawable is GradientDrawable) {
                drawable.setColor(ContextCompat.getColor(view.context, colorRes))
            }
        }

        private fun paletteFor(category: String): Palette {
            val c = category.lowercase()
            return when {
                c.contains("дом") || c.contains("сем") -> Palette(R.color.pastel_green, R.color.slot_green)
                c.contains("путеш") || c.contains("отпуск") -> Palette(R.color.pastel_orange, R.color.slot_orange)
                c.contains("покуп") -> Palette(R.color.pastel_lilac, R.color.slot_lilac)
                c.contains("авто") || c.contains("транспорт") -> Palette(R.color.pastel_rose, R.color.slot_rose)
                else -> Palette(R.color.pastel_blue, R.color.slot_blue)
            }
        }

        @DrawableRes
        private fun iconByCategory(category: String): Int {
            val normalized = category.lowercase()
            return when {
                normalized.contains("дом") || normalized.contains("сем") -> R.drawable.ic_budget_home_24
                normalized.contains("путеш") || normalized.contains("отпуск") -> R.drawable.ic_budget_plane_24
                normalized.contains("покуп") -> R.drawable.ic_budget_bag_24
                normalized.contains("авто") || normalized.contains("транспорт") -> R.drawable.ic_budget_car_24
                normalized.contains("здоров") -> R.drawable.ic_budget_default_24
                normalized.contains("развлеч") -> R.drawable.ic_budget_default_24
                else -> R.drawable.ic_budget_default_24
            }
        }

        private fun participantsLabel(count: Int): String {
            val n = count.coerceAtLeast(0)
            val mod100 = n % 100
            val mod10 = n % 10
            val word = when {
                mod100 in 11..14 -> "участников"
                mod10 == 1 -> "участник"
                mod10 in 2..4 -> "участника"
                else -> "участников"
            }
            return "$n $word"
        }

        private fun formatRub(amount: Double): String {
            val n = kotlin.math.round(amount).toLong()
            val nf = NumberFormat.getNumberInstance(Locale("ru", "RU"))
            return "${nf.format(n)} ₽"
        }
    }

    private data class Palette(val card: Int, val slot: Int)
}
