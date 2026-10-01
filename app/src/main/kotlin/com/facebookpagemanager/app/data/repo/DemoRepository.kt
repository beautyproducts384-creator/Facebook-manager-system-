package com.facebookpagemanager.app.data.repo

import android.content.Context
import com.facebookpagemanager.app.data.local.AppDatabase
import com.facebookpagemanager.app.data.local.ChatMessageEntity
import com.facebookpagemanager.app.data.local.CommentEntity
import com.facebookpagemanager.app.data.local.ConversationEntity
import com.facebookpagemanager.app.data.local.PostEntity
import com.facebookpagemanager.app.data.local.ScheduledPostEntity
import com.facebookpagemanager.app.data.model.AnalyticsData
import com.facebookpagemanager.app.data.model.ChatMessage
import com.facebookpagemanager.app.data.model.Conversation
import com.facebookpagemanager.app.data.model.DashboardData
import com.facebookpagemanager.app.data.model.FbPage
import com.facebookpagemanager.app.data.model.PageComment
import com.facebookpagemanager.app.data.model.Post
import com.facebookpagemanager.app.data.model.RepoResult
import com.facebookpagemanager.app.data.model.ScheduledPost
import com.facebookpagemanager.app.worker.NotificationHelper
import com.facebookpagemanager.app.worker.PostScheduler
import java.io.File
import java.util.UUID

/**
 * Demo-mode repository: every feature works end-to-end against Room-backed
 * sample data. Nothing touches the network. All demo content is clearly
 * labeled in the UI via the persistent DEMO MODE banner.
 */
