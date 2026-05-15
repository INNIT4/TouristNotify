package com.joseibarra.trazago

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.trazago.databinding.ActivityEventDetailsBinding
import com.joseibarra.trazago.ui.BaseActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EventDetailsActivity : BaseActivity() {

    companion object {
        const val EXTRA_EVENT = "extra_event"
        const val EXTRA_EVENT_ID = "extra_event_id"
        private const val TAG = "EventDetails"
    }

    private lateinit var binding: ActivityEventDetailsBinding
    private lateinit var db: FirebaseFirestore
    private var event: Event? = null
    private var eventId: String? = null
    private val dateFormat = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        supportPostponeEnterTransition()
        super.onCreate(savedInstanceState)
        binding = ActivityEventDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.appBarLayout, applyTop = true, applyBottom = false)
        binding.heroImage.postDelayed({ supportStartPostponedEnterTransition() }, 500L)

        db = FirebaseFirestore.getInstance()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        loadEvent()
    }

    private fun loadEvent() {
        val parcelableEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_EVENT, Event::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_EVENT)
        }

        if (parcelableEvent != null) {
            event = parcelableEvent
            eventId = parcelableEvent.id
            renderEvent(parcelableEvent)
        } else {
            val id = intent.getStringExtra(EXTRA_EVENT_ID)
                ?: DeepLinkResolver.resolveEventId(intent.data ?: Uri.EMPTY)
            if (id != null) {
                eventId = id
                loadFromFirestore(id)
            } else {
                Toast.makeText(this, getString(R.string.event_not_found), Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun loadFromFirestore(id: String) {
        lifecycleScope.launch {
            try {
                val doc = db.collection(FirestoreCollections.EVENTS).document(id).get().await()
                val e = doc.toObject(Event::class.java)
                if (e != null) {
                    event = e
                    eventId = e.id
                    renderEvent(e)
                } else {
                    Toast.makeText(this@EventDetailsActivity, getString(R.string.event_not_found), Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (ex: Exception) {
                Toast.makeText(this@EventDetailsActivity, getString(R.string.event_load_error), Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun renderEvent(event: Event) {
        supportActionBar?.title = event.title

        // Hero image
        if (!event.imageUrl.isNullOrBlank()) {
            Glide.with(this)
                .load(SafeImageUrl.sanitize(event.imageUrl))
                .placeholder(R.drawable.gradient_event_placeholder)
                .into(binding.heroImage)
        } else {
            binding.heroImage.setImageResource(R.drawable.gradient_event_placeholder)
            binding.heroEmoji.text = CategoryUtils.getCategoryEmoji(event.category)
            binding.heroEmoji.visibility = View.VISIBLE
        }
        binding.heroImage.contentDescription = getString(R.string.a11y_event_detail_hero, event.title)

        // Badges
        renderBadges(event)
        binding.eventTitle.text = event.title
        binding.eventCategory.text = "${CategoryUtils.getCategoryEmoji(event.category)} ${event.category}"

        // Price
        binding.eventPrice.text = if (event.priceInfo.isNotBlank()) event.priceInfo
        else getString(R.string.event_price_free)

        // Dates
        val startStr = event.startDate?.let { dateFormat.format(it) } ?: "—"
        val endStr = event.endDate?.let { dateFormat.format(it) }
        binding.eventDates.text = if (endStr != null && endStr != startStr) "$startStr - $endStr" else startStr
        val startTime = event.startDate?.let { timeFormat.format(it) } ?: ""
        binding.eventTime.text = startTime

        // Description
        if (event.description.isNotBlank()) {
            binding.eventDescription.text = HtmlCompat.fromHtml(event.description, HtmlCompat.FROM_HTML_MODE_LEGACY)
            binding.eventDescription.visibility = View.VISIBLE
        } else {
            binding.eventDescription.visibility = View.GONE
        }

        // Location / map
        if (event.latitude != null && event.longitude != null) {
            binding.eventLocationText.text = event.location
            binding.eventLocation.visibility = View.VISIBLE
            setupMiniMap(event.latitude!!, event.longitude!!, event.title)
        } else if (!event.placeId.isNullOrBlank()) {
            binding.eventLocationText.text = event.location
            binding.eventLocation.visibility = View.VISIBLE
            binding.btnViewPlace.visibility = View.VISIBLE
            binding.btnViewPlace.setOnClickListener {
                val intent = Intent(this, PlaceDetailsActivity::class.java).apply {
                    putExtra("PLACE_ID", event.placeId)
                }
                startActivity(intent)
            }
            binding.miniMapContainer.visibility = View.GONE
        } else {
            binding.eventLocation.visibility = View.GONE
            binding.miniMapContainer.visibility = View.GONE
        }

        // Organizer
        if (event.organizerName.isNotBlank()) {
            binding.eventOrganizer.text = getString(R.string.event_organizer_label, event.organizerName)
            binding.eventOrganizer.visibility = View.VISIBLE
        }
        if (event.organizerContact.isNotBlank()) {
            binding.eventContact.visibility = View.VISIBLE
            binding.eventContact.setOnClickListener {
                val contact = event.organizerContact
                val intent = when {
                    contact.contains("@") -> Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:$contact")
                    }
                    else -> Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:$contact")
                    }
                }
                try { startActivity(intent) } catch (_: Exception) {}
            }
        }

        // Website
        if (event.websiteUrl.isNotBlank()) {
            binding.eventWebsite.visibility = View.VISIBLE
            binding.eventWebsite.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(event.websiteUrl))
                try { startActivity(intent) } catch (_: Exception) {}
            }
        }

        // Action buttons
        setupActionButtons(event)
    }

    private fun renderBadges(event: Event) {
        val today = Calendar.getInstance()
        val badges = mutableListOf<String>()

        event.startDate?.let { start ->
            val startCal = Calendar.getInstance().apply { time = start }
            if (startCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                startCal.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
                badges.add(getString(R.string.event_badge_today))
            } else if (start.before(today.apply { add(Calendar.DAY_OF_YEAR, 7) }.time) && start.after(today.time)) {
                badges.add(getString(R.string.event_badge_this_week))
            }
        }
        if (event.isFeatured) badges.add(getString(R.string.event_badge_featured))

        if (badges.isNotEmpty()) {
            binding.badgeChip1.text = badges[0]
            binding.badgeChip1.visibility = View.VISIBLE
        }
        if (badges.size > 1) {
            binding.badgeChip2.text = badges[1]
            binding.badgeChip2.visibility = View.VISIBLE
        }
    }

    private var mapFragment: SupportMapFragment? = null

    private fun setupMiniMap(lat: Double, lng: Double, title: String) {
        binding.miniMapContainer.visibility = View.VISIBLE
        mapFragment = supportFragmentManager.findFragmentById(R.id.mini_map) as? SupportMapFragment
            ?: SupportMapFragment.newInstance().also {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.mini_map, it)
                    .commit()
            }
        mapFragment?.getMapAsync { map ->
            val pos = LatLng(lat, lng)
            map.addMarker(MarkerOptions().position(pos).title(title))
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f))
            map.uiSettings.isScrollGesturesEnabled = false
            map.uiSettings.isZoomGesturesEnabled = false
            map.setOnMapClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng($title)"))
                try { startActivity(intent) } catch (_: Exception) {}
            }
            binding.miniMapContainer.contentDescription = getString(R.string.event_location_map_cd)
        }
    }

    private fun setupActionButtons(event: Event) {
        // Share
        binding.btnShare.setOnClickListener { shareEvent(event) }

        // Add to calendar (allowed for guests)
        binding.btnCalendar.setOnClickListener { addToCalendar(event) }

        // Remind me (locked for guests)
        if (AuthManager.isGuestMode(this)) {
            binding.btnReminder.isEnabled = false
            binding.btnReminder.alpha = 0.5f
            binding.btnReminder.text = "🔒"
            binding.btnReminder.setOnClickListener {
                Toast.makeText(this, getString(R.string.event_remind_me_locked), Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, LoginActivity::class.java))
            }
        } else {
            updateReminderButton(event)
            binding.btnReminder.setOnClickListener { showReminderDialog(event) }
        }
    }

    private fun updateReminderButton(event: Event) {
        binding.btnReminder.apply {
            isEnabled = true
            alpha = 1.0f
            text = if (EventReminderManager.isScheduled(this@EventDetailsActivity, event.id)) "🔔"
            else "🔔+"
        }
    }

    private fun showReminderDialog(event: Event) {
        if (EventReminderManager.isScheduled(this, event.id)) {
            EventReminderManager.cancelReminder(this, event.id)
            updateReminderButton(event)
            Toast.makeText(this, getString(R.string.event_reminder_cancelled), Toast.LENGTH_SHORT).show()
            return
        }

        val options = arrayOf(
            getString(R.string.event_reminder_30min),
            getString(R.string.event_reminder_1hour),
            getString(R.string.event_reminder_1day)
        )
        val minutes = longArrayOf(30L, 60L, 1440L)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.event_reminder_dialog_title))
            .setItems(options) { _, which ->
                EventReminderManager.scheduleReminder(this, event, minutes[which])
                updateReminderButton(event)
                Toast.makeText(this, getString(R.string.event_reminder_scheduled), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun shareEvent(event: Event) {
        val title = event.title
        val dates = event.startDate?.let { dateFormat.format(it) } ?: ""
        val location = event.location
        val deepLink = "trazago://event/${event.id}"
        val text = getString(R.string.event_share_template, title, dates, location, deepLink)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, title)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    private fun addToCalendar(event: Event) {
        val startMs = event.startDate?.time ?: return
        val endMs = event.endDate?.time ?: (startMs + 3_600_000)

        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, event.title)
            putExtra(CalendarContract.Events.DESCRIPTION, event.description)
            putExtra(CalendarContract.Events.EVENT_LOCATION, event.location)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMs)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.event_calendar_error), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
