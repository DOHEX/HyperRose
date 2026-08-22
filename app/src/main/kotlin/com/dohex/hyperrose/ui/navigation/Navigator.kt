package com.dohex.hyperrose.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.snapshots.Snapshot
import top.yukonga.miuix.kmp.nav.core.NavBackStack

/**
 * 应用导航栈的唯一写入口。
 *
 * Route 只携带可序列化的导航参数；业务状态由页面从 Activity 持有的 Store 读取。所有
 * 栈变更在这里做去重，避免 Miuix NavDisplay 收到重复 content key。
 */
class Navigator(
    val backStack: NavBackStack,
) {
    val currentRoute: Route
        get() = checkNotNull(backStack.lastOrNull() as? Route) {
            "Navigation back stack cannot be empty"
        }

    fun replaceAll(vararg routes: Route) {
        require(routes.isNotEmpty()) { "Navigation stack cannot be empty" }
        require(routes.distinct().size == routes.size) {
            "Navigation stack routes must be unique"
        }
        require(routes.first() == Route.Main) {
            "Navigation stack must start with Main route"
        }
        require(routes.drop(1).none { it.isTopLevel }) {
            "Main route must be the only top-level route"
        }

        Snapshot.withMutableSnapshot {
            if (backStack.size == routes.size &&
                routes.indices.all { backStack[it] == routes[it] }
            ) {
                return@withMutableSnapshot
            }
            backStack.clear()
            backStack.addAll(routes.asList())
        }
    }

    /** Pushes a child, or pops to its existing instance when it is already in the stack. */
    fun push(route: Route) {
        require(!route.isTopLevel) {
            "Main route cannot be pushed as a child"
        }
        require(backStack.firstOrNull() == Route.Main) {
            "Navigation stack must start with Main route"
        }
        val existingIndex = backStack.indexOfLast { it == route }
        when {
            existingIndex == backStack.lastIndex -> Unit
            existingIndex >= 0 -> popToIndex(existingIndex)
            else -> backStack.add(route)
        }
    }

    /** Removes one child route; a root back is left for Activity to handle. */
    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeLast()
        return true
    }

    private fun popToIndex(index: Int) {
        while (backStack.lastIndex > index) backStack.removeLast()
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("LocalNavigator is not provided")
}
