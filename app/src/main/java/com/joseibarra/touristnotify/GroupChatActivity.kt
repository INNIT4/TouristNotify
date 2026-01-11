package com.joseibarra.touristnotify

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.joseibarra.touristnotify.databinding.ActivityGroupChatBinding

/**
 * Actividad para chat grupal en tiempo real
 */
class GroupChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupChatBinding
    private lateinit var chatAdapter: GroupChatAdapter
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var messagesRef: DatabaseReference

    private var groupId: String? = null
    private var groupName: String? = null
    private val messages = mutableListOf<GroupChatMessage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        groupId = intent.getStringExtra("GROUP_ID")
        groupName = intent.getStringExtra("GROUP_NAME")

        if (groupId == null) {
            NotificationHelper.error(binding.root, "Error: grupo no encontrado")
            finish()
            return
        }

        supportActionBar?.title = "$groupName - Chat"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        messagesRef = database.getReference("group_messages").child(groupId!!)

        setupRecyclerView()
        setupUI()
        listenToMessages()
    }

    private fun setupRecyclerView() {
        chatAdapter = GroupChatAdapter(auth.currentUser?.uid ?: "")

        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@GroupChatActivity).apply {
                stackFromEnd = true // Mostrar mensajes más recientes abajo
            }
            adapter = chatAdapter
        }
    }

    private fun setupUI() {
        binding.sendButton.setOnClickListener {
            sendMessage()
        }

        // Enviar al presionar Enter
        binding.messageEditText.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }
    }

    private fun sendMessage() {
        val messageText = binding.messageEditText.text.toString().trim()

        // Validación de mensaje vacío
        if (messageText.isEmpty()) {
            return
        }

        // SEGURIDAD: Validar longitud máxima del mensaje
        val MAX_MESSAGE_LENGTH = 500
        if (messageText.length > MAX_MESSAGE_LENGTH) {
            NotificationHelper.error(
                binding.root,
                "Mensaje muy largo. Máximo $MAX_MESSAGE_LENGTH caracteres"
            )
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            NotificationHelper.error(binding.root, "Debes iniciar sesión")
            return
        }

        // SEGURIDAD: Sanitizar el mensaje (remover caracteres de control y limitar espacios)
        val sanitizedMessage = messageText
            .replace(Regex("\\p{C}"), "") // Remover caracteres de control
            .replace(Regex("\\s+"), " ") // Normalizar espacios múltiples

        // Crear mensaje
        val messageId = messagesRef.push().key ?: return
        val message = GroupChatMessage(
            id = messageId,
            groupId = groupId!!,
            senderId = currentUser.uid,
            senderName = currentUser.displayName ?: "Usuario",
            message = sanitizedMessage,
            timestamp = System.currentTimeMillis()
        )

        // Enviar a Firebase
        messagesRef.child(messageId).setValue(message)
            .addOnSuccessListener {
                binding.messageEditText.text?.clear()
            }
            .addOnFailureListener {
                // SEGURIDAD: Mensaje de error genérico sin detalles
                NotificationHelper.error(binding.root, "Error al enviar mensaje. Intenta de nuevo")
            }
    }

    private fun listenToMessages() {
        messagesRef.orderByChild("timestamp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    messages.clear()

                    for (messageSnapshot in snapshot.children) {
                        val message = messageSnapshot.getValue(GroupChatMessage::class.java)
                        if (message != null) {
                            messages.add(message)
                        }
                    }

                    chatAdapter.submitList(messages.toList())

                    // Scroll al último mensaje
                    if (messages.isNotEmpty()) {
                        binding.chatRecyclerView.smoothScrollToPosition(messages.size - 1)
                    }

                    // Mostrar/ocultar empty state
                    if (messages.isEmpty()) {
                        binding.emptyStateTextView.visibility = View.VISIBLE
                    } else {
                        binding.emptyStateTextView.visibility = View.GONE
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    NotificationHelper.error(binding.root, "Error al cargar mensajes")
                }
            })
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
