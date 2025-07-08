package com.example.triptogether

import android.os.Bundle
import android.view.View
import okhttp3.Request
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ChatMessage(val message: String, val isUser: Boolean)

class Revgpt : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var inputEditText: EditText
    private lateinit var sendButton: MaterialButton
    private lateinit var shimmerLayout: ShimmerFrameLayout
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    private val conversationHistory = mutableListOf<String>() // Store all user messages

    private val API_KEY = "AIzaSyAmCIQw2_g-rJrZjlftVNLa4h_m6cfsgzM"
    private val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_revgpt)

        recyclerView = findViewById(R.id.chatRecyclerView)
        inputEditText = findViewById(R.id.inputLayout)
        sendButton = findViewById(R.id.sendButton)
        shimmerLayout = findViewById(R.id.shimmerLayout)
        val backButton = findViewById<ImageButton>(R.id.backButton)

        chatAdapter = ChatAdapter(chatMessages)
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@Revgpt)
            adapter = chatAdapter
        }

        sendButton.setOnClickListener {
            val message = inputEditText.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
                inputEditText.text.clear()
            }
        }
        backButton.setOnClickListener {
            finish() // Navigate back to the previous activity
        }
    }

    private fun sendMessage(message: String) {
        chatMessages.add(ChatMessage(message, true))
        conversationHistory.add("User: $message") // Add user message to history
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        recyclerView.scrollToPosition(chatMessages.size - 1)

        shimmerLayout.visibility = View.VISIBLE
        shimmerLayout.startShimmer()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = getGeminiResponse(conversationHistory)
                withContext(Dispatchers.Main) {
                    shimmerLayout.stopShimmer()
                    shimmerLayout.visibility = View.GONE
                    chatMessages.add(ChatMessage(response, false))
                    chatAdapter.notifyItemInserted(chatMessages.size - 1)
                    recyclerView.scrollToPosition(chatMessages.size - 1)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    shimmerLayout.stopShimmer()
                    shimmerLayout.visibility = View.GONE
                    chatMessages.add(ChatMessage("Error: ${e.message}", false))
                    chatAdapter.notifyItemInserted(chatMessages.size - 1)
                    recyclerView.scrollToPosition(chatMessages.size - 1)
                }
            }
        }
    }

    private suspend fun getGeminiResponse(history: List<String>): String {
        // Construct the full conversation context
        val fullPrompt = history.joinToString("\n") + "\nAssistant: "
        val json = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", fullPrompt)
                }))
            }))
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$API_URL?key=$API_KEY")
            .post(requestBody)
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "No error details"
                    throw IOException("Unexpected code ${response.code} - ${response.message}, URL: ${response.request.url}, Error: $errorBody")
                }

                val responseBody = response.body?.string()
                val jsonResponse = JSONObject(responseBody)
                jsonResponse.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .removePrefix(fullPrompt) // Remove the prompt from the response
            }
        }
    }
}
