package com.alertify.mobileapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.alertify.mobileapp.R
import com.alertify.core.ui.viewmodel.SharedMapViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint

/**
 * DashboardHostFragment - Contenedor principal después del login
 *
 * RESPONSABILIDADES:
 * - Mostrar BottomNavigationView con opciones: Mapa, Reportar, Historial, Ruteo
 * - Gestionar navegación entre módulos usando nested navigation
 * - Mantener el estado de la navegación
 *
 * ARQUITECTURA:
 * - Usa NavHostFragment anidado para cada sección
 * - BottomNavigationView controla qué NavHostFragment se muestra
 */
@AndroidEntryPoint
class DashboardHostFragment : Fragment() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var dashboardNavHost: NavHostFragment
    
    // Instancia compartida del ViewModel para comunicación entre Mapa y Ruteo
    private val sharedMapViewModel: SharedMapViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard_host, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bottomNav = view.findViewById(R.id.bottom_navigation)
        dashboardNavHost = childFragmentManager.findFragmentById(R.id.dashboard_nav_host) as NavHostFragment

        // Conectar BottomNavigationView con el NavController del dashboard
        NavigationUI.setupWithNavController(bottomNav, dashboardNavHost.navController)

        // Establecer icono por defecto
        bottomNav.selectedItemId = R.id.nav_mapa
    }
}
