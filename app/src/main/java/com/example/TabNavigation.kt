package com.example

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

/** A directly opened tab may have been saved under the graph's start entry. Restoring that
 * alias while returning to the start tab can reopen Settings instead of Home. Keep the real
 * start entry, and restore saved stacks only for the other tabs. */
internal fun NavHostController.navigateToTab(route: String) {
    if (currentDestination?.route == route) return
    val start = graph.findStartDestination()
    navigate(route) {
        popUpTo(start.id) { saveState = true }
        launchSingleTop = true
        restoreState = route != start.route
    }
}
