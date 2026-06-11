package com.Popov.budgetapp

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.ImageViewCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.NavOptions
import com.Popov.budgetapp.databinding.ActivityMainBinding
import com.Popov.budgetapp.ui.SessionStore
import com.Popov.budgetapp.notification.NotificationPermissionHelper
import com.Popov.budgetapp.ui.ThemeManager
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var notificationPermissionHelper: NotificationPermissionHelper

    /** Нижний отступ контента над таб-баром (0 на экране входа). */
    private var navHostBottomInsetBasePx = 0

    private data class TabUi(
        val root: View,
        val indicator: View,
        val icon: ImageView,
        val label: TextView,
        val destId: Int,
    )

    private lateinit var tabs: List<TabUi>

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyStoredTheme(this)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navHostBottomInsetBasePx =
            if (FirebaseAuth.getInstance().currentUser != null) {
                resources.getDimensionPixelSize(R.dimen.budgets_bottom_inset)
            } else {
                0
            }

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        notificationPermissionHelper = NotificationPermissionHelper(this)
        notificationPermissionHelper.requestOnFirstLaunchIfNeeded()

        tabs = listOf(
            TabUi(
                binding.navTabBudgets,
                binding.indicatorBudgets,
                binding.ivNavBudgets,
                binding.tvNavBudgets,
                R.id.budgetsFragment,
            ),
            TabUi(
                binding.navTabTransactions,
                binding.indicatorTransactions,
                binding.ivNavTransactions,
                binding.tvNavTransactions,
                R.id.transactionsFragment,
            ),
            TabUi(
                binding.navTabReports,
                binding.indicatorReports,
                binding.ivNavReports,
                binding.tvNavReports,
                R.id.reportsFragment,
            ),
            TabUi(
                binding.navTabProfile,
                binding.indicatorProfile,
                binding.ivNavProfile,
                binding.tvNavProfile,
                R.id.profileFragment,
            ),
        )

        tabs.forEach { tab ->
            tab.root.setOnClickListener { navigateToTab(tab.destId) }
        }

        setupWindowInsets()

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val signedIn = FirebaseAuth.getInstance().currentUser != null
            val authFlow = destination.id == R.id.authFragment ||
                destination.id == R.id.registerFragment ||
                destination.id == R.id.setupNicknameFragment
            val showBottomNav = signedIn && !authFlow
            binding.bottomNavigation.visibility =
                if (showBottomNav) View.VISIBLE else View.GONE
            navHostBottomInsetBasePx =
                if (showBottomNav) resources.getDimensionPixelSize(R.dimen.budgets_bottom_inset) else 0
            ViewCompat.requestApplyInsets(binding.navHostFragment)
            if (binding.bottomNavigation.visibility == View.VISIBLE) {
                syncTabSelection(destination.id)
            }
        }
    }

    private fun setupWindowInsets() {
        val bottomNavBasePx = resources.getDimensionPixelSize(R.dimen.bottom_nav_padding_bottom)

        ViewCompat.setOnApplyWindowInsetsListener(binding.navHostFragment) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = navHostBottomInsetBasePx + bars.bottom,
            )
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(
                bottom = bottomNavBasePx + navBars.bottom,
            )
            insets
        }

        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun navigateToTab(destId: Int) {
        val current = navController.currentDestination?.id ?: return
        if (current == destId) return

        when (destId) {
            R.id.budgetsFragment -> {
                if (navController.popBackStack(R.id.budgetsFragment, false)) return
                navController.navigate(
                    R.id.action_global_budgets,
                    null,
                    NavOptions.Builder().setLaunchSingleTop(true).build(),
                )
            }
            R.id.transactionsFragment -> {
                if (SessionStore.selectedBudgetId.isBlank()) {
                    Toast.makeText(this, "Сначала выберите бюджет", Toast.LENGTH_SHORT).show()
                    if (current != R.id.budgetsFragment) {
                        navigateToTab(R.id.budgetsFragment)
                    }
                    return
                }
                when (current) {
                    R.id.budgetsFragment ->
                        navController.navigate(R.id.action_budgetsFragment_to_transactionsFragment)
                    R.id.profileFragment ->
                        navController.navigate(R.id.action_profileFragment_to_transactionsFragment)
                    R.id.transactionsFragment, R.id.budgetMembersFragment -> Unit
                    else -> {
                        navController.popBackStack(R.id.budgetsFragment, false)
                        navController.navigate(R.id.action_budgetsFragment_to_transactionsFragment)
                    }
                }
            }
            else -> {
                if (current == R.id.transactionsFragment || current == R.id.budgetMembersFragment) {
                    navController.popBackStack()
                }
                val options = NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setPopUpTo(R.id.budgetsFragment, inclusive = false, saveState = true)
                    .build()
                navController.navigate(destId, null, options)
            }
        }
    }

    private fun syncTabSelection(destinationId: Int) {
        val selectedIndex = when (destinationId) {
            R.id.budgetsFragment -> 0
            R.id.transactionsFragment, R.id.budgetMembersFragment -> 1
            R.id.reportsFragment -> 2
            R.id.profileFragment -> 3
            else -> null
        }
        val accent = ContextCompat.getColor(this, R.color.primary_warm)
        val muted = ContextCompat.getColor(this, R.color.bottom_nav_inactive)
        tabs.forEachIndexed { index, tab ->
            val selected = selectedIndex != null && selectedIndex == index
            tab.indicator.visibility = if (selected) View.VISIBLE else View.INVISIBLE
            val color = if (selected) accent else muted
            ImageViewCompat.setImageTintList(tab.icon, ColorStateList.valueOf(color))
            tab.label.setTextColor(color)
        }
    }
}
