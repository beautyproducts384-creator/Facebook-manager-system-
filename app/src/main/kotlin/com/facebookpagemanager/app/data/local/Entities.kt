package com.facebookpagemanager.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pages")
data class PageEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String = "",
    val pictureUrl: String? = null,
    val followers: Long = 0,
    val isDemo: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val message: String? = null,
    val link: String? = null,
    val mediaUrl: String? = null,
    val createdTime: Long = 0,
    val permalinkUrl: String? = null,
    val likes: Int = 0,
    val comments: Int = 0,
    val shares: Int = 0,
    val reach: Long = 0,
    val engagement: Long = 0,
    val isDemo: Boolean = true,
)

@Entity(tableName = "scheduled_posts")
data class ScheduledPostEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val message: String? = null,
    val link: String? = null,
    val mediaUri: String? = null,
    val postType: String = "text",
    val scheduledFor: Long = 0,
    val status: String = "scheduled",
    val remoteId: String? = null,
    val error: String? = null,
    val isDemo: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val message: String = "",
    val link: String? = null,
    val mediaUri: String? = null,
    val postType: String = "text",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val participantName: String? = null,
    val snippet: String? = null,
    val updatedTime: Long = 0,
    val unreadCount: Int = 0,
    val isDemo: Boolean = true,
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val text: String? = null,
    val fromPage: Boolean = false,
    val createdTime: Long = 0,
    val isDemo: Boolean = true,
)

@Entity(tableName = "page_comments")
data class CommentEntity(
    @PrimaryKey val id: String,
    val postId: String,
    val text: String? = null,
    val fromName: String? = null,
    val createdTime: Long = 0,
    val likeCount: Int = 0,
    val hidden: Boolean = false,
    val isDemo: Boolean = true,
)

@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val type: String = "image",
    val name: String = "",
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "saved_replies")
data class SavedReplyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val text: String,
)

@Entity(tableName = "post_templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val text: String,
    val category: String = "General",
)
