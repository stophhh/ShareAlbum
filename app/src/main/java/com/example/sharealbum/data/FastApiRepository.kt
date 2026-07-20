package com.example.sharealbum.data

import com.example.sharealbum.model.AgentCommandRequest
import com.example.sharealbum.model.AgentCommandResponse
import com.example.sharealbum.model.AgentPhotoItem
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

interface AiApi {
    @POST("/agent/command")
    suspend fun sendCommand(
        @Body request: AgentCommandRequest
    ): AgentCommandResponse
}

class FastApiRepository {

    private val api: AiApi = Retrofit.Builder()
        .baseUrl("http://127.0.0.1:8000/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AiApi::class.java)

    suspend fun sendAgentCommand(
        userId: String,
        message: String,
        photos: List<AgentPhotoItem> = emptyList()
    ): AgentCommandResponse {
        return api.sendCommand(
            AgentCommandRequest(
                user_id = userId,
                message = message,
                photos = photos
            )
        )
    }
}
