package com.example.sharealbum.data

import com.example.sharealbum.model.AiPhotoResult
import com.google.firebase.functions.FirebaseFunctions

private const val FUNCTIONS_REGION = "asia-northeast3"

private val functions: FirebaseFunctions
    get() = FirebaseFunctions.getInstance(FUNCTIONS_REGION)

fun requestPhotoAnalysis(
    albumId: String,
    photoId: String,
    imageUrl: String,
    onError: (Exception) -> Unit = {}
) {
    val data = hashMapOf(
        "albumId" to albumId,
        "photoId" to photoId,
        "imageUrl" to imageUrl
    )

    functions
        .getHttpsCallable("analyzePhoto")
        .call(data)
        .addOnFailureListener { onError(it) }
}

fun searchPhotosWithAi(
    query: String,
    albumId: String? = null,
    onSuccess: (List<AiPhotoResult>) -> Unit,
    onError: (Exception) -> Unit
) {
    val data = hashMapOf<String, Any>("query" to query)
    if (albumId != null) data["albumId"] = albumId

    functions
        .getHttpsCallable("searchPhotos")
        .call(data)
        .addOnSuccessListener { result ->
            val payload = result.data as? Map<*, *>
            val photos = payload?.get("photos") as? List<*> ?: emptyList<Any>()
            onSuccess(photos.mapNotNull { it.toAiPhotoResult() })
        }
        .addOnFailureListener { onError(it) }
}

fun createAlbumWithAi(
    prompt: String,
    onSuccess: (String, Int) -> Unit,
    onError: (Exception) -> Unit
) {
    val data = hashMapOf<String, Any>("prompt" to prompt)

    functions
        .getHttpsCallable("createAlbumFromPrompt")
        .call(data)
        .addOnSuccessListener { result ->
            val payload = result.data as? Map<*, *>
            val albumId = payload?.get("albumId") as? String
            val count = (payload?.get("count") as? Number)?.toInt() ?: 0
            if (albumId.isNullOrBlank()) {
                onError(IllegalStateException("조건에 맞는 사진을 찾지 못했습니다."))
            } else {
                onSuccess(albumId, count)
            }
        }
        .addOnFailureListener { onError(it) }
}

private fun Any?.toAiPhotoResult(): AiPhotoResult? {
    val map = this as? Map<*, *> ?: return null
    return AiPhotoResult(
        albumId = map["albumId"] as? String ?: "",
        albumTitle = map["albumTitle"] as? String ?: "",
        photoId = map["photoId"] as? String ?: "",
        imageUrl = map["imageUrl"] as? String ?: return null,
        aiCaption = map["aiCaption"] as? String ?: "",
        aiCategory = map["aiCategory"] as? String ?: "",
        aiLocation = map["aiLocation"] as? String ?: "",
        score = (map["score"] as? Number)?.toDouble() ?: 0.0
    )
}
