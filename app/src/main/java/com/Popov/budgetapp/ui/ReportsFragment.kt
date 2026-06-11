package com.Popov.budgetapp.ui

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.Popov.budgetapp.R
import com.Popov.budgetapp.data.FirebaseRepository
import com.Popov.budgetapp.data.userMessage
import com.Popov.budgetapp.data.TransactionItem
import com.Popov.budgetapp.data.TransactionType
import com.Popov.budgetapp.databinding.FragmentReportsBinding
import com.Popov.budgetapp.databinding.ItemReportLegendRowBinding
import com.Popov.budgetapp.ui.widget.DonutChartView
import com.google.firebase.firestore.ListenerRegistration
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class ReportsFragment : Fragment(R.layout.fragment_reports) {
    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!
    private val repo = FirebaseRepository()

    private enum class PeriodTab { WEEK, MONTH, YEAR }

    private data class CategorySlice(
        val label: String,
        val amount: Double,
        val color: Int,
    )

    private var periodTab = PeriodTab.MONTH
    private val anchor: Calendar = Calendar.getInstance()
    private var transactions: List<TransactionItem> = emptyList()
    private var transactionsListener: ListenerRegistration? = null
    private var subscribedBudgetId: String = ""

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

        binding.lineIncomeChart.lineColorRes = R.color.chart_line_income

        binding.tvReportsBudgetTitle.text =
            SessionStore.selectedBudgetName.ifBlank { "Отчеты" }

        binding.tabWeek.setOnClickListener { selectPeriod(PeriodTab.WEEK) }
        binding.tabMonth.setOnClickListener { selectPeriod(PeriodTab.MONTH) }
        binding.tabYear.setOnClickListener { selectPeriod(PeriodTab.YEAR) }

        binding.btnPeriodPrev.setOnClickListener {
            stepAnchor(-1)
            render()
        }
        binding.btnPeriodNext.setOnClickListener {
            stepAnchor(1)
            render()
        }

        selectPeriod(PeriodTab.MONTH)
        ensureTransactionsSubscription()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            binding.tvReportsBudgetTitle.text =
                SessionStore.selectedBudgetName.ifBlank { "Отчеты" }
            ensureTransactionsSubscription()
        }
    }

    private fun ensureTransactionsSubscription() {
        val budgetId = SessionStore.selectedBudgetId
        if (budgetId.isBlank()) {
            if (subscribedBudgetId.isNotEmpty()) {
                transactionsListener?.remove()
                transactionsListener = null
                subscribedBudgetId = ""
                transactions = emptyList()
            }
            binding.tvReportsEmpty.visibility = View.VISIBLE
            binding.tvReportsEmpty.text = "Выберите бюджет на вкладке «Бюджеты»"
            binding.cardSummary.visibility = View.GONE
            return
        }
        if (budgetId == subscribedBudgetId) return
        subscribedBudgetId = budgetId
        transactionsListener?.remove()
        transactions = emptyList()
        transactionsListener = repo.subscribeTransactions(budgetId) { result ->
            if (_binding == null) return@subscribeTransactions
            result.onSuccess { list ->
                if (_binding == null) return@onSuccess
                transactions = list
                render()
            }.onFailure { err ->
                if (_binding == null) return@onFailure
                Toast.makeText(requireContext(), err.userMessage(requireContext()), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun stepAnchor(delta: Int) {
        when (periodTab) {
            PeriodTab.WEEK -> anchor.add(Calendar.WEEK_OF_YEAR, delta)
            PeriodTab.MONTH -> anchor.add(Calendar.MONTH, delta)
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
        binding.tabWeek.setTypeface(null, if (tab == PeriodTab.WEEK) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        binding.tabMonth.setTypeface(null, if (tab == PeriodTab.MONTH) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        binding.tabYear.setTypeface(null, if (tab == PeriodTab.YEAR) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

        render()
    }

    private fun render() {
        if (SessionStore.selectedBudgetId.isBlank() || _binding == null) return

        binding.tvReportsEmpty.visibility = View.GONE
        binding.cardSummary.visibility = View.VISIBLE

        val (start, end) = computeRange(anchor, periodTab)
        binding.tvPeriodLabel.text = formatPeriodTitle()

        val incomeItems = filterByPeriod(TransactionType.INCOME, start, end)
        val expenseItems = filterByPeriod(TransactionType.EXPENSE, start, end)
        val totalIncome = incomeItems.sumOf { it.amount }
        val totalExpense = expenseItems.sumOf { it.amount }
        val balance = totalIncome - totalExpense

        renderSummary(totalIncome, totalExpense, balance)

        binding.tvExpensesTotal.text = formatMoney(totalExpense)
        renderCategoryChart(
            expenseItems,
            totalExpense,
            binding.legendContainer,
            binding.donutChart,
            "Нет расходов за период",
        )
        binding.lineChart.values = buildDailySeries(expenseItems, start, end)
        binding.tvChartHint.text = compareHint(
            totalExpense,
            previousPeriodTotal(TransactionType.EXPENSE),
            expenseLabel = true,
        )

        binding.tvIncomeTotal.text = formatMoney(totalIncome)
        renderCategoryChart(
            incomeItems,
            totalIncome,
            binding.legendIncomeContainer,
            binding.donutIncomeChart,
            getString(R.string.reports_no_income),
        )
        binding.lineIncomeChart.values = buildDailySeries(incomeItems, start, end)
        val incomeHint = compareHint(
            totalIncome,
            previousPeriodTotal(TransactionType.INCOME),
            expenseLabel = false,
        )
        binding.tvIncomeChartHint.text = incomeHint
        binding.lineChart.setOnClickListener { showChartHintDialog(getString(R.string.reports_expenses), binding.tvChartHint.text.toString()) }
        binding.lineIncomeChart.setOnClickListener { showChartHintDialog(getString(R.string.reports_income), incomeHint) }
        binding.donutChart.isClickable = true
        binding.donutIncomeChart.isClickable = true
    }

    private fun showChartHintDialog(title: String, message: String) {
        if (message.isBlank()) return
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showCategoryDetail(slice: CategorySlice, total: Double) {
        val pct = if (total > 0) (slice.amount / total * 100).roundToInt() else 0
        AlertDialog.Builder(requireContext())
            .setTitle(slice.label)
            .setMessage(getString(R.string.reports_category_detail, formatMoney(slice.amount), pct))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun renderSummary(income: Double, expense: Double, balance: Double) {
        binding.tvSummaryBalance.text = formatSignedMoney(balance)
        val balanceColor = when {
            balance > 0 -> R.color.income_text_green
            balance < 0 -> R.color.danger
            else -> R.color.text_primary
        }
        binding.tvSummaryBalance.setTextColor(ContextCompat.getColor(requireContext(), balanceColor))
        binding.tvSummaryIncome.text = formatMoney(income)
        binding.tvSummaryExpense.text = formatMoney(expense)
        binding.tvSummarySavings.text = if (income > 0) {
            val rate = ((income - expense) / income * 100).roundToInt().coerceIn(-999, 999)
            "${if (rate > 0) "+" else ""}$rate%"
        } else {
            "—"
        }
    }

    private fun renderCategoryChart(
        items: List<TransactionItem>,
        total: Double,
        legendContainer: LinearLayout,
        donutChart: DonutChartView,
        emptyMessage: String,
    ) {
        legendContainer.removeAllViews()
        val byCategory = items.groupBy { it.category }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
            .entries
            .sortedByDescending { it.value }

        if (byCategory.isEmpty()) {
            donutChart.segments = emptyList()
            val empty = TextView(requireContext())
            empty.text = emptyMessage
            empty.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            legendContainer.addView(empty)
            return
        }

        val segments = mutableListOf<DonutChartView.Segment>()
        val slices = mutableListOf<CategorySlice>()
        byCategory.forEachIndexed { index, entry ->
            val color = ContextCompat.getColor(requireContext(), donutColors[index % donutColors.size])
            val slice = CategorySlice(entry.key, entry.value, color)
            slices += slice
            segments.add(DonutChartView.Segment(entry.value.toFloat(), color, entry.key))

            val row = ItemReportLegendRowBinding.inflate(
                LayoutInflater.from(requireContext()),
                legendContainer,
                false,
            )
            val dot = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            row.legendDot.background = dot
            row.tvLegendLabel.text = entry.key
            val pct = if (total > 0) (entry.value / total * 100).roundToInt() else 0
            row.tvLegendPercent.text = "$pct%"
            row.tvLegendAmount.text = formatMoney(entry.value)
            row.root.setOnClickListener { showCategoryDetail(slice, total) }
            legendContainer.addView(row.root)
        }
        donutChart.segments = segments
        donutChart.onSegmentClick = { index, _ ->
            slices.getOrNull(index)?.let { showCategoryDetail(it, total) }
        }
    }

    private fun filterByPeriod(type: TransactionType, start: Long, end: Long): List<TransactionItem> =
        transactions.filter { it.type == type && it.createdAt in start..end }

    private fun compareHint(current: Double, previous: Double, expenseLabel: Boolean): String {
        if (previous <= 0.0) {
            return if (current <= 0.0) {
                "Нет данных за выбранный период"
            } else {
                val noun = if (expenseLabel) "расходов" else "доходов"
                "Нет $noun за прошлый период для сравнения"
            }
        }
        val deltaPct = ((current - previous) / previous * 100).roundToInt()
        val cmp = when {
            deltaPct < 0 -> "ниже"
            deltaPct > 0 -> "выше"
            else -> "на уровне"
        }
        val noun = if (expenseLabel) "Расходы" else "Доходы"
        return if (deltaPct == 0) {
            "$noun на уровне прошлого периода"
        } else {
            "$noun ${if (deltaPct > 0) "+" else ""}$deltaPct% $cmp по сравнению с прошлым периодом"
        }
    }

    private fun previousPeriodTotal(type: TransactionType): Double {
        val prev = anchor.clone() as Calendar
        when (periodTab) {
            PeriodTab.WEEK -> prev.add(Calendar.WEEK_OF_YEAR, -1)
            PeriodTab.MONTH -> prev.add(Calendar.MONTH, -1)
            PeriodTab.YEAR -> prev.add(Calendar.YEAR, -1)
        }
        val (s, e) = computeRange(prev, periodTab)
        return transactions.filter { it.type == type && it.createdAt in s..e }.sumOf { it.amount }
    }

    private fun buildDailySeries(
        items: List<TransactionItem>,
        start: Long,
        @Suppress("UNUSED_PARAMETER") end: Long,
    ): List<Float> {
        return when (periodTab) {
            PeriodTab.WEEK -> {
                val sums = FloatArray(7)
                for (t in items) {
                    val idx = ((t.createdAt - start) / 86400000L).toInt().coerceIn(0, 6)
                    sums[idx] += t.amount.toFloat()
                }
                sums.toList()
            }
            PeriodTab.MONTH -> {
                val cal = Calendar.getInstance().apply { timeInMillis = start }
                val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val sums = FloatArray(days)
                for (t in items) {
                    val c = Calendar.getInstance().apply { timeInMillis = t.createdAt }
                    val d = c.get(Calendar.DAY_OF_MONTH) - 1
                    if (d in sums.indices) sums[d] += t.amount.toFloat()
                }
                sums.toList()
            }
            PeriodTab.YEAR -> {
                val sums = FloatArray(12)
                for (t in items) {
                    val c = Calendar.getInstance().apply { timeInMillis = t.createdAt }
                    sums[c.get(Calendar.MONTH)] += t.amount.toFloat()
                }
                sums.toList()
            }
        }
    }

    private fun formatMoney(amount: Double): String =
        "${moneyFmt.format(amount.roundToLong())} ₽"

    private fun formatSignedMoney(amount: Double): String {
        val n = amount.roundToLong()
        val sign = when {
            n > 0 -> "+"
            n < 0 -> "−"
            else -> ""
        }
        return "$sign${moneyFmt.format(abs(n))} ₽"
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
            PeriodTab.MONTH -> {
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
            PeriodTab.MONTH -> {
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
        subscribedBudgetId = ""
        super.onDestroyView()
        _binding = null
    }
}
