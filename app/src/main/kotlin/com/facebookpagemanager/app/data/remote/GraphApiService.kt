package com.facebookpagemanager.app.data.remote

import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import okhttp3.MultipartBody
import okhttp3.RequestBody

/**
 * Official Meta Graph API (v20.0) endpoints used by the app.
 * Every call can fail with a Graph error (permissions / App Review) — the
 * repository translates those into honest, actionable messages.
 */
interface GraphApiService {

    @GET("{ver}/me")
    suspend fun getMe(
        @Path("ver") ver: String,
        @Query("access_token") token: String,
        @Query("fields") fields: String = "id,name",
    ): MeResponse

    @GET("{ver}/me/accounts")
    suspend fun getAccounts(
        @Path("ver") ver: String,
        @Query("access_token") token: String,
        @Query("fields") fields: String = "id,name,category,access_token,picture{url},followers_count",
        @Query("limit") limit: Int = 100,
    ): AccountsResponse

    @GET("{ver}/{pageId}")
    suspend fun getPage(
        @Path("ver") ver: String,
        @Path("pageId") pageId: String,
        @Query("access_token") token: String,
        @Query("fields") fields: String = "id,name,category,followers_count,fan_count,picture{url}",
    ): PageDetailResponse

    @GET("{ver}/{pageId}/insights")
    suspend fun getInsights(
        @Path("ver") ver: String,
        @Path("pageId") pageId: String,
        @Query("metric") metric: String,
        @Query("period") period: String = "day",
        @Query("access_token") token: String,
        @Query("since") since: Long? = null,
        @Query("until") until: Long? = null,
    ): InsightsResponse

    @GET("{ver}/{pageId}/posts")
    suspend fun getPosts(
        @Path("ver") ver: String,
        @Path("pageId") pageId: String,
        @Query("access_token") token: String,
        @Query("fields") fields: String = "id,message,link,created_time,permalink_url,likes.summary(true),comments.summary(true),shares,full_picture",
        @Query("limit") limit: Int = 25,
    ): PostsResponse

    @FormUrlEncoded
    @POST("{ver}/{pageId}/feed")
    suspend fun createFeedPost(
        @Path("ver") ver: String,
        @Path("pageId") pageId: String,
        @Field("message") message: String,
        @Field("link") link: String?,
        @Field("published") published: Boolean,
        @Field("scheduled_publish_time") scheduledPublishTime: Long?,
        @Field("access_token") token: String,
    ): CreateResponse

    @Multipart
    @POST("{ver}/{pageId}/photos")
    suspend fun uploadPhoto(
        @Path("ver") ver: String,
        @Path("pageId") pageId: String,
        @Part source: MultipartBody.Part,
        @Part("caption") caption: RequestBody?,
        @Part("published") published: RequestBody?,
        @Part("scheduled_publish_time") scheduledPublishTime: RequestBody?,
        @Part("access_token") token: RequestBody,
    ): CreateResponse

    @FormUrlEncoded
    @POST("{ver}/{pageId}/photos")
    suspend fun publishPhotoUrl(
        @Path("ver") ver: String,
        @Path("pageId") pageId: String,
        @Field("url") url: String,
        @Field("caption") caption: String?,
        @Field("published") published: Boolean,
        @Field("scheduled_publish_time") scheduledPublishTime: Long?,
        @Field("access_token") token: String,
    ): CreateResponse

    @GET("{ver}/{pageId}/scheduled_posts")
    suspend fun getScheduledPosts(
        @Path("ver") ver: String,
        @Path("pageId") pageId: String,
        @Query("access_token") token: String,
        @Query("fields") fields: String = "id,message,scheduled_publish_time,created_time",
        @Query("limit") limit: Int = 50,
    ): ScheduledPostsResponse

    @DELETE("{ver}/{objectId}")
    suspend fun deleteObject(
        @Path("ver") ver: String,
        @Path("objectId") objectId: String,
        @Query("access_token") token: String,
    ): DeleteResponse

    @GET("{ver}/{pageId}/conversations")
    suspend fun getConversations(
        @Path("ver") ver: String,
        @Path("pageId") pageId: String,
        @Query("access_token") token: String,
        @Query("fields") fields: String = "id,snippet,updated_time,unread_count,participants",
        @Query("limit") limit: Int = 25,
    ): ConversationsResponse

    @GET("{ver}/{conversationId}")
    suspend fun getMessages(
        @Path("ver") ver: String,
        @Path("conversationId") conversationId: String,
        @Query("access_token") token: String,
        @Query("fields") fields: String = "messages{id,message,from,created_time}",
    ): ConversationDetailResponse

    @FormUrlEncoded
    @POST("{ver}/{conversationId}/messages")
    suspend fun sendMessage(
        @Path("ver") ver: String,
        @Path("conversationId") conversationId: String,
        @Field("message") message: String,
        @Field("access_token") token: String,
    ): CreateResponse

    @GET("{ver}/{postId}/comments")
    suspend fun getComments(
        @Path("ver") ver: String,
        @Path("postId") postId: String,
        @Query("access_token") token: String,
        @Query("fields") fields: String = "id,message,from,created_time,like_count,is_hidden",
        @Query("limit") limit: Int = 50,
    ): CommentsResponse

    @FormUrlEncoded
    @POST("{ver}/{objectId}/comments")
    suspend fun replyToComment(
        @Path("ver") ver: String,
        @Path("objectId") objectId: String,
        @Field("message") message: String,
        @Field("access_token") token: String,
    ): CreateResponse

    @FormUrlEncoded
    @POST("{ver}/{commentId}")
    suspend fun setCommentHidden(
        @Path("ver") ver: String,
        @Path("commentId") commentId: String,
        @Field("is_hidden") isHidden: Boolean,
        @Field("access_token") token: String,
    ): DeleteResponse

    // --- Reels (limited availability — Meta enables per app) ---
    @FormUrlEncoded
    @POST("{ver}/{pageId}/video_reels")
    suspend fun startReelUpload(
        @Path("ver") ver: String,
        @Path("pageId") pageId: String,
        @Field("upload_phase") uploadPhase: String = "start",
        @Field("access_token") token: String,
    ): ReelUploadResponse

    @FormUrlEncoded
    @POST("{ver}/{pageId}/video_reels")
    suspend fun finishReelUpload(
        @Path("ver") ver: String,
        @Path("pageId") pageId: String,
        @Field("upload_phase") uploadPhase: String = "finish",
        @Field("video_id") videoId: String,
        @Field("description") description: String?,
        @Field("access_token") token: String,
    ): CreateResponse
}

/** Video uploads go to the dedicated graph-video host. */
interface GraphVideoService {
    @Multipart
    @POST("{ver}/{pageId}/videos")
    suspend fun uploadVideo(
        @Path("ver") ver: String,
        @Path("pageId") pageId: String,
        @Part source: MultipartBody.Part,
        @Part("description") description: RequestBody?,
        @Part("access_token") token: RequestBody,
    ): CreateResponse

    /** Second phase of a Reels upload: the raw bytes go to the returned video_id. */
    @Multipart
    @POST("{ver}/{videoId}")
    suspend fun uploadReelChunk(
        @Path("ver") ver: String,
        @Path("videoId") videoId: String,
        @Part file: MultipartBody.Part,
        @Part("access_token") token: RequestBody,
    ): CreateResponse
}
