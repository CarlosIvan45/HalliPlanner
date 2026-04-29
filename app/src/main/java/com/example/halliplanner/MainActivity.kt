package com.example.halliplanner

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var bottomNav: BottomNavigationView
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        NotificationHelper.createChannels(this)
        requestNotificationPermissionIfNeeded()

        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            openAuth()
            return
        }
        if (SessionManager.isExpiredRememberSession(this)) {
            auth.signOut()
            SessionManager.clear(this)
            openAuth()
            return
        }

        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottom_nav)

        bottomNav.setOnItemSelectedListener {
            replaceFragment(fragmentForNavItem(it.itemId))
            true
        }

        val selectedItem = savedInstanceState?.getInt(KEY_SELECTED_NAV) ?: R.id.nav_home
        replaceFragment(fragmentForNavItem(selectedItem))
        if (bottomNav.selectedItemId != selectedItem) {
            bottomNav.selectedItemId = selectedItem
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.fragment_fade_in,
                R.anim.fragment_fade_out
            )
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun fragmentForNavItem(itemId: Int): Fragment {
        return when (itemId) {
            R.id.nav_schedule -> ScheduleFragment()
            R.id.nav_tasks -> TasksFragment()
            R.id.nav_operations -> OperationsFragment()
            R.id.nav_engineers -> EngineersFragment()
            R.id.nav_profile -> ProfileFragment()
            else -> HomeFragment()
        }
    }

    private fun openAuth() {
        val intent = Intent(this, AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            if (!SessionManager.isRememberActive(this)) {
                auth.signOut()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_SELECTED_NAV, bottomNav.selectedItemId)
        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val KEY_SELECTED_NAV = "selected_nav"
    }
}
