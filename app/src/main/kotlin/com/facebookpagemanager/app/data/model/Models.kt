package com.facebookpagemanager.app.data.model

/** Uniform result type. [requirement] carries the honest Meta permission/App Review
 *  explanation when a real-mode call cannot succeed. */
sealed interface RepoResult<out T> {
    data class Ok<T>(val value: T) : RepoResult<T>
    data class Err(val message: String, val requirement: String? = null) : RepoResult<Nothing>
}

data class FbPage(
    val id: String,
    val name: String,
    val category: String = "",
    val pictureUrl: String? = null,
    val followers: Long = 0,
)

data class DashboardData(
    val page: FbPage,
    val followers: Long,
    val reach: Long,
    val engagement: Long,
    val scheduledCount: Int,
    val publishedCount: Int,
    val unreadMessages: Int,
    val recentCommentsCount: Int,
    val topPosts: List<Post>,
    /** True when some stats could not be loaded (e.g. read_insights not granted). */
    val limited: Boolean = false,
    val limitedNote: String? = null,
)

data class Post(
    val id: String,
    val pageId: String,
    val message: String? = null,
    val link: String? = null,
    val mediaUrl: String? = null,
    val createdTime: Long = 0, // epoch seconds
    val permalinkUrl: String? = null,
    val likes: Int = 0,
    val comments: Int = 0,
    val shares: Int = 0,
    val reach: Long = 0,
    val engagement: Long = 0,
)

data class ScheduledPost(
    val id: String,
    val pageId: String,
    val message: String? = null,
    val link: String? = null,
    /** Local content:// URI, file path, or remote URL of attached media. */
    val mediaUri: String? = null,
    val postType: String = "text", // text | image | video | reel | link
    val scheduledFor: Long = 0, // epoch seconds
    val status: String = "scheduled", // scheduled | published | failed | cancelled
    val remoteId: String? = null,
    val error: String? = null,
)

data class Draft(
    val id: Long = 0,
    val message: String = "",
    val link: String? = null,
    val mediaUri: String? = null,
    val postType: String = "text",
    val updatedAt: Long = 0,
)

data class Conversation(
    val id: String,
    val pageId: String,
    val participantName: String? = null,
    val snippet: String? = null,
    val updatedTime: Long = 0,
    val unreadCount: Int = 0,
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val text: String? = null,
    val fromPage: Boolean = false,
    val createdTime: Long = 0,
)

data class PageComment(
    val id: String,
    val postId: String,
    val text: String? = null,
    val fromName: String? = null,
    val createdTime: Long = 0,
    val likeCount: Int = 0,
    val hidden: Boolean = false,
)

data class MediaItem(
    val id: Long = 0,
    val uri: String,
    val type: String = "image", // image | video
    val name: String = "",
    val addedAt: Long = 0,
)

data class SavedReply(
    val id: Long = 0,
    val title: String,
    val text: String,
)

data class PostTemplate(
    val id: Long = 0,
    val title: String,
    val text: String,
    val category: String = "General",
)

data class AnalyticsData(
    val followers: Long,
    val reach: Long,
    val engagement: Long,
    /** Pairs of (day label, impressions). */
    val impressionsByDay: List<Pair<String, Long>> = emptyList(),
    val topPosts: List<Post> = emptyList(),
    val limited: Boolean = false,
    val note: String? = null,
)

/** One validated row from a bulk-upload CSV file. */
data class BulkRow(
    val lineNumber: Int,
    val date: String,
    val time: String,
    val caption: String,
    val media: String,
    val link: String,
    val postType: String,
    val scheduledForEpochSec: Long = 0,
    val errors: List<String> = emptyList(),
) {
    val isValid: Boolean get() = errors.isEmpty()
}
