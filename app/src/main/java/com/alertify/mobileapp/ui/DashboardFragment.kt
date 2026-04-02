package com.alertify.mobileapp.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.alertify.core.storage.AuthSessionManager
import com.alertify.mobileapp.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

	@Inject
	lateinit var sessionManager: AuthSessionManager

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		val logoutButton = view.findViewById<Button>(R.id.dashboard_logout_button)
		logoutButton.setOnClickListener {
			logoutButton.isEnabled = false
			viewLifecycleOwner.lifecycleScope.launch {
				sessionManager.logout()
				logoutButton.isEnabled = true
			}
		}
	}
}
