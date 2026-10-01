package com.facebookpagemanager.app.data.repo

import android.content.Context
import android.net.Uri
import com.facebookpagemanager.app.BuildConfig
import com.facebookpagemanager.app.auth.TokenStore
import com.facebookpagemanager.app.data.local.PageEntity
import com.facebookpagemanager.app.data.local.AppDatabase
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
import com.facebookpagemanager.app.data.remote.GraphApiService
import com.facebookpagemanager.app.data.remote.GraphErrorBody
import com.facebookpagemanager.app.data.remote.GraphVideoService
import com.facebookpagemanager.app.util.MediaUtils
import com.facebookpagemanager.app.worker.PostScheduler
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

/**
 * Real-mode repository: every call goes to the official Meta Graph API via Retrofit.
 * Nothing is faked — permission / App Review failures surface as [RepoResult.Err]
 * with the exact requirement.
 */
class GraphApiRepository(
    private val appContext: Context,
    db: AppDatabase,
    private val api: GraphApiService,
    private val videoApi: GraphVideoService,
    private val tokenStore: TokenStore,
) : BaseRepository(db) {

    private val ver = BuildConfig.GRAPH_API_VERSION
    private val gson = Gson()

    companion object {
        const val REQ_PAGES =
            "Requires Facebook Login with the 'pages_show_list' permission. " +
                "For anyone who isn't an admin/tester of your Meta App, Meta must approve the app (App Review) first."
        const val REQ_INSIGHTS =
            "Requires the 'read_insights' permission AND Meta App Review approval. " +
                "Until Meta approves your app, reach/engagement stay unavailable for non-test users."
        const val REQ_PUBLISH =
            "Requires the 'pages_manage_posts' permission AND Meta App Review approval for non-test users."
        const val REQ_MESSAGING =
            "Requires the 'pages_messaging' permission AND Meta App Review approval. " +
                "The Page Inbox shows Demo data until Meta approves the app."
        const val REQ_COMMENTS =
            "Reading comments needs 'pages_read_user_content'; replying/hiding needs 'pages_manage_posts'. " +
                "Non-test users additionally need Meta App Review approval."
        const val REQ_REELS =
            "Reels publishing via the API has limited availability — Meta enables it per app. " +
                "If Meta returns an error here, Reels are not enabled for your Meta App; " +
                "publish the Reel from the Facebook app instead."
    }

    // ---------- helpers ----------

    private fun pageToken(pageId: String): String? =
        tokenStore.getPageToken(pageId) ?: tokenStore.getUserToken()

    private fun requirePageToken(pageId: String): String =
        pageToken(pageId)
            ?: throw IllegalStateException("Not connected to Facebook — connect your account on the Pages screen first.")

    private suspend fun <T> graphCall(requirement: String?, block: suspend () -> T): RepoResult<T> {
        return try {
            RepoResult.Ok(block())
        } catch (e: HttpException) {
            RepoResult.Err(graphErrorMessage(e), requirement)
        } catch (e: IOException) {
            RepoResult.Err(
                "Network error — couldn't reach Meta's servers. Check your connection and try again.",
                null
            )
        } catch (e: IllegalStateException) {
            RepoResult.Err(e.message ?: "Not connected.", "Connect your Facebook account on the Pages screen.")
        } catch (e: Exception) {
            RepoResult.Err("Unexpected error: ${e.message}", null)
        }
    }

    private fun graphErrorMessage(e: HttpException): String {
        val raw = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
        val detail = try { gson.fromJson(raw, GraphErrorBody::class.java)?.error } catch (_: Exception) { null }
        val msg = detail?.message?.takeIf { it.isNotBlank() } ?: "HTTP ${e.code()}"
        return when (detail?.code ?: 0) {
            190 -> "Facebook session expired or is invalid. Please reconnect your account. ($msg)"
            200, 10 -> "Permission denied by Meta: $msg"
            100 -> "Meta rejected the request: $msg"
            4, 17, 32 -> "Meta rate limit reached: $msg. Please wait a little and try again."
            else -> "Meta Graph API error (code ${detail?.code ?: e.code()}): $msg"
        }
    }

    private fun shortGraphError(e: HttpException): String {
        val raw = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
        val detail = try { gson.fromJson(raw, GraphErrorBody::class.java)?.error } catch (_: Exception) { null }
        return detail?.message?.takeIf { it.isNotBlank() } ?: "HTTP ${e.code()}"
    }

    private fun parseGraphTime(s: String?): Long {
        if (s.isNullOrBlank()) return 0
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
            (fmt.parse(s)?.time ?: 0) / 1000
        } catch (_: Exception) { 0 }
    }

    private fun insightNumber(values: List<com.facebookpagemanager.app.data.remote.InsightValue>): Long =
        (values.lastOrNull()?.value as? Number)?.toLong() ?: 0

    private fun filePart(file: File, fieldName: String, mime: String): MultipartBody.Part {
        val body = file.asRequestBody(mime.toMediaType())
        return MultipartBody.Part.createFormData(fieldName, file.name, body)
    }

    private fun textPart(value: String?) = value?.toRequestBody("text/plain".toMediaType())

    private fun resolveMediaFile(uriStr: String?): File? {
        if (uriStr.isNullOrBlank()) return null
        return try {
            when {
                uriStr.startsWith("content://") ->
                    MediaUtils.copyUriToCache(appContext, Uri.parse(uriStr), "sched")
                uriStr.startsWith("file://") -> File(URI(uriStr)).takeIf { it.exists() }
                uriStr.startsWith("android.resource://") -> null // demo asset: real mode needs a real file
                else -> File(uriStr).takeIf { it.exists() }
            }
        } catch (_: Exception) { null }
    }

    // ---------- pages ----------

    /** Display name of the connected Facebook user (for the Settings/Pages screens). */
    suspend fun getMyName(): String? {
        val token = tokenStore.getUserToken() ?: return null
        return try {
            api.getMe(ver, token).name.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    /** Called right after Facebook Login: pulls /me/accounts and caches page access tokens. */
    suspend fun refreshPages(userToken: String): RepoResult<List<FbPage>> =
        graphCall(REQ_PAGES) {
            val resp = api.getAccounts(ver, userToken)
            val pages = resp.data.map { a ->
                if (a.access_token.isNotBlank()) tokenStore.savePageToken(a.id, a.access_token)
                PageEntity(
                    id = a.id, name = a.name, category = a.category,
                    pictureUrl = a.picture?.data?.url, followers = a.followers_count, isDemo = false
                )
            }
            db.pageDao().upsertAll(pages)
            pages.map { it.toDomain() }
        }

    override suspend fun getPages(): RepoResult<List<FbPage>> {
        val token = tokenStore.getUserToken()
            ?: return RepoResult.Err(
                "Not connected to Facebook.",
                "Connect your Facebook account on the Pages screen to manage real Pages. " +
                    "Demo Mode (default) needs no setup at all."
            )
        return when (val r = refreshPages(token)) {
            is RepoResult.Ok -> r
            is RepoResult.Err -> {
                // Offline / API hiccup: fall back to cached pages so the UI still works.
                val cached = db.pageDao().getAll().filter { !it.isDemo }
                if (cached.isNotEmpty()) RepoResult.Ok(cached.map { it.toDomain() }) else r
            }
        }
    }

    // ---------- dashboard ----------

    private suspend fun fetchPostsInternal(pageId: String, token: String, limit: Int): List<Post> {
        val resp = api.getPosts(ver, pageId, token, limit = limit)
        return resp.data.map { p ->
            Post(
                id = p.id, pageId = pageId, message = p.message, link = p.link,
                mediaUrl = p.full_picture, createdTime = parseGraphTime(p.created_time),
                permalinkUrl = p.permalink_url,
                likes = p.likes?.total_count ?: 0, comments = p.comments?.total_count ?: 0,
                shares = p.shares?.count ?: 0
            )
        }
    }

    override suspend fun getDashboard(pageId: String): RepoResult<DashboardData> =
        graphCall(null) {
            val token = requirePageToken(pageId)
            val page = api.getPage(ver, pageId, token)

            var reach = 0L
            var engagement = 0L
            var limitedNote: String? = null
            try {
                val insights = api.getInsights(ver, pageId, "page_impressions,page_engaged_users", "day", token)
                reach = insightNumber(insights.data.find { it.name == "page_impressions" }?.values ?: emptyList())
                engagement = insightNumber(insights.data.find { it.name == "page_engaged_users" }?.values ?: emptyList())
            } catch (e: HttpException) {
                limitedNote = "$REQ_INSIGHTS (Meta said: ${shortGraphError(e)})"
            }

            val posts = try { fetchPostsInternal(pageId, token, 10) } catch (e: Exception) { emptyList() }
            val scheduled = db.scheduledPostDao().getByPage(pageId).count { it.status == "scheduled" }
            val unread = try {
                api.getConversations(ver, pageId, token, limit = 25).data.sumOf { it.unread_count }
            } catch (e: Exception) { 0 }
            val recentComments = db.commentDao().countRecentByPage(pageId)

            DashboardData(
                page = FbPage(pageId, page.name, page.category, page.picture?.data?.url, page.followers_count),
                followers = page.followers_count,
                reach = reach,
                engagement = engagement,
                scheduledCount = scheduled,
                publishedCount = posts.size,
                unreadMessages = unread,
                recentCommentsCount = recentComments,
                topPosts = posts.sortedByDescending { it.likes + it.comments * 2 + it.shares * 3 }.take(5),
                limited = limitedNote != null,
                limitedNote = limitedNote
            )
        }

    // ---------- publishing ----------

    override suspend fun getPosts(pageId: String, limit: Int): RepoResult<List<Post>> =
        graphCall(REQ_PAGES) {
            fetchPostsInternal(pageId, requirePageToken(pageId), limit)
        }

    override suspend fun createTextPost(pageId: String, message: String, link: String?): RepoResult<String> =
        graphCall(REQ_PUBLISH) {
            val token = requirePageToken(pageId)
            val r = api.createFeedPost(ver, pageId, message, link?.ifBlank { null }, true, null, token)
            r.post_id.ifBlank { r.id }.ifBlank { throw IllegalStateException("Meta did not return a post id.") }
        }

    override suspend fun createImagePost(pageId: String, message: String, imageFile: File): RepoResult<String> =
        graphCall(REQ_PUBLISH) {
            val token = requirePageToken(pageId)
            val r = api.uploadPhoto(
                ver, pageId,
                filePart(imageFile, "source", "image/*"),
                textPart(message), textPart("true"), null,
                token.toRequestBody("text/plain".toMediaType())
            )
            r.post_id.ifBlank { r.id }.ifBlank { throw IllegalStateException("Meta did not return a post id.") }
        }

    override suspend fun createVideoPost(pageId: String, message: String, videoFile: File): RepoResult<String> =
        graphCall(REQ_PUBLISH) {
            val token = requirePageToken(pageId)
            val r = videoApi.uploadVideo(
                ver, pageId,
                filePart(videoFile, "source", "video/*"),
                textPart(message),
                token.toRequestBody("text/plain".toMediaType())
            )
            r.id.ifBlank { throw IllegalStateException("Meta did not return a video id.") }
        }

    override suspend fun createReel(pageId: String, message: String, videoFile: File): RepoResult<String> =
        graphCall(REQ_REELS) {
            val token = requirePageToken(pageId)
            val start = api.startReelUpload(ver, pageId, token = token)
            val videoId = start.id.ifBlank {
                throw IllegalStateException("Meta did not start a Reels upload session — Reels are likely not enabled for this Meta App.")
            }
            videoApi.uploadReelChunk(
                ver, videoId,
                filePart(videoFile, "video_file", "video/*"),
                token.toRequestBody("text/plain".toMediaType())
            )
            val done = api.finishReelUpload(
                ver = ver, pageId = pageId, videoId = videoId,
                description = message.ifBlank { null }, token = token
            )
            done.id.ifBlank { done.post_id }
                .ifBlank { throw IllegalStateException("Meta did not return a Reel id — Reels may not be enabled for this Meta App.") }
        }

    // ---------- scheduling ----------

    override suspend fun schedulePost(
        pageId: String, message: String?, link: String?, mediaUri: String?,
        postType: String, scheduledForEpochSec: Long,
    ): RepoResult<ScheduledPost> {
        val now = System.currentTimeMillis() / 1000
        if (scheduledForEpochSec <= now + 60) {
            return RepoResult.Err("Scheduled time must be at least 1 minute in the future.")
        }
        if (scheduledForEpochSec > now + 75L * 24 * 3600) {
            return RepoResult.Err("Posts can be scheduled up to 75 days ahead (Meta's limit).")
        }
        val entity = ScheduledPostEntity(
            id = UUID.randomUUID().toString(), pageId = pageId,
            message = message, link = link?.ifBlank { null }, mediaUri = mediaUri?.ifBlank { null },
            postType = postType, scheduledFor = scheduledForEpochSec,
            status = "scheduled", isDemo = false
        )
        db.scheduledPostDao().upsert(entity)
        PostScheduler.schedule(appContext, entity.id, scheduledForEpochSec)
        return RepoResult.Ok(entity.toDomain())
    }

    override suspend fun getScheduledPosts(pageId: String): RepoResult<List<ScheduledPost>> =
        RepoResult.Ok(db.scheduledPostDao().getByPage(pageId).map { it.toDomain() })

    override suspend fun cancelScheduledPost(post: ScheduledPost): RepoResult<Unit> {
        val entity = db.scheduledPostDao().getById(post.id)
            ?: return RepoResult.Err("Scheduled post not found — it may already have been published.")
        PostScheduler.cancel(appContext, post.id)
        db.scheduledPostDao().update(entity.copy(status = "cancelled"))
        return RepoResult.Ok(Unit)
    }

    override suspend fun updateScheduledPost(post: ScheduledPost): RepoResult<Unit> {
        val entity = db.scheduledPostDao().getById(post.id)
            ?: return RepoResult.Err("Scheduled post not found.")
        if (entity.status != "scheduled") return RepoResult.Err("Only scheduled posts can be edited.")
        val updated = entity.copy(
            message = post.message, link = post.link, mediaUri = post.mediaUri,
            postType = post.postType, scheduledFor = post.scheduledFor
        )
        db.scheduledPostDao().update(updated)
        PostScheduler.schedule(appContext, post.id, post.scheduledFor)
        return RepoResult.Ok(Unit)
    }

    override suspend fun publishScheduledNow(post: ScheduledPost): RepoResult<Unit> {
        val result: RepoResult<String> = when (post.postType) {
            "image" -> {
                val file = resolveMediaFile(post.mediaUri)
                if (file == null) RepoResult.Err("Attached image file is no longer available.")
                else createImagePost(post.pageId, post.message ?: "", file)
            }
            "video" -> {
                val file = resolveMediaFile(post.mediaUri)
                if (file == null) RepoResult.Err("Attached video file is no longer available.")
                else createVideoPost(post.pageId, post.message ?: "", file)
            }
            "reel" -> {
                val file = resolveMediaFile(post.mediaUri)
                if (file == null) RepoResult.Err("Attached video file is no longer available.")
                else createReel(post.pageId, post.message ?: "", file)
            }
            else -> createTextPost(post.pageId, post.message ?: "", post.link)
        }
        val entity = db.scheduledPostDao().getById(post.id)
        when (result) {
            is RepoResult.Ok -> {
                if (entity != null) db.scheduledPostDao().update(entity.copy(status = "published", remoteId = result.value))
            }
            is RepoResult.Err -> {
                if (entity != null) db.scheduledPostDao().update(entity.copy(status = "failed", error = result.message))
            }
        }
        return when (result) {
            is RepoResult.Ok -> RepoResult.Ok(Unit)
            is RepoResult.Err -> result
        }
    }

    // ---------- inbox ----------

    override suspend fun getConversations(pageId: String): RepoResult<List<Conversation>> =
        graphCall(REQ_MESSAGING) {
            val token = requirePageToken(pageId)
            api.getConversations(ver, pageId, token).data.map { c ->
                val name = c.participants?.data?.firstOrNull { it.id != pageId }?.name
                    ?: c.participants?.data?.firstOrNull()?.name
                Conversation(c.id, pageId, name, c.snippet, parseGraphTime(c.updated_time), c.unread_count)
            }
        }

    override suspend fun getMessages(pageId: String, conversationId: String): RepoResult<List<ChatMessage>> =
        graphCall(REQ_MESSAGING) {
            val token = requirePageToken(pageId)
            val detail = api.getMessages(ver, conversationId, token)
            detail.messages?.data?.sortedBy { parseGraphTime(it.created_time) }?.map { m ->
                ChatMessage(m.id, conversationId, m.message, m.from?.id == pageId, parseGraphTime(m.created_time))
            } ?: emptyList()
        }

    override suspend fun sendMessage(pageId: String, conversationId: String, text: String): RepoResult<Unit> =
        graphCall(REQ_MESSAGING) {
            api.sendMessage(ver, conversationId, text, requirePageToken(pageId))
            Unit
        }

    // ---------- comments ----------

    override suspend fun getComments(pageId: String, postId: String): RepoResult<List<PageComment>> =
        graphCall(REQ_COMMENTS) {
            val token = requirePageToken(pageId)
            api.getComments(ver, postId, token).data.map { c ->
                PageComment(c.id, postId, c.message, c.from?.name, parseGraphTime(c.created_time), c.like_count, c.is_hidden)
            }
        }

    override suspend fun replyToComment(pageId: String, commentId: String, text: String): RepoResult<Unit> =
        graphCall(REQ_COMMENTS) {
            api.replyToComment(ver, commentId, text, requirePageToken(pageId))
            Unit
        }

    override suspend fun hideComment(pageId: String, commentId: String, hide: Boolean): RepoResult<Unit> =
        graphCall(REQ_COMMENTS) {
            api.setCommentHidden(ver, commentId, hide, requirePageToken(pageId))
            Unit
        }

    // ---------- analytics ----------

    override suspend fun getAnalytics(pageId: String): RepoResult<AnalyticsData> =
        graphCall(null) {
            val token = requirePageToken(pageId)
            val page = api.getPage(ver, pageId, token)
            var reach = 0L
            var engagement = 0L
            var daily: List<Pair<String, Long>> = emptyList()
            var limitedNote: String? = null
            try {
                val now = System.currentTimeMillis() / 1000
                val insights = api.getInsights(
                    ver, pageId, "page_impressions,page_engaged_users", "day", token,
                    since = now - 7 * 24 * 3600, until = now
                )
                val impr = insights.data.find { it.name == "page_impressions" }
                reach = insightNumber(impr?.values ?: emptyList())
                engagement = insightNumber(
                    insights.data.find { it.name == "page_engaged_users" }?.values ?: emptyList()
                )
                daily = (impr?.values ?: emptyList()).takeLast(7).mapIndexed { i, v ->
                    "D-${6 - i}" to ((v.value as? Number)?.toLong() ?: 0)
                }
            } catch (e: HttpException) {
                limitedNote = "$REQ_INSIGHTS (Meta said: ${shortGraphError(e)})"
            }
            val posts = try { fetchPostsInternal(pageId, token, 25) } catch (e: Exception) { emptyList() }
            AnalyticsData(
                followers = page.followers_count, reach = reach, engagement = engagement,
                impressionsByDay = daily,
                topPosts = posts.sortedByDescending { it.likes + it.comments * 2 + it.shares * 3 }.take(5),
                limited = limitedNote != null, note = limitedNote
            )
        }
}
