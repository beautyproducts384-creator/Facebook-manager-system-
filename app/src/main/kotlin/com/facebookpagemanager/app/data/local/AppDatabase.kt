package com.facebookpagemanager.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PageEntity::class,
        PostEntity::class,
        ScheduledPostEntity::class,
        DraftEntity::class,
        ConversationEntity::class,
        ChatMessageEntity::class,
        CommentEntity::class,
        MediaItemEntity::class,
        SavedReplyEntity::class,
        TemplateEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pageDao(): PageDao
    abstract fun postDao(): PostDao
    abstract fun scheduledPostDao(): ScheduledPostDao
    abstract fun draftDao(): DraftDao
    abstract fun conversationDao(): ConversationDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun commentDao(): CommentDao
    abstract fun mediaDao(): MediaDao
    abstract fun savedReplyDao(): SavedReplyDao
    abstract fun templateDao(): TemplateDao
}
