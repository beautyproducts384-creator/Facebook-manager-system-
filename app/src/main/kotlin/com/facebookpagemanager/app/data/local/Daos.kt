package com.facebookpagemanager.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface PageDao {
    @Query("SELECT * FROM pages ORDER BY name")
    suspend fun getAll(): List<PageEntity>

    @Query("SELECT * FROM pages WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(pages: List<PageEntity>)

    @Query("DELETE FROM pages WHERE isDemo = 1")
    suspend fun clearDemo()
}

@Dao
interface PostDao {
    @Query("SELECT * FROM posts WHERE pageId = :pageId ORDER BY createdTime DESC LIMIT :limit")
    suspend fun getByPage(pageId: String, limit: Int = 100): List<PostEntity>

    @Query("SELECT * FROM posts WHERE pageId = :pageId ORDER BY (likes + comments * 2 + shares * 3) DESC LIMIT :limit")
    suspend fun getTopByPage(pageId: String, limit: Int = 5): List<PostEntity>

    @Query("SELECT COUNT(*) FROM posts WHERE pageId = :pageId")
    suspend fun countByPage(pageId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(posts: List<PostEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(post: PostEntity)

    @Query("DELETE FROM posts WHERE isDemo = 1")
    suspend fun clearDemo()
}

@Dao
interface ScheduledPostDao {
    @Query("SELECT * FROM scheduled_posts WHERE pageId = :pageId ORDER BY scheduledFor ASC")
    suspend fun getByPage(pageId: String): List<ScheduledPostEntity>

    @Query("SELECT * FROM scheduled_posts WHERE status = 'scheduled' AND scheduledFor <= :nowEpochSec ORDER BY scheduledFor ASC")
    suspend fun getDue(nowEpochSec: Long): List<ScheduledPostEntity>

    @Query("SELECT * FROM scheduled_posts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ScheduledPostEntity?

    @Query("SELECT COUNT(*) FROM scheduled_posts WHERE pageId = :pageId AND status = 'scheduled'")
    suspend fun countScheduled(pageId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(post: ScheduledPostEntity)

    @Update
    suspend fun update(post: ScheduledPostEntity)

    @Delete
    suspend fun delete(post: ScheduledPostEntity)

    @Query("DELETE FROM scheduled_posts WHERE isDemo = 1")
    suspend fun clearDemo()
}

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts ORDER BY updatedAt DESC")
    suspend fun getAll(): List<DraftEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: DraftEntity): Long

    @Delete
    suspend fun delete(draft: DraftEntity)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE pageId = :pageId ORDER BY updatedTime DESC")
    suspend fun getByPage(pageId: String): List<ConversationEntity>

    @Query("SELECT COALESCE(SUM(unreadCount), 0) FROM conversations WHERE pageId = :pageId")
    suspend fun unreadCount(pageId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ConversationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ConversationEntity)

    @Query("DELETE FROM conversations WHERE isDemo = 1")
    suspend fun clearDemo()
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY createdTime ASC")
    suspend fun getByConversation(conversationId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ChatMessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE isDemo = 1")
    suspend fun clearDemo()
}

@Dao
interface CommentDao {
    @Query("SELECT * FROM page_comments WHERE postId = :postId ORDER BY createdTime DESC")
    suspend fun getByPost(postId: String): List<CommentEntity>

    @Query("SELECT COUNT(*) FROM page_comments WHERE postId IN (SELECT id FROM posts WHERE pageId = :pageId)")
    suspend fun countRecentByPage(pageId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CommentEntity>)

    @Update
    suspend fun update(item: CommentEntity)

    @Query("DELETE FROM page_comments WHERE isDemo = 1")
    suspend fun clearDemo()
}

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items ORDER BY addedAt DESC")
    suspend fun getAll(): List<MediaItemEntity>

    @Insert
    suspend fun insert(item: MediaItemEntity): Long

    @Delete
    suspend fun delete(item: MediaItemEntity)
}

@Dao
interface SavedReplyDao {
    @Query("SELECT * FROM saved_replies ORDER BY title")
    suspend fun getAll(): List<SavedReplyEntity>

    @Insert
    suspend fun insert(item: SavedReplyEntity): Long

    @Update
    suspend fun update(item: SavedReplyEntity)

    @Delete
    suspend fun delete(item: SavedReplyEntity)
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM post_templates ORDER BY category, title")
    suspend fun getAll(): List<TemplateEntity>

    @Insert
    suspend fun insert(item: TemplateEntity): Long

    @Update
    suspend fun update(item: TemplateEntity)

    @Delete
    suspend fun delete(item: TemplateEntity)
}
