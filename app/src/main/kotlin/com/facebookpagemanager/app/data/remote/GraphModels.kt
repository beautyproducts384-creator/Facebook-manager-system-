package com.facebookpagemanager.app.data.remote

// --- Graph API DTOs (only the fields the app uses) ---

data class MeResponse(val id: String = "", val name: String = "")

data class GraphErrorDetail(
    val message: String = "",
    val type: String = "",
    val code: Int = 0,
    val error_subcode: Int = 0,
)

data class GraphErrorBody(val error: GraphErrorDetail? = null)

data class PictureData(val data: PictureUrl? = null)
data class PictureUrl(val url: String? = null)

data class AccountData(
    val id: String = "",
    val name: String = "",
    val category: String = "",
    val access_token: String = "",
    val picture: PictureData? = null,
    val followers_count: Long = 0,
)

data class AccountsResponse(val data: List<AccountData> = emptyList())

data class PageDetailResponse(
    val id: String = "",
    val name: String = "",
    val category: String = "",
    val followers_count: Long = 0,
    val fan_count: Long = 0,
    val picture: PictureData? = null,
)

data class InsightValue(val value: Any? = null, val end_time: String? = null)
data class InsightData(val name: String = "", val period: String = "", val values: List<InsightValue> = emptyList())
data class InsightsResponse(val data: List<InsightData> = emptyList())

data class Summary(val total_count: Int = 0)
data class PostData(
    val id: String = "",
    val message: String? = null,
    val link: String? = null,
    val created_time: String? = null,
    val permalink_url: String? = null,
    val likes: Summary? = null,
    val comments: Summary? = null,
    val shares: SharesData? = null,
    val full_picture: String? = null,
)
data class SharesData(val count: Int = 0)
data class PostsResponse(val data: List<PostData> = emptyList())

data class CreateResponse(val id: String = "", val post_id: String = "")

data class ScheduledPostData(
    val id: String = "",
    val message: String? = null,
    val scheduled_publish_time: Long = 0,
    val created_time: String? = null,
)
data class ScheduledPostsResponse(val data: List<ScheduledPostData> = emptyList())

data class DeleteResponse(val success: Boolean = false)

data class ReelUploadResponse(val id: String = "")

data class Participant(val id: String = "", val name: String = "")
data class ParticipantsData(val data: List<Participant> = emptyList())
data class ConversationData(
    val id: String = "",
    val snippet: String? = null,
    val updated_time: String? = null,
    val unread_count: Int = 0,
    val participants: ParticipantsData? = null,
)
data class ConversationsResponse(val data: List<ConversationData> = emptyList())

data class MessageFrom(val id: String = "", val name: String = "")
data class MessageData(
    val id: String = "",
    val message: String? = null,
    val from: MessageFrom? = null,
    val created_time: String? = null,
)
data class MessagesData(val data: List<MessageData> = emptyList())
data class ConversationDetailResponse(val messages: MessagesData? = null)

data class CommentData(
    val id: String = "",
    val message: String? = null,
    val from: MessageFrom? = null,
    val created_time: String? = null,
    val like_count: Int = 0,
    val is_hidden: Boolean = false,
)
data class CommentsResponse(val data: List<CommentData> = emptyList())
