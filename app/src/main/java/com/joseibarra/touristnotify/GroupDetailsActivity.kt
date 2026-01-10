package com.joseibarra.touristnotify

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.touristnotify.databinding.ActivityGroupDetailsBinding

/**
 * Actividad para ver detalles del grupo y miembros en tiempo real
 */
class GroupDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupDetailsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var firestore: FirebaseFirestore
    private lateinit var membersAdapter: GroupMembersAdapter

    private var groupId: String? = null
    private var groupName: String? = null
    private var currentGroup: TravelGroup? = null
    private val membersList = mutableListOf<GroupMember>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        firestore = FirebaseFirestore.getInstance()

        groupId = intent.getStringExtra("GROUP_ID")
        groupName = intent.getStringExtra("GROUP_NAME")

        if (groupId == null) {
            NotificationHelper.error(binding.root, "Error: grupo no encontrado")
            finish()
            return
        }

        supportActionBar?.title = groupName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        setupUI()
        loadGroupDetails()
        listenToMembers()
    }

    private fun setupRecyclerView() {
        membersAdapter = GroupMembersAdapter()

        binding.membersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@GroupDetailsActivity)
            adapter = membersAdapter
        }
    }

    private fun setupUI() {
        binding.openMapButton.setOnClickListener {
            openGroupMap()
        }

        binding.openChatButton.setOnClickListener {
            openGroupChat()
        }

        binding.shareCodeButton.setOnClickListener {
            shareGroupCode()
        }

        binding.leaveGroupButton.setOnClickListener {
            confirmLeaveGroup()
        }
    }

    private fun loadGroupDetails() {
        binding.progressBar.visibility = View.VISIBLE

        database.getReference("groups").child(groupId!!)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val group = snapshot.getValue(TravelGroup::class.java)
                    if (group != null) {
                        currentGroup = group
                        updateGroupInfo(group)
                    }
                    binding.progressBar.visibility = View.GONE
                }

                override fun onCancelled(error: DatabaseError) {
                    binding.progressBar.visibility = View.GONE
                    NotificationHelper.error(binding.root, "Error: ${error.message}")
                }
            })
    }

    private fun updateGroupInfo(group: TravelGroup) {
        binding.groupNameTextView.text = group.name
        binding.groupDescriptionTextView.text = group.description.ifBlank { "Sin descripción" }
        binding.groupCodeTextView.text = "Código: ${group.groupCode}"
        binding.memberCountTextView.text = "${group.memberIds.size} miembros"

        if (group.meetingPoint.isNotBlank()) {
            binding.meetingPointTextView.text = "📍 ${group.meetingPoint}"
        } else {
            binding.meetingPointTextView.text = "📍 Sin punto de encuentro"
        }

        // Mostrar/ocultar botón de salir según sea owner
        val isOwner = group.ownerId == auth.currentUser?.uid
        binding.leaveGroupButton.visibility = if (isOwner) View.GONE else View.VISIBLE
    }

    private fun listenToMembers() {
        val membersRef = database.getReference("group_members").child(groupId!!)

        membersRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                membersList.clear()

                for (memberSnapshot in snapshot.children) {
                    val member = memberSnapshot.getValue(GroupMember::class.java)
                    if (member != null) {
                        membersList.add(member)
                    }
                }

                // Si no hay miembros en tiempo real, crear lista básica desde el grupo
                if (membersList.isEmpty() && currentGroup != null) {
                    for (userId in currentGroup!!.memberIds) {
                        loadMemberInfo(userId)
                    }
                } else {
                    membersAdapter.submitList(membersList.toList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                NotificationHelper.error(binding.root, "Error al cargar miembros")
            }
        })
    }

    private fun loadMemberInfo(userId: String) {
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val userName = document.getString("displayName") ?: "Usuario"
                val member = GroupMember(
                    userId = userId,
                    userName = userName,
                    latitude = 0.0,
                    longitude = 0.0,
                    lastUpdate = null,
                    isOnline = false
                )
                membersList.add(member)
                membersAdapter.submitList(membersList.toList())
            }
    }

    private fun openGroupMap() {
        val intent = Intent(this, GroupMapActivity::class.java).apply {
            putExtra("GROUP_ID", groupId)
            putExtra("GROUP_NAME", groupName)
        }
        startActivity(intent)
    }

    private fun openGroupChat() {
        val intent = Intent(this, GroupChatActivity::class.java).apply {
            putExtra("GROUP_ID", groupId)
            putExtra("GROUP_NAME", groupName)
        }
        startActivity(intent)
    }

    private fun shareGroupCode() {
        if (currentGroup == null) return

        val shareText = "¡Únete a mi grupo de viaje en TouristNotify!\n\n" +
                "Grupo: ${currentGroup!!.name}\n" +
                "Código: ${currentGroup!!.groupCode}\n\n" +
                "Descarga la app y usa este código para unirte."

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, "Compartir código del grupo"))
    }

    private fun confirmLeaveGroup() {
        AlertDialog.Builder(this)
            .setTitle("Salir del Grupo")
            .setMessage("¿Estás seguro que deseas salir de este grupo?")
            .setPositiveButton("Salir") { _, _ ->
                leaveGroup()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun leaveGroup() {
        val currentUser = auth.currentUser ?: return
        if (currentGroup == null) return

        val updatedMembers = currentGroup!!.memberIds.toMutableList()
        updatedMembers.remove(currentUser.uid)

        database.getReference("groups").child(groupId!!).child("memberIds")
            .setValue(updatedMembers)
            .addOnSuccessListener {
                // Eliminar ubicación del miembro
                database.getReference("group_members").child(groupId!!).child(currentUser.uid)
                    .removeValue()

                NotificationHelper.success(binding.root, "Saliste del grupo")
                finish()
            }
            .addOnFailureListener { e ->
                NotificationHelper.error(binding.root, "Error: ${e.message}")
            }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_group_details, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_share_code -> {
                shareGroupCode()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
