package com.joseibarra.touristnotify

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.joseibarra.touristnotify.databinding.ActivityChatBinding
import kotlinx.coroutines.launch

/**
 * Actividad para el chat con IA sobre Álamos
 * Usa Gemini API para responder preguntas turísticas
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Chat con Alamitos 🤖"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        initializeGemini()
        setupUI()
        showWelcomeMessage()
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }
    }

    private fun initializeGemini() {
        try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isBlank()) {
                NotificationHelper.error(binding.root, "API Key no configurada")
                return
            }
            GeminiChatManager.initialize(apiKey)
        } catch (e: Exception) {
            NotificationHelper.error(binding.root, "Error al inicializar IA: ${e.message}")
        }
    }

    private fun setupUI() {
        binding.sendButton.setOnClickListener {
            sendMessage()
        }

        binding.messageEditText.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }

        binding.clearChatButton.setOnClickListener {
            clearChat()
        }
    }

    private fun showWelcomeMessage() {
        val welcomeMessage = ChatMessage(
            text = "¡Hola! 👋 Soy Alamitos, tu asistente turístico personal de Álamos, Sonora.\n\n" +
                    "Puedo ayudarte con:\n" +
                    "• Información sobre lugares turísticos\n" +
                    "• Recomendaciones de restaurantes\n" +
                    "• Historia y cultura de Álamos\n" +
                    "• Eventos y festividades\n" +
                    "• Consejos de viaje\n\n" +
                    "¿Qué te gustaría saber sobre este Pueblo Mágico?",
            isFromUser = false,
            timestamp = System.currentTimeMillis()
        )
        messages.add(welcomeMessage)
        chatAdapter.submitList(messages.toList())
    }

    private fun sendMessage() {
        val messageText = binding.messageEditText.text.toString().trim()

        if (messageText.isBlank()) {
            return
        }

        // SEGURIDAD: Validar longitud máxima del mensaje
        val MAX_MESSAGE_LENGTH = 1000 // Más largo para preguntas complejas a IA
        if (messageText.length > MAX_MESSAGE_LENGTH) {
            NotificationHelper.error(
                binding.root,
                "Pregunta muy larga. Máximo $MAX_MESSAGE_LENGTH caracteres"
            )
            return
        }

        // SEGURIDAD: Sanitizar el mensaje
        val sanitizedMessage = messageText
            .replace(Regex("\\p{C}"), "") // Remover caracteres de control
            .replace(Regex("\\s+"), " ") // Normalizar espacios

        // Deshabilitar input mientras se procesa
        binding.messageEditText.isEnabled = false
        binding.sendButton.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        // Agregar mensaje del usuario
        val userMessage = ChatMessage(
            text = sanitizedMessage,
            isFromUser = true,
            timestamp = System.currentTimeMillis()
        )
        messages.add(userMessage)
        chatAdapter.submitList(messages.toList())
        binding.chatRecyclerView.smoothScrollToPosition(messages.size - 1)

        // Limpiar input
        binding.messageEditText.text?.clear()

        // Enviar mensaje a Gemini
        lifecycleScope.launch {
            val result = GeminiChatManager.sendMessage(sanitizedMessage)

            result.onSuccess { aiResponse ->
                // Agregar respuesta de la IA
                val aiMessage = ChatMessage(
                    text = aiResponse,
                    isFromUser = false,
                    timestamp = System.currentTimeMillis()
                )
                messages.add(aiMessage)
                chatAdapter.submitList(messages.toList())
                binding.chatRecyclerView.smoothScrollToPosition(messages.size - 1)
            }.onFailure { error ->
                NotificationHelper.error(
                    binding.root,
                    "Error: ${error.message ?: "No se pudo obtener respuesta"}"
                )

                // Agregar mensaje de error
                val errorMessage = ChatMessage(
                    text = "Lo siento, hubo un error al procesar tu mensaje. Por favor intenta de nuevo.",
                    isFromUser = false,
                    timestamp = System.currentTimeMillis()
                )
                messages.add(errorMessage)
                chatAdapter.submitList(messages.toList())
            }

            // Re-habilitar input
            binding.progressBar.visibility = View.GONE
            binding.messageEditText.isEnabled = true
            binding.sendButton.isEnabled = true
            binding.messageEditText.requestFocus()
        }
    }

    private fun clearChat() {
        messages.clear()
        GeminiChatManager.clearHistory()
        chatAdapter.submitList(emptyList())
        showWelcomeMessage()
        NotificationHelper.info(binding.root, "Conversación reiniciada")
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpiar historial al salir
        GeminiChatManager.clearHistory()
    }
}
