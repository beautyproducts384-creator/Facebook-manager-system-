package com.facebookpagemanager.app.data.repo

import com.facebookpagemanager.app.data.model.AnalyticsData
import com.facebookpagemanager.app.data.model.ChatMessage
import com.facebookpagemanager.app.data.model.Conversation
import com.facebookpagemanager.app.data.model.DashboardData
import com.facebookpagemanager.app.data.model.Draft
import com.facebookpagemanager.app.data.model.FbPage
import com.facebookpagemanager.app.data.model.MediaItem
import com.facebookpagemanager.app.data.model.PageComment
import com.facebookpagemanager.app.data.model.Post
import com.facebookpagemanager.app.data.model.PostTemplate
import com.facebookpagemanager.app.data.model.RepoResult
import com.facebookpagemanager.app.data.model.SavedReply
import com.facebookpagemanager.app.data.model.ScheduledPost
import java.io.File

/**
 * Single data API used by all screens. Two implementations:
 * - [GraphApiRepository]: official Meta Graph API (real mode)
 * - [DemoRepository]: Room-backed sample data (demo mode, default)
 */
interface PageManagerRepository {

    // --- Pages ---
    suspend fun getPages(): RepoResult<List<FbPage>>

    // --- Dashboard ---
    suspend fun getDashboard(pageId: String): RepoResult<DashboardData>

    // --- Publishing ---
    suspend fun getPosts(pageId: String, limit: Int = 25): RepoResult<List<Post>>
    suspend fun createTextPost(pageId: String, message: String, link: String?): RepoResult<String>
    suspend fun createImagePost(pageId: String, message: String, imageFile: File): RepoResult<String>
    suspend fun createVideoPost(pageId: String, message: String, videoFile: File): RepoResult<String>
    suspend fun createReel(pageId: String, message: String, videoFile: File): RepoResult<String>

    // --- Scheduling (local schedule + WorkManager publish; fully editable/cancellable) ---
    suspend fun schedulePost(
        pageId: String,
        message: String?,
        link: String?,
        mediaUri: String?,
        postType: String,
        scheduledForEpochSec: Long,
    ): RepoResult<ScheduledPost>

    suspend fun getScheduledPosts(pageId: String): RepoResult<List<ScheduledPost>>
    suspend fun cancelScheduledPost(post: ScheduledPost): RepoResult<Unit>
    suspend fun updateScheduledPost(post: ScheduledPost): RepoResult<Unit>

    /** Called by the scheduler worker when a post becomes due. */
    suspend fun publishScheduledNow(post: ScheduledPost): RepoResult<Unit>

    // --- Inbox ---
    suspend fun getConversations(pageId: String): RepoResult<List<Conversation>>
    suspend fun getMessages(pageId: String, conversationId: String): RepoResult<List<ChatMessage>>
    suspend fun sendMessage(pageId: String, conversationId: String, text: String): RepoResult<Unit>

    // --- Comments ---
    suspend fun getComments(pageId: String, postId: String): RepoResult<List<PageComment>>
    suspend fun replyToComment(pageId: String, commentId: String, text: String): RepoResult<Unit>
    suspend fun hideComment(pageId: String, commentId: String, hide: Boolean): RepoResult<Unit>

    // --- Analytics ---
    suspend fun getAnalytics(pageId: String): RepoResult<AnalyticsData>

    // --- Local data (identical in both modes) ---
    suspend fun getDrafts(): List<Draft>
    suspend fun saveDraft(draft: Draft): Long
    suspend fun deleteDraft(draft: Draft)

    suspend fun getMedia(): List<MediaItem>
    suspend fun addMedia(item: MediaItem): Long
    suspend fun deleteMedia(item: MediaItem)

    suspend fun getSavedReplies(): List<SavedReply>
    suspend fun saveSavedReply(reply: SavedReply): Long
    suspend fun updateSavedReply(reply: SavedReply)
    suspend fun deleteSavedReply(reply: SavedReply)

    suspend fun getTemplates(): List<PostTemplate>
    suspend fun saveTemplate(template: PostTemplate): Long
    suspend fun updateTemplate(template: PostTemplate)
    suspend fun deleteTemplate(template: PostTemplate)
}
