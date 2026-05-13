package com.Popov.budgetapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.Popov.budgetapp.R
import com.Popov.budgetapp.data.FirebaseRepository
import com.Popov.budgetapp.data.TransactionType
import com.Popov.budgetapp.databinding.FragmentReportsBinding
import com.Popov.budgetapp.databinding.ItemReportLegendRowBinding
import com.Popov.budgetapp.ui.widget.DonutChartView
import com.google.firebase.firestore.ListenerRegistration
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class ReportsFragment : Fragment(R.layout.fragment_reports) {
    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!
    private val repo = FirebaseRepository()

    private enum class PeriodTab { WEEK, MONTH, YEAR, PERIOD }

    private var periodTab = PeriodTab.MONTH
    private val anchor: Calendar = Calendar.getInstance()
    private var transactions: List<com.Popov.budgetapp.data.TransactionItem> = emptyList()
    private var transactionsListener: ListenerRegistration? = null

    private val moneyFmt = NumberFormat.getNumberInstance(Locale("ru", "RU"))
    private val donutColors = listOf(
        R.color.donut_1,
        R.color.donut_2,
        R.color.donut_3,
        R.color.donut_4,
        R.color.donut_5,
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentReportsBinding.bind(view)

        binding.tvReportsBudgetTitle.text =
            SessionStore.selectedBudgetName.ifBlank { "Отчеты" }

        binding.tabWeek.setOnClickListener { selectPeriod(PeriodTab.WEEK) }
        binding.tabMonth.setOnClickListener { selectPeriod(PeriodTab.MONTH) }
        binding.tabYear.setOnClickListener { selectPeriod(PeriodTab.YEAR) }
        binding.tabPeriod.setOnClickListener { selectPeriod(PeriodTab.PERIOD) }

        binding.btnPeriodPrev.setOnClickListener {
            stepAnchor(-1)
            render()
        }
        binding.btnPeriodNext.setOnClickListener {
            stepAnchor(1)
            render()
        }

        binding.btnReportsCalendar.setOnClickListener {
            Toast.makeText(requireContext(), "Используйте стрелки для смены периода", Toast.LENGTH_SHORT).show()
        }

        selectPeriod(PeriodTab.MONTH)

        if (SessionStore.selectedBudgetId.isBlank()) {
            binding.tvReportsEmpty.visibility = View.VISIBLE
            binding.tvReportsEmpty.text = "Выберите бюджет на вкладке «Бюджеты»"
            return
        }

        transactionsListener?.remove()
        transactionsListener = repo.subscribeTransactions(SessionStore.selectedBudgetId) { result ->
            if (_binding == null) return@subscribeTransactions
            result.onSuccess { list ->
                if (_binding == null) return@onSuccess
                transactions = list
                render()
            }.onFailure { err ->
                if (_binding == null) return@onFailure
                Toast.makeText(requireContext(), err.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun stepAnchor(delta: Int) {
        when (periodTab) {
            PeriodTab.WEEK -> anchor.add(Calendar.WEEK_OF_YEAR, delta)
            PeriodTab.MONTH, PeriodTab.PERIOD -> anchor.add(Calendar.MONTH, delta)
            PeriodTab.YEAR -> anchor.add(Calendar.YEAR, delta)
        }
    }

    private fun selectPeriod(tab: PeriodTab) {
        periodTab = tab
        binding.tabWeek.setBackgroundResource(
            if (tab == PeriodTab.WEEK) R.drawable.bg_segment_selected else R.drawable.bg_segment_unselected,
        )
        binding.tabWeek.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (tab == PeriodTab.WEEK) R.color.text_primary else R.color.text_secondary,
            ),
        )
        binding.tabMonth.setBackgroundResource(
            if (tab == PeriodTab.MONTH) R.drawable.bg_segment_selected else R.drawable.bg_segment_unselected,
        )
        binding.tabMonth.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (tab == PeriodTab.MONTH) R.color.text_primary else R.color.text_secondary,
            ),
        )
        binding.tabYear.setBackgroundResource(
            if (tab == PeriodTab.YEAR) R.drawable.bg_segment_selected else R.drawable.bg_segment_unselected,
        )
        binding.tabYear.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (tab == PeriodTab.YEAR) R.color.text_primary else R.color.text_secondary,
            ),
        )
        binding.tabPeriod.setBackgroundResource(
            if (tab == PeriodTab.PERIOD) R.drawable.bg_segment_selected else R.drawable.bg_segment_unselected,
        )
        binding.tabPeriod.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (tab == PeriodTab.PERIOD) R.color.text_primary else R.color.text_secondary,
            ),
        )

        binding.tabWeek.setTypeface(null, if (tab == PeriodTab.WEEK) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        binding.tabMonth.setTypeface(null, if (tab == PeriodTab.MONTH) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        binding.tabYear.setTypeface(null, if (tab == PeriodTab.YEAR) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        binding.tabPeriod.setTypeface(null, if (tab == PeriodTab.PERIOD) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

        render()
    }

    private fun render() {
        if (SessionStore.selectedBudgetId.isBlank() || _binding == null) return

        binding.tvReportsEmpty.visibility = View.GONE

        val (start, end) = computeRange(anchor, periodTab)
        binding.tvPeriodLabel.text = formatPeriodTitle()

        val expenses = transactions.filter {
            it.type == TransactionType.EXPENSE && it.createdAt >= start && it.createdAt <= end
        }
        val totalExpense = expenses.sumOf { it.amount }
        binding.tvExpensesTotal.text =
            "${moneyFmt.format(totalExpense.roundToLong())} ₽"

        val byCategory = expenses.groupBy { it.category }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
            .entries
            .sortedByDescending { it.value }

        binding.legendContainer.removeAllViews()
        if (byCategory.isEmpty()) {
            binding.donutChart.segments = emptyList()
            val empty = TextView(requireContext())
            empty.text = "Нет расходов за период"
            empty.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            binding.legendContainer.addView(empty)
        } else {
            val segments = mutableListOf<DonutChartView.Segment>()
            byCategory.forEachIndexed { index, entry ->
                val color = ContextCompat.getColor(
                    requireContext(),
                    donutColors[index % donutColors.size],
                )
                segments.add(DonutChartView.Segment(entry.value.toFloat(), color))

                val row = ItemReportLegendRowBinding.inflate(LayoutInflater.from(requireContext()), binding.legendContainer, false)
                val dot = android.graphics.drawable.GradientDrawable()
                dot.shape = android.graphics.drawable.GradientDrawable.OVAL
                dot.setColor(color)
                row.legendDot.background = dot
                row.tvLegendLabel.text = entry.key
                val pct = if (totalExpense > 0) {
                    (entry.value / totalExpense * 100).roundToInt()
                } else {
                    0
                }
                row.tvLegendPercent.text = "$pct%"
                row.tvLegendAmount.text = "${moneyFmt.format(entry.value.roundToLong())} ₽"
                binding.legendContainer.addView(row.root)
            }
            binding.donutChart.segments = segments
        }

        val linePoints = buildDailySeries(expenses, start, end)
        binding.lineChart.values = linePoints

        val prevTotal = previousPeriodExpenseTotal()
        val prevAnchor = anchor.clone() as Calendar
        when (periodTab) {
            PeriodTab.WEEK -> prevAnchor.add(Calendar.WEEK_OF_YEAR, -1)
            PeriodTab.MONTH, PeriodTab.PERIOD -> prevAnchor.add(Calendar.MONTH, -1)
            PeriodTab.YEAR -> prevAnchor.add(Calendar.YEAR, -1)
        }
        val prevMonthName = SimpleDateFormat("LLLL", Locale("ru", "RU")).format(prevAnchor.time)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru")) else it.toString() }
        binding.tvChartHint.text = if (periodTab == PeriodTab.MONTH || periodTab == PeriodTab.PERIOD) {
            compareHint(totalExpense, prevTotal, prevMonthName)
        } else {
            compareHint(totalExpense, prevTotal, null)
        }
    }

    private fun compareHint(current: Double, previous: Double, prevLabel: String? = null): String {
        if (previous <= 0.0) {
            return if (current <= 0.0) {
                "Нет данных за выбранный период"
            } else {
                "Нет расходов за прошлый период для сравнения"
            }
        }
        val deltaPct = ((current - previous) / previous * 100).roundToInt()
        val cmp = when {
            deltaPct < 0 -> "ниже"
            deltaPct > 0 -> "выше"
            else -> "на уровне"
        }
        val extra = prevLabel?.let { " ($it)" } ?: ""
        return if (deltaPct == 0) {
            "Расходы на уровне прошлого периода"
        } else {
            "${if (deltaPct > 0) "+" else ""}$deltaPct% $cmp по сравнению с прошлым периодом$extra"
        }
    }

    private fun previousPeriodExpenseTotal(): Double {
        val prev = anchor.clone() as Calendar
        when (periodTab) {
            PeriodTab.WEEK -> prev.add(Calendar.WEEK_OF_YEAR, -1)
            PeriodTab.MONTH, PeriodTab.PERIOD -> prev.add(Calendar.MONTH, -1)
            PeriodTab.YEAR -> prev.add(Calendar.YEAR, -1)
        }
        val (s, e) = computeRange(prev, periodTab)
        return transactions
            .filter { it.type == TransactionType.EXPENSE && it.createdAt in s..e }
            .sumOf { it.amount }
    }

    private fun buildDailySeries(
        expenses: List<com.Popov.budgetapp.data.TransactionItem>,
        start: Long,
        @Suppress("UNUSED_PARAMETER") end: Long,
    ): List<Float> {
        return when (periodTab) {
            PeriodTab.WEEK -> {
                val sums = FloatArray(7)
                for (t in expenses) {
                    val idx = ((t.createdAt - start) / 86400000L).toInt().coerceIn(0, 6)
                    sums[idx] += t.amount.toFloat()
                }
                sums.toList()
            }
            PeriodTab.MONTH, PeriodTab.PERIOD -> {
                val cal = Calendar.getInstance().apply { timeInMillis = start }
                val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val sums = FloatArray(days)
                for (t in expenses) {
                    val c = Calendar.getInstance().apply { timeInMillis = t.createdAt }
                    val d = c.get(Calendar.DAY_OF_MONTH) - 1
                    if (d in sums.indices) sums[d] += t.amount.toFloat()
                }
                sums.toList()
            }
            PeriodTab.YEAR -> {
                val sums = FloatArray(12)
                for (t in expenses) {
                    val c = Calendar.getInstance().apply { timeInMillis = t.createdAt }
                    val m = c.get(Calendar.MONTH)
                    sums[m] += t.amount.toFloat()
                }
                sums.toList()
            }
        }
    }

    private fun stripToStartOfDay(c: Calendar): Calendar {
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c
    }

    private fun computeRange(cal: Calendar, tab: PeriodTab): Pair<Long, Long> {
        val c = cal.clone() as Calendar
        return when (tab) {
            PeriodTab.WEEK -> {
                c.timeInMillis = cal.timeInMillis
                c.set(Calendar.DAY_OF_WEEK, c.firstDayOfWeek)
                stripToStartOfDay(c)
                val start = c.timeInMillis
                c.add(Calendar.DAY_OF_MONTH, 7)
                val end = c.timeInMillis - 1
                start to end
            }
            PeriodTab.MONTH, PeriodTab.PERIOD -> {
                c.set(Calendar.DAY_OF_MONTH, 1)
                stripToStartOfDay(c)
                val start = c.timeInMillis
                val max = c.getActualMaximum(Calendar.DAY_OF_MONTH)
                c.set(Calendar.DAY_OF_MONTH, max)
                c.set(Calendar.HOUR_OF_DAY, 23)
                c.set(Calendar.MINUTE, 59)
                c.set(Calendar.SECOND, 59)
                c.set(Calendar.MILLISECOND, 999)
                val end = c.timeInMillis
                start to end
            }
            PeriodTab.YEAR -> {
                c.set(Calendar.MONTH, Calendar.JANUARY)
                c.set(Calendar.DAY_OF_MONTH, 1)
                stripToStartOfDay(c)
                val start = c.timeInMillis
                c.add(Calendar.YEAR, 1)
                val end = c.timeInMillis - 1
                start to end
            }
        }
    }

    private fun formatPeriodTitle(): String {
        val c = anchor.clone() as Calendar
        return when (periodTab) {
            PeriodTab.WEEK -> {
                val (s, _) = computeRange(c, PeriodTab.WEEK)
                val cal = Calendar.getInstance().apply { timeInMillis = s }
                val df = SimpleDateFormat("d MMM", Locale("ru", "RU"))
                val s1 = capitalizeMonth(df.format(cal.time))
                cal.add(Calendar.DAY_OF_MONTH, 6)
                val s2 = capitalizeMonth(df.format(cal.time))
                val y = c.get(Calendar.YEAR)
                "$s1 – $s2 $y"
            }
            PeriodTab.MONTH, PeriodTab.PERIOD -> {
                capitalizeMonth(SimpleDateFormat("LLLL yyyy", Locale("ru", "RU")).format(c.time))
            }
            PeriodTab.YEAR -> "${c.get(Calendar.YEAR)}"
        }
    }

    private fun capitalizeMonth(s: String): String =
        s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru")) else it.toString() }

    override fun onDestroyView() {
        transactionsListener?.remove()
        transactionsListener = null
        super.onDestroyView()
        _binding = null
    }
}
