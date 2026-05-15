package com.joseibarra.trazago

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes


enum class MenuItemId {
    GENERATE_ROUTE, THEMED_ROUTES, MY_ROUTES, VIEW_MAP,
    CONTACTS, SERVICES, TOP_PLACES, FAVORITES,
    EVENTS, BLOG, PROXIMITY, COMMUNITY_POSTS
}

enum class MenuColorScheme { PRIMARY, SECONDARY, TERTIARY }

data class MenuItemData(
    val id: MenuItemId,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int? = null,
    @DrawableRes val iconDrawableRes: Int? = null,
    val iconEmoji: String? = null,
    val colorScheme: MenuColorScheme,
    val spanFull: Boolean = false,
    val isLarge: Boolean = false,
    val showArrow: Boolean = false,
    val requiresAuth: String? = null,
    @StringRes val a11yDescRes: Int
)