class DemoRepository(
    private val appContext: Context,
    db: AppDatabase,
    private val notifications: NotificationHelper,
) : BaseRepository(db) {

    override suspend fun getPages(): RepoResult<List<FbPage>> =
        RepoResult.Ok(db.pageDao().getAll().filter { it.isDemo }.map { it.toDomain() })

    override suspend fun getDashboard(pageId: String): RepoResult<DashboardData> {
        val pageEntity = db.pageDao().getById(pageId)
            ?: db.pageDao().getAll().firstOrNull { it.isDemo }
            ?: return RepoResult.Err("Demo data not ready yet — please reopen the app.")
        val posts = db.postDao().getByPage(pageEntity.id)
        val reach = posts.sumOf { it.reach }
        val engagement = posts.sumOf { it.engagement }
        val scheduled = db.scheduledPostDao().getByPage(pageEntity.id).count { it.status == "scheduled" }
        val unread = db.conversationDao().unreadCount(pageEntity.id)
        val recentComments = db.commentDao().countRecentByPage(pageEntity.id)
        return RepoResult.Ok(
            DashboardData(
                page = pageEntity.toDomain(),
                followers = pageEntity.followers,
                reach = reach,
                engagement = engagement,
                scheduledCount = scheduled,
                publishedCount = posts.size,
                unreadMessages = unread,
                recentCommentsCount = recentComments,
                topPosts = posts.sortedByDescending { it.likes + it.comments * 2 + it.shares * 3 }
                    .take(5).map { it.toDomain() },
                limited = false,
                limitedNote = "Demo Mode: sample analytics, clearly labeled. Connect Facebook for real Page data."
            )
        )
    }

    override suspend fun getPosts(pageId: String, limit: Int): RepoResult<List<Post>> =
        RepoResult.Ok(db.postDao().getByPage(pageId, limit).map { it.toDomain() })

    private suspend fun demoPublish(pageId: String, message: String, mediaPath: String?): String {
        val id = "demo_post_${UUID.randomUUID().toString().take(8)}"
        db.postDao().upsert(
            PostEntity(
                id = id, pageId = pageId, message = message.ifBlank { null },
                mediaUrl = mediaPath, createdTime = System.currentTimeMillis() / 1000,
                permalinkUrl = "https://facebook.com/demo/$id", isDemo = true
            )
        )
        return id
    }

    override suspend fun createTextPost(pageId: String, message: String, link: String?): RepoResult<String> =
        RepoResult.Ok(demoPublish(pageId, message, null))

    override suspend fun createImagePost(pageId: String, message: String, imageFile: File): RepoResult<String> =
        RepoResult.Ok(demoPublish(pageId, message, imageFile.absolutePath))

    override suspend fun createVideoPost(pageId: String, message: String, videoFile: File): RepoResult<String> =
        RepoResult.Ok(demoPublish(pageId, message, videoFile.absolutePath))

    override suspend fun createReel(pageId: String, message: String, videoFile: File): RepoResult<String> =
        RepoResult.Ok(demoPublish(pageId, message, videoFile.absolutePath))

    override suspend fun schedulePost(
        pageId: String, message: String?, link: String?, mediaUri: String?,
        postType: String, scheduledForEpochSec: Long,
    ): RepoResult<ScheduledPost> {
        val now = System.currentTimeMillis() / 1000
        if (scheduledForEpochSec <= now + 30) {
            return RepoResult.Err("Scheduled time must be at least 30 seconds in the future.")
        }
        val entity = ScheduledPostEntity(
            id = UUID.randomUUID().toString(), pageId = pageId,
            message = message, link = link?.ifBlank { null }, mediaUri = mediaUri?.ifBlank { null },
            postType = postType, scheduledFor = scheduledForEpochSec,
            status = "scheduled", isDemo = true
        )
        db.scheduledPostDao().upsert(entity)
        PostScheduler.schedule(appContext, entity.id, scheduledForEpochSec)
        return RepoResult.Ok(entity.toDomain())
    }

    override suspend fun getScheduledPosts(pageId: String): RepoResult<List<ScheduledPost>> =
        RepoResult.Ok(db.scheduledPostDao().getByPage(pageId).map { it.toDomain() })

    override suspend fun cancelScheduledPost(post: ScheduledPost): RepoResult<Unit> {
        val entity = db.scheduledPostDao().getById(post.id)
            ?: return RepoResult.Err("Scheduled post not found.")
        PostScheduler.cancel(appContext, post.id)
        db.scheduledPostDao().update(entity.copy(status = "cancelled"))
        return RepoResult.Ok(Unit)
    }

    override suspend fun updateScheduledPost(post: ScheduledPost): RepoResult<Unit> {
        val entity = db.scheduledPostDao().getById(post.id)
            ?: return RepoResult.Err("Scheduled post not found.")
        if (entity.status != "scheduled") return RepoResult.Err("Only scheduled posts can be edited.")
        db.scheduledPostDao().update(
            entity.copy(
                message = post.message, link = post.link, mediaUri = post.mediaUri,
                postType = post.postType, scheduledFor = post.scheduledFor
            )
        )
        PostScheduler.schedule(appContext, post.id, post.scheduledFor)
        return RepoResult.Ok(Unit)
    }

    override suspend fun publishScheduledNow(post: ScheduledPost): RepoResult<Unit> {
        val entity = db.scheduledPostDao().getById(post.id)
        demoPublish(post.pageId, post.message ?: "", post.mediaUri)
        if (entity != null) db.scheduledPostDao().update(entity.copy(status = "published"))
        notifications.showPublished("Demo post published", (post.message ?: "Your scheduled post") .take(80))
        return RepoResult.Ok(Unit)
    }

    override suspend fun getConversations(pageId: String): RepoResult<List<Conversation>> =
        RepoResult.Ok(db.conversationDao().getByPage(pageId).map { it.toDomain() })

    override suspend fun getMessages(pageId: String, conversationId: String): RepoResult<List<ChatMessage>> =
        RepoResult.Ok(db.chatMessageDao().getByConversation(conversationId).map { it.toDomain() })

    override suspend fun sendMessage(pageId: String, conversationId: String, text: String): RepoResult<Unit> {
        val now = System.currentTimeMillis() / 1000
        db.chatMessageDao().upsert(
            ChatMessageEntity(
                id = "demo_msg_${UUID.randomUUID().toString().take(8)}",
                conversationId = conversationId, text = text, fromPage = true,
                createdTime = now, isDemo = true
            )
        )
        val conv = db.conversationDao().getByPage(pageId).find { it.id == conversationId }
        if (conv != null) {
            db.conversationDao().upsert(conv.copy(snippet = text.take(60), updatedTime = now, unreadCount = 0))
        }
        return RepoResult.Ok(Unit)
    }

    override suspend fun getComments(pageId: String, postId: String): RepoResult<List<PageComment>> =
        RepoResult.Ok(db.commentDao().getByPost(postId).map { it.toDomain() })

    override suspend fun replyToComment(pageId: String, commentId: String, text: String): RepoResult<Unit> {
        // A reply is stored as a new comment from the Page on the same post.
        val parent = findComment(commentId) ?: return RepoResult.Err("Comment not found.")
        db.commentDao().upsertAll(
            listOf(
                CommentEntity(
                    id = "demo_c_${UUID.randomUUID().toString().take(8)}",
                    postId = parent.postId, text = text, fromName = "Page (Demo)",
                    createdTime = System.currentTimeMillis() / 1000, isDemo = true
                )
            )
        )
        return RepoResult.Ok(Unit)
    }

    private suspend fun findComment(commentId: String): CommentEntity? {
        // scan all demo comments (small dataset)
        val dao = db.commentDao()
        val pages = db.pageDao().getAll().filter { it.isDemo }
        for (p in pages) {
            for (post in db.postDao().getByPage(p.id, 100)) {
                val found = dao.getByPost(post.id).firstOrNull { it.id == commentId }
                if (found != null) return found
            }
        }
        return null
    }

    override suspend fun hideComment(pageId: String, commentId: String, hide: Boolean): RepoResult<Unit> {
        val c = findComment(commentId) ?: return RepoResult.Err("Comment not found.")
        db.commentDao().update(c.copy(hidden = hide))
        return RepoResult.Ok(Unit)
    }

    override suspend fun getAnalytics(pageId: String): RepoResult<AnalyticsData> {
        val pageEntity = db.pageDao().getById(pageId)
            ?: return RepoResult.Err("Demo data not ready yet.")
        val posts = db.postDao().getByPage(pageId, 100)
        val reach = posts.sumOf { it.reach }
        val engagement = posts.sumOf { it.engagement }
        // Bucket reach by day for the last 7 days.
        val now = System.currentTimeMillis() / 1000
        val day = 24 * 3600L
        val daily = (6 downTo 0).map { d ->
            val start = now - (d + 1) * day
            val end = now - d * day
            val label = java.text.SimpleDateFormat("EEE", java.util.Locale.US)
                .format(java.util.Date(end * 1000))
            label to posts.filter { it.createdTime in start..end }.sumOf { it.reach }
        }
        return RepoResult.Ok(
            AnalyticsData(
                followers = pageEntity.followers,
                reach = reach,
                engagement = engagement,
                impressionsByDay = daily,
                topPosts = posts.sortedByDescending { it.likes + it.comments * 2 + it.shares * 3 }
                    .take(5).map { it.toDomain() },
                limited = false,
                note = "Demo Mode: sample analytics, clearly labeled."
            )
        )
    }
}
