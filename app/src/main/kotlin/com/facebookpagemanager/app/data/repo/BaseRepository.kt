package com.facebookpagemanager.app.data.repo

import com.facebookpagemanager.app.data.local.AppDatabase
import com.facebookpagemanager.app.data.local.ChatMessageEntity
import com.facebookpagemanager.app.data.local.CommentEntity
import com.facebookpagemanager.app.data.local.ConversationEntity
import com.facebookpagemanager.app.data.local.DraftEntity
import com.facebookpagemanager.app.data.local.MediaItemEntity
import com.facebookpagemanager.app.data.local.PageEntity
import com.facebookpagemanager.app.data.local.PostEntity
import com.facebookpagemanager.app.data.local.SavedReplyEntity
import com.facebookpagemanager.app.data.local.ScheduledPostEntity
import com.facebookpagemanager.app.data.local.TemplateEntity
import com.facebookpagemanager.app.data.model.ChatMessage
import com.facebookpagemanager.app.data.model.Conversation
import com.facebookpagemanager.app.data.model.Draft
import com.facebookpagemanager.app.data.model.FbPage
import com.facebookpagemanager.app.data.model.MediaItem
import com.facebookpagemanager.app.data.model.PageComment
import com.facebookpagemanager.app.data.model.Post
import com.facebookpagemanager.app.data.model.PostTemplate
import com.facebookpagemanager.app.data.model.SavedReply
import com.facebookpagemanager.app.data.model.ScheduledPost

/**
 * Shared Room-backed pieces: entity<->domain mapping and all purely-local CRUD
 * (drafts, media library, saved replies, templates) which behave identically
 * in Demo and Real mode.
 */
abstract class BaseRepository(protected val db: AppDatabase) : PageManagerRepository {

    // --- mapping ---
    protected fun PageEntity.toDomain() = FbPage(id, name, category, pictureUrl, followers)
    protected fun PostEntity.toDomain() = Post(
        id, pageId, message, link, mediaUrl, createdTime, permalinkUrl,
        likes, comments, shares, reach, engagement
    )
    protected fun ScheduledPostEntity.toDomain() = ScheduledPost(
        id, pageId, message, link, mediaUri, postType, scheduledFor, status, remoteId, error
    )
    protected fun ConversationEntity.toDomain() = Conversation(
        id, pageId, participantName, snippet, updatedTime, unreadCount
    )
    protected fun ChatMessageEntity.toDomain() = ChatMessage(id, conversationId, text, fromPage, createdTime)
    protected fun CommentEntity.toDomain() = PageComment(id, postId, text, fromName, createdTime, likeCount, hidden)
    protected fun MediaItemEntity.toDomain() = MediaItem(id, uri, type, name, addedAt)
    protected fun SavedReplyEntity.toDomain() = SavedReply(id, title, text)
    protected fun TemplateEntity.toDomain() = PostTemplate(id, title, text, category)
    protected fun DraftEntity.toDomain() = Draft(id, message, link, mediaUri, postType, updatedAt)

    // --- local CRUD (same in both modes) ---
    override suspend fun getDrafts(): List<Draft> = db.draftDao().getAll().map { it.toDomain() }
    override suspend fun saveDraft(draft: Draft): Long =
        db.draftDao().upsert(DraftEntity(draft.id, draft.message, draft.link, draft.mediaUri, draft.postType))
    override suspend fun deleteDraft(draft: Draft) {
        db.draftDao().delete(DraftEntity(draft.id, draft.message, draft.link, draft.mediaUri, draft.postType))
    }

    override suspend fun getMedia(): List<MediaItem> = db.mediaDao().getAll().map { it.toDomain() }
    override suspend fun addMedia(item: MediaItem): Long =
        db.mediaDao().insert(MediaItemEntity(0, item.uri, item.type, item.name))
    override suspend fun deleteMedia(item: MediaItem) {
        db.mediaDao().delete(MediaItemEntity(item.id, item.uri, item.type, item.name, item.addedAt))
    }

    override suspend fun getSavedReplies(): List<SavedReply> =
        db.savedReplyDao().getAll().map { it.toDomain() }
    override suspend fun saveSavedReply(reply: SavedReply): Long =
        db.savedReplyDao().insert(SavedReplyEntity(0, reply.title, reply.text))
    override suspend fun updateSavedReply(reply: SavedReply) {
        db.savedReplyDao().update(SavedReplyEntity(reply.id, reply.title, reply.text))
    }
    override suspend fun deleteSavedReply(reply: SavedReply) {
        db.savedReplyDao().delete(SavedReplyEntity(reply.id, reply.title, reply.text))
    }

    override suspend fun getTemplates(): List<PostTemplate> =
        db.templateDao().getAll().map { it.toDomain() }
    override suspend fun saveTemplate(template: PostTemplate): Long =
        db.templateDao().insert(TemplateEntity(0, template.title, template.text, template.category))
    override suspend fun updateTemplate(template: PostTemplate) {
        db.templateDao().update(TemplateEntity(template.id, template.title, template.text, template.category))
    }
    override suspend fun deleteTemplate(template: PostTemplate) {
        db.templateDao().delete(TemplateEntity(template.id, template.title, template.text, template.category))
    }
}
