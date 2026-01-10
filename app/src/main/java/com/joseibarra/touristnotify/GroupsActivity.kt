package com.joseibarra.touristnotify

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.touristnotify.databinding.ActivityGroupsBinding
import com.google.android.material.textfield.TextInputEditText
import java.util.*

/**
 * Actividad para gestionar grupos de viaje
 * Lista grupos, permite crear y unirse a grupos
 */
class GroupsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var firestore: FirebaseFirestore
    private lateinit var groupsAdapter: GroupsAdapter
    private val userGroups = mutableListOf<TravelGroup>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        firestore = FirebaseFirestore.getInstance()

        supportActionBar?.title = "Mis Grupos"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        setupUI()
        loadUserGroups()
    }

    private fun setupRecyclerView() {
        groupsAdapter = GroupsAdapter { group ->
            openGroupDetails(group)
        }

        binding.groupsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@GroupsActivity)
            adapter = groupsAdapter
        }
    }

    private fun setupUI() {
        binding.fabCreateGroup.setOnClickListener {
            showCreateGroupDialog()
        }

        binding.joinGroupButton.setOnClickListener {
            showJoinGroupDialog()
        }
    }

    private fun loadUserGroups() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            NotificationHelper.error(binding.root, "Debes iniciar sesión")
            finish()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.emptyStateTextView.visibility = View.GONE

        val groupsRef = database.getReference("groups")

        groupsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userGroups.clear()

                for (groupSnapshot in snapshot.children) {
                    val group = groupSnapshot.getValue(TravelGroup::class.java)
                    if (group != null) {
                        // Mostrar solo grupos donde el usuario es owner o miembro
                        if (group.ownerId == currentUser.uid ||
                            group.memberIds.contains(currentUser.uid)) {
                            userGroups.add(group)
                        }
                    }
                }

                binding.progressBar.visibility = View.GONE

                if (userGroups.isEmpty()) {
                    binding.emptyStateTextView.visibility = View.VISIBLE
                    binding.emptyStateTextView.text = "👥\n\nNo tienes grupos\n\n" +
                            "Crea un grupo nuevo o únete a uno existente"
                } else {
                    groupsAdapter.submitList(userGroups.toList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                binding.progressBar.visibility = View.GONE
                NotificationHelper.error(binding.root, "Error: ${error.message}")
            }
        })
    }

    private fun showCreateGroupDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_group, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.group_name_input)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.group_description_input)
        val meetingPointInput = dialogView.findViewById<TextInputEditText>(R.id.meeting_point_input)

        AlertDialog.Builder(this)
            .setTitle("Crear Grupo")
            .setView(dialogView)
            .setPositiveButton("Crear") { dialog, _ ->
                val name = nameInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim()
                val meetingPoint = meetingPointInput.text.toString().trim()

                if (name.isBlank()) {
                    NotificationHelper.error(binding.root, "El nombre es requerido")
                    return@setPositiveButton
                }

                createGroup(name, description, meetingPoint)
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun createGroup(name: String, description: String, meetingPoint: String) {
        val currentUser = auth.currentUser ?: return

        binding.progressBar.visibility = View.VISIBLE

        val groupId = database.getReference("groups").push().key ?: return
        val groupCode = generateGroupCode()

        val group = TravelGroup(
            id = groupId,
            name = name,
            description = description,
            ownerId = currentUser.uid,
            memberIds = listOf(currentUser.uid),
            currentRouteId = "",
            isActive = true,
            createdAt = Date(),
            meetingPoint = meetingPoint,
            groupCode = groupCode
        )

        database.getReference("groups").child(groupId).setValue(group)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                NotificationHelper.success(binding.root, "✓ Grupo creado: $groupCode")

                // Mostrar código del grupo
                showGroupCodeDialog(groupCode, name)
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                NotificationHelper.error(binding.root, "Error: ${e.message}")
            }
    }

    private fun showGroupCodeDialog(groupCode: String, groupName: String) {
        AlertDialog.Builder(this)
            .setTitle("Grupo Creado ✓")
            .setMessage("Tu grupo '$groupName' ha sido creado.\n\n" +
                    "Código del grupo:\n$groupCode\n\n" +
                    "Comparte este código con otros viajeros para que se unan.")
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun showJoinGroupDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_join_group, null)
        val codeInput = dialogView.findViewById<TextInputEditText>(R.id.group_code_input)

        AlertDialog.Builder(this)
            .setTitle("Unirse a Grupo")
            .setView(dialogView)
            .setPositiveButton("Unirse") { dialog, _ ->
                val code = codeInput.text.toString().trim().uppercase()

                if (code.isBlank()) {
                    NotificationHelper.error(binding.root, "El código es requerido")
                    return@setPositiveButton
                }

                joinGroup(code)
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun joinGroup(groupCode: String) {
        val currentUser = auth.currentUser ?: return

        binding.progressBar.visibility = View.VISIBLE

        val groupsRef = database.getReference("groups")

        groupsRef.orderByChild("groupCode").equalTo(groupCode)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (groupSnapshot in snapshot.children) {
                            val group = groupSnapshot.getValue(TravelGroup::class.java)
                            if (group != null) {
                                // Verificar si ya es miembro
                                if (group.memberIds.contains(currentUser.uid)) {
                                    binding.progressBar.visibility = View.GONE
                                    NotificationHelper.warning(binding.root, "Ya eres miembro de este grupo")
                                    return
                                }

                                // Agregar usuario al grupo
                                val updatedMembers = group.memberIds.toMutableList()
                                updatedMembers.add(currentUser.uid)

                                groupSnapshot.ref.child("memberIds").setValue(updatedMembers)
                                    .addOnSuccessListener {
                                        binding.progressBar.visibility = View.GONE
                                        NotificationHelper.success(binding.root, "✓ Te uniste al grupo: ${group.name}")
                                    }
                                    .addOnFailureListener { e ->
                                        binding.progressBar.visibility = View.GONE
                                        NotificationHelper.error(binding.root, "Error: ${e.message}")
                                    }
                                return
                            }
                        }
                    } else {
                        binding.progressBar.visibility = View.GONE
                        NotificationHelper.error(binding.root, "Código de grupo inválido")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    binding.progressBar.visibility = View.GONE
                    NotificationHelper.error(binding.root, "Error: ${error.message}")
                }
            })
    }

    private fun generateGroupCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6)
            .map { chars.random() }
            .joinToString("")
    }

    private fun openGroupDetails(group: TravelGroup) {
        val intent = Intent(this, GroupDetailsActivity::class.java).apply {
            putExtra("GROUP_ID", group.id)
            putExtra("GROUP_NAME", group.name)
        }
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
