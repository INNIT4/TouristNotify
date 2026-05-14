package com.joseibarra.trazago

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

class MenuAdapter(
    private val items: List<MenuItemData>,
    private val isAuthenticated: Boolean,
    private val onItemClick: (MenuItemId) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_PRIMARY = 0
        const val TYPE_ACTION = 1
    }

    fun getSpanSize(position: Int): Int = if (items[position].spanFull) 2 else 1

    override fun getItemViewType(position: Int) =
        if (items[position].isLarge) TYPE_PRIMARY else TYPE_ACTION

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_PRIMARY -> PrimaryViewHolder(
                inflater.inflate(R.layout.item_menu_card_primary, parent, false)
            )
            else -> ActionViewHolder(
                inflater.inflate(R.layout.item_menu_card_action, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is PrimaryViewHolder -> holder.bind(item)
            is ActionViewHolder -> holder.bind(item)
        }
    }

    private fun bgColorRes(scheme: MenuColorScheme) = when (scheme) {
        MenuColorScheme.PRIMARY -> R.color.md_theme_light_primaryContainer
        MenuColorScheme.SECONDARY -> R.color.md_theme_light_secondaryContainer
        MenuColorScheme.TERTIARY -> R.color.md_theme_light_tertiaryContainer
    }

    private fun onContainerAttr(scheme: MenuColorScheme) = when (scheme) {
        MenuColorScheme.PRIMARY -> com.google.android.material.R.attr.colorOnPrimaryContainer
        MenuColorScheme.SECONDARY -> com.google.android.material.R.attr.colorOnSecondaryContainer
        MenuColorScheme.TERTIARY -> com.google.android.material.R.attr.colorOnTertiaryContainer
    }

    private fun setIconContainerBackground(container: FrameLayout, color: Int) {
        val density = container.context.resources.displayMetrics.density
        val radius = 14 * density
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = radius
        bg.setColor(color)
        container.background = bg
    }

    inner class PrimaryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: MaterialCardView = view.findViewById(R.id.menu_item_card)
        private val iconContainer: FrameLayout = view.findViewById(R.id.icon_container)
        private val iconView: ImageView = view.findViewById(R.id.item_icon)
        private val emojiView: TextView = view.findViewById(R.id.item_icon_emoji)
        private val titleView: TextView = view.findViewById(R.id.item_title)
        private val lockIcon: ImageView = view.findViewById(R.id.item_lock_icon)

        fun bind(item: MenuItemData) {
            val containerColor = ContextCompat.getColor(itemView.context, bgColorRes(item.colorScheme))
            val iconColor = MaterialColors.getColor(itemView, onContainerAttr(item.colorScheme), 0)

            card.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.card_background))
            setIconContainerBackground(iconContainer, containerColor)
            card.contentDescription = itemView.context.getString(item.a11yDescRes)

            titleView.text = itemView.context.getString(item.titleRes)
            titleView.setTextColor(MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnBackground, 0))

            when {
                item.iconDrawableRes != null -> {
                    iconView.visibility = View.VISIBLE
                    emojiView.visibility = View.GONE
                    iconView.setImageResource(item.iconDrawableRes)
                    iconView.setColorFilter(iconColor)
                    iconView.contentDescription = null
                }
                item.iconEmoji != null -> {
                    emojiView.visibility = View.VISIBLE
                    iconView.visibility = View.GONE
                    emojiView.text = item.iconEmoji
                }
            }

            lockIcon.visibility =
                if (item.requiresAuth != null && !isAuthenticated) View.VISIBLE else View.GONE
            if (lockIcon.visibility == View.VISIBLE) lockIcon.setColorFilter(iconColor)

            itemView.setOnClickListener { onItemClick(item.id) }
        }
    }

    inner class ActionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: MaterialCardView = view.findViewById(R.id.menu_item_card)
        private val iconContainer: FrameLayout = view.findViewById(R.id.icon_container)
        private val iconView: ImageView = view.findViewById(R.id.item_icon)
        private val emojiView: TextView = view.findViewById(R.id.item_icon_emoji)
        private val titleView: TextView = view.findViewById(R.id.item_title)
        private val subtitleView: TextView = view.findViewById(R.id.item_subtitle)
        private val arrowView: TextView = view.findViewById(R.id.item_arrow)
        private val lockIcon: ImageView = view.findViewById(R.id.item_lock_icon)

        fun bind(item: MenuItemData) {
            val containerColor = ContextCompat.getColor(itemView.context, bgColorRes(item.colorScheme))
            val iconColor = MaterialColors.getColor(itemView, onContainerAttr(item.colorScheme), 0)
            val onBg = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnBackground, 0)

            card.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.card_background))
            setIconContainerBackground(iconContainer, containerColor)
            card.contentDescription = itemView.context.getString(item.a11yDescRes)

            titleView.text = itemView.context.getString(item.titleRes)
            titleView.setTextColor(onBg)

            if (item.subtitleRes != null) {
                subtitleView.visibility = View.VISIBLE
                subtitleView.text = itemView.context.getString(item.subtitleRes)
                subtitleView.setTextColor(MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurfaceVariant, 0))
            } else {
                subtitleView.visibility = View.GONE
            }

            when {
                item.iconDrawableRes != null -> {
                    iconView.visibility = View.VISIBLE
                    emojiView.visibility = View.GONE
                    iconView.setImageResource(item.iconDrawableRes)
                    iconView.setColorFilter(iconColor)
                    iconView.contentDescription = null
                }
                item.iconEmoji != null -> {
                    emojiView.visibility = View.VISIBLE
                    iconView.visibility = View.GONE
                    emojiView.text = item.iconEmoji
                }
            }

            arrowView.visibility = if (item.showArrow) View.VISIBLE else View.GONE
            if (item.showArrow) arrowView.setTextColor(onBg)

            lockIcon.visibility =
                if (item.requiresAuth != null && !isAuthenticated) View.VISIBLE else View.GONE
            if (lockIcon.visibility == View.VISIBLE) lockIcon.setColorFilter(iconColor)

            itemView.setOnClickListener { onItemClick(item.id) }
        }
    }
}
