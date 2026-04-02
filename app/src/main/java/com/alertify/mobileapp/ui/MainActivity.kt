package com.alertify.mobileapp.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import com.alertify.core.session.SessionEvent
import com.alertify.core.session.SessionEventBus
import com.alertify.mobileapp.R
import com.alertify.feature_identidad.navigation.LoginNavigator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), LoginNavigator {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionEventBus.events.collect { event ->
                    handleSessionEvent(event)
                }
            }
        }
    }

    override fun onLoginSuccess() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.main_nav_host) as NavHostFragment
        val navController = navHostFragment.navController
        if (navController.currentDestination?.id != R.id.dashboardFragment) {
            navController.navigate(R.id.action_loginFragment_to_dashboardFragment)
        }
    }

    private fun handleSessionEvent(event: SessionEvent) {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.main_nav_host) as NavHostFragment
        val navController = navHostFragment.navController

        when (event) {
            is SessionEvent.SessionExpired -> {
                if (navController.currentDestination?.id != R.id.loginFragment) {
                    Toast.makeText(this, getString(R.string.session_expired), Toast.LENGTH_LONG).show()
                    navController.navigate(R.id.action_dashboardFragment_to_loginFragment)
                }
            }

            is SessionEvent.LogoutSuccess -> {
                if (navController.currentDestination?.id != R.id.loginFragment) {
                    Toast.makeText(this, getString(R.string.logout_success), Toast.LENGTH_SHORT).show()
                    navController.navigate(R.id.action_dashboardFragment_to_loginFragment)
                }
            }
        }
    }
}