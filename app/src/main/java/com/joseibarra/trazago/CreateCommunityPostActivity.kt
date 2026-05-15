package com.joseibarra.trazago

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.joseibarra.trazago.databinding.ActivityCreateCommunityPostBinding
import com.joseibarra.trazago.ui.BaseActivity
import kotlinx.coroutines.launch

class CreateCommunityPostActivity : BaseActivity() {

    private lateinit var binding: ActivityCreateCommunityPostBinding
    private lateinit var thumbAdapter: PostPhotoThumbAdapter

    private val selectedPhotos = mutableListOf<Uri>()
    private var selectedPlaceId = ""
    private var selectedPlaceName = ""
    private var editPostId: String? = null

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)
    ) { uris ->
        val available = 4 - selectedPhotos.size
        val toAdd = uris.take(available)
        selectedPhotos.addAll(toAdd)
        refreshPhotos()
    }

    companion object {
        const val EXTRA_EDIT_POST_ID = "edit_post_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateCommunityPostBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.root, applyTop = true, applyBottom = true)

        editPostId = intent.getStringExtra(EXTRA_EDIT_POST_ID)

        setupUI()

        if (editPostId != null) {
            loadExistingPost(editPostId!!)
        }
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Photo picker RecyclerView
        thumbAdapter = PostPhotoThumbAdapter { uri ->
            selectedPhotos.remove(uri)
            refreshPhotos()
        }
        binding.photosRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.photosRecycler.adapter = thumbAdapter

        binding.btnAddPhotos.setOnClickListener {
            if (selectedPhotos.size < 4) {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                NotificationHelper.warning(binding.root, getString(R.string.community_add_photos))
            }
        }

        // Place picker
        binding.btnTagPlace.setOnClickListener {
            supportFragmentManager.setFragmentResultListener(
                PlaceSelectorBottomSheet.RESULT_KEY, this
            ) { _, bundle ->
                selectedPlaceId = bundle.getString(PlaceSelectorBottomSheet.KEY_PLACE_ID).orEmpty()
                selectedPlaceName = bundle.getString(PlaceSelectorBottomSheet.KEY_PLACE_NAME).orEmpty()
                binding.taggedPlaceChip.text = selectedPlaceName
                binding.taggedPlaceChip.visibility =
                    if (selectedPlaceName.isNotBlank()) View.VISIBLE else View.GONE
            }
            PlaceSelectorBottomSheet().show(supportFragmentManager, PlaceSelectorBottomSheet.TAG)
        }

        binding.taggedPlaceChip.closeIconContentDescription =
            getString(R.string.community_remove_tagged_place)
        binding.taggedPlaceChip.setOnCloseIconClickListener {
            selectedPlaceId = ""
            selectedPlaceName = ""
            binding.taggedPlaceChip.visibility = View.GONE
        }

        binding.btnPublish.setOnClickListener { submitPost() }
    }

    private fun loadExistingPost(postId: String) {
        lifecycleScope.launch {
            CommunityRepository.getPost(postId).onSuccess { post ->
                binding.titleInput.setText(post.title)
                binding.contentInput.setText(post.content)
                selectedPlaceId = post.taggedPlaceId
                selectedPlaceName = post.taggedPlaceName
                if (selectedPlaceName.isNotBlank()) {
                    binding.taggedPlaceChip.text = selectedPlaceName
                    binding.taggedPlaceChip.visibility = View.VISIBLE
                }
                binding.btnPublish.text = getString(android.R.string.ok)
            }
        }
    }

    private fun refreshPhotos() {
        thumbAdapter.submitList(selectedPhotos.toList())
        binding.photosRecycler.visibility =
            if (selectedPhotos.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun submitPost() {
        val title = binding.titleInput.text?.toString()?.trim().orEmpty()
        val content = binding.contentInput.text?.toString()?.trim().orEmpty()

        if (title.isBlank()) {
            NotificationHelper.error(binding.root, getString(R.string.community_validation_title_required))
            return
        }
        if (content.isBlank()) {
            NotificationHelper.error(binding.root, getString(R.string.community_validation_content_required))
            return
        }

        val existingId = editPostId
        if (existingId != null) {
            updatePost(existingId, title, content)
            return
        }

        val user = FirebaseAuth.getInstance().currentUser ?: return
        val draft = NewPostDraft(
            authorId = user.uid,
            authorName = user.displayName ?: user.email ?: "Usuario",
            authorPhotoUrl = user.photoUrl?.toString() ?: "",
            title = title,
            content = content,
            placeId = selectedPlaceId,
            placeName = selectedPlaceName
        )

        setPublishing(true)
        lifecycleScope.launch {
            CommunityRepository.createPost(draft, selectedPhotos).onSuccess {
                NotificationHelper.success(binding.root, getString(R.string.community_publish_success))
                finish()
            }.onFailure { e ->
                setPublishing(false)
                FirestoreErrorHandler.handleError(this@CreateCommunityPostActivity, binding.root, e,
                    "publicar")
            }
        }
    }

    private fun updatePost(postId: String, title: String, content: String) {
        setPublishing(true)
        lifecycleScope.launch {
            CommunityRepository.updatePost(postId, title, content).onSuccess {
                finish()
            }.onFailure { e ->
                setPublishing(false)
                FirestoreErrorHandler.handleError(this@CreateCommunityPostActivity, binding.root, e,
                    "guardar cambios")
            }
        }
    }

    private fun setPublishing(publishing: Boolean) {
        binding.btnPublish.isEnabled = !publishing
        binding.btnPublish.text = if (publishing)
            getString(R.string.community_publishing)
        else
            getString(R.string.community_publish)
    }
}

