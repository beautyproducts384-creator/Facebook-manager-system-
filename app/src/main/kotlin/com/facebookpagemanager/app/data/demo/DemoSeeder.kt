package com.facebookpagemanager.app.data.demo

import com.facebookpagemanager.app.data.local.AppDatabase
import com.facebookpagemanager.app.data.local.ChatMessageEntity
import com.facebookpagemanager.app.data.local.CommentEntity
import com.facebookpagemanager.app.data.local.ConversationEntity
import com.facebookpagemanager.app.data.local.MediaItemEntity
import com.facebookpagemanager.app.data.local.PageEntity
import com.facebookpagemanager.app.data.local.PostEntity
import com.facebookpagemanager.app.data.local.SavedReplyEntity
import com.facebookpagemanager.app.data.local.ScheduledPostEntity
import com.facebookpagemanager.app.data.local.TemplateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Seeds clearly-labeled sample data on first launch so Demo Mode is fully
 * functional with zero setup. Never runs when real pages exist.
 */
object DemoSeeder {

    suspend fun seedIfEmpty(db: AppDatabase, packageName: String) = withContext(Dispatchers.IO) {
        if (db.pageDao().getAll().any { it.isDemo }) return@withContext

        val now = System.currentTimeMillis() / 1000
        val day = 24 * 3600L

        val pages = listOf(
            PageEntity("demo_page_cafe", "Bloom & Bean Café", "Coffee shop", null, 12_480, isDemo = true),
            PageEntity("demo_page_fit", "FitFuel Studio", "Gym", null, 8_214, isDemo = true),
            PageEntity("demo_page_travel", "Wander Lens Travel", "Travel agency", null, 25_930, isDemo = true),
        )
        db.pageDao().upsertAll(pages)

        fun img(n: Int) = "android.resource://$packageName/drawable/demo_img_$n"

        val posts = listOf(
            PostEntity("demo_post_1", "demo_page_cafe",
                "Our new autumn spice latte is here! Come try it this weekend — first 50 cups are on us.",
                createdTime = now - 1 * day, permalinkUrl = "https://facebook.com/demo/demo_post_1",
                likes = 342, comments = 48, shares = 21, reach = 8_400, engagement = 1_120,
                mediaUrl = img(1), isDemo = true),
            PostEntity("demo_post_2", "demo_page_cafe",
                "Behind the scenes: how we roast our beans every morning at 5am.",
                createdTime = now - 3 * day, permalinkUrl = "https://facebook.com/demo/demo_post_2",
                likes = 518, comments = 96, shares = 64, reach = 14_200, engagement = 2_310,
                mediaUrl = img(2), isDemo = true),
            PostEntity("demo_post_3", "demo_page_cafe",
                "Thank you for 12k followers! Drop a comment with your favorite drink.",
                createdTime = now - 6 * day, permalinkUrl = "https://facebook.com/demo/demo_post_3",
                likes = 891, comments = 214, shares = 45, reach = 21_500, engagement = 3_980, isDemo = true),
            PostEntity("demo_post_4", "demo_page_fit",
                "Monday motivation: 5 moves, 20 minutes, zero excuses. Full routine in the comments!",
                createdTime = now - 2 * day, permalinkUrl = "https://facebook.com/demo/demo_post_4",
                likes = 276, comments = 59, shares = 88, reach = 9_800, engagement = 1_640,
                mediaUrl = img(3), isDemo = true),
            PostEntity("demo_post_5", "demo_page_fit",
                "Member spotlight: Sara lost 8kg in 3 months with our evening program. Proud of you!",
                createdTime = now - 5 * day, permalinkUrl = "https://facebook.com/demo/demo_post_5",
                likes = 634, comments = 121, shares = 33, reach = 16_700, engagement = 2_750, isDemo = true),
            PostEntity("demo_post_6", "demo_page_travel",
                "Santorini in October: fewer crowds, golden light, perfect weather. Who's in?",
                createdTime = now - 1 * day, permalinkUrl = "https://facebook.com/demo/demo_post_6",
                likes = 1_204, comments = 187, shares = 156, reach = 38_400, engagement = 6_120,
                mediaUrl = img(1), isDemo = true),
            PostEntity("demo_post_7", "demo_page_travel",
                "Packing hack: roll, don't fold. Saves 30% space in your carry-on.",
                createdTime = now - 4 * day, permalinkUrl = "https://facebook.com/demo/demo_post_7",
                likes = 445, comments = 52, shares = 210, reach = 19_300, engagement = 2_940, isDemo = true),
            PostEntity("demo_post_8", "demo_page_travel",
                "New vlog is live: 48 hours in Istanbul on a budget. Link in comments!",
                link = "https://example.com/istanbul-vlog",
                createdTime = now - 8 * day, permalinkUrl = "https://facebook.com/demo/demo_post_8",
                likes = 389, comments = 44, shares = 29, reach = 11_900, engagement = 1_530, isDemo = true),
        )
        db.postDao().upsertAll(posts)

        db.scheduledPostDao().upsert(
            ScheduledPostEntity("demo_sched_1", "demo_page_cafe",
                "Weekend special: buy one get one free on all pastries, Saturday only!",
                postType = "text", scheduledFor = now + 5 * 3600, isDemo = true)
        )
        db.scheduledPostDao().upsert(
            ScheduledPostEntity("demo_sched_2", "demo_page_fit",
                "New yoga batch starts Monday — 10 spots left. DM to book!",
                postType = "image", mediaUri = img(3), scheduledFor = now + 1 * day, isDemo = true)
        )
        db.scheduledPostDao().upsert(
            ScheduledPostEntity("demo_sched_3", "demo_page_travel",
                "Top 5 hidden beaches in Bali you haven't heard of.",
                postType = "video", scheduledFor = now + 2 * day, isDemo = true)
        )
        db.scheduledPostDao().upsert(
            ScheduledPostEntity("demo_sched_4", "demo_page_cafe",
                "Monday blues? Free cookie with every large coffee.",
                postType = "text", scheduledFor = now + 4 * day, isDemo = true)
        )

        val convs = listOf(
            ConversationEntity("demo_conv_1", "demo_page_cafe", "Ayesha Khan",
                "Do you have oat milk options?", now - 2 * 3600, 2, isDemo = true),
            ConversationEntity("demo_conv_2", "demo_page_cafe", "Bilal Ahmed",
                "Thanks, see you Saturday!", now - 1 * day, 0, isDemo = true),
            ConversationEntity("demo_conv_3", "demo_page_fit", "Danish Raza",
                "What are the monthly charges?", now - 5 * 3600, 1, isDemo = true),
        )
        db.conversationDao().upsertAll(convs)

        db.chatMessageDao().upsertAll(
            listOf(
                ChatMessageEntity("demo_m_1", "demo_conv_1", "Hi! Do you have oat milk options?",
                    fromPage = false, createdTime = now - 3 * 3600, isDemo = true),
                ChatMessageEntity("demo_m_2", "demo_conv_1", "Yes! Oat, almond and soy — all at no extra charge.",
                    fromPage = true, createdTime = now - 3 * 3600 + 600, isDemo = true),
                ChatMessageEntity("demo_m_3", "demo_conv_1", "Great, are you open on Sundays?",
                    fromPage = false, createdTime = now - 2 * 3600, isDemo = true),
                ChatMessageEntity("demo_m_4", "demo_conv_2", "Loved the latte art today!",
                    fromPage = false, createdTime = now - 1 * day - 3600, isDemo = true),
                ChatMessageEntity("demo_m_5", "demo_conv_2", "Thanks, see you Saturday!",
                    fromPage = true, createdTime = now - 1 * day, isDemo = true),
                ChatMessageEntity("demo_m_6", "demo_conv_3", "What are the monthly charges?",
                    fromPage = false, createdTime = now - 5 * 3600, isDemo = true),
            )
        )

        db.commentDao().upsertAll(
            listOf(
                CommentEntity("demo_c_1", "demo_post_1", "The autumn spice latte is amazing!",
                    "Ayesha Khan", now - 20 * 3600, likeCount = 24, isDemo = true),
                CommentEntity("demo_c_2", "demo_post_1", "What time do you open on weekends?",
                    "Bilal Ahmed", now - 18 * 3600, likeCount = 5, isDemo = true),
                CommentEntity("demo_c_3", "demo_post_6", "Adding Santorini to my bucket list right now!",
                    "Danish Raza", now - 22 * 3600, likeCount = 41, isDemo = true),
                CommentEntity("demo_c_4", "demo_post_6", "Went last year, can confirm it's magical.",
                    "Sara Malik", now - 20 * 3600, likeCount = 18, isDemo = true),
                CommentEntity("demo_c_5", "demo_post_4", "Day 3 of the routine and I'm already feeling stronger!",
                    "Ayesha Khan", now - 30 * 3600, likeCount = 12, isDemo = true),
            )
        )

        db.mediaDao().insert(MediaItemEntity(0, img(1), "image", "demo_img_1"))
        db.mediaDao().insert(MediaItemEntity(0, img(2), "image", "demo_img_2"))
        db.mediaDao().insert(MediaItemEntity(0, img(3), "image", "demo_img_3"))

        db.savedReplyDao().insert(SavedReplyEntity(0, "Greeting", "Hi! Thanks for reaching out to us. How can we help you today?"))
        db.savedReplyDao().insert(SavedReplyEntity(0, "Hours", "We're open Mon–Sat, 9am to 9pm, and Sundays 10am to 6pm."))
        db.savedReplyDao().insert(SavedReplyEntity(0, "Pricing", "Thanks for asking! Our full price list is pinned at the top of our Page."))

        db.templateDao().insert(TemplateEntity(0, "Weekend Promo", "Weekend special: {offer} — valid Saturday & Sunday only! Tag a friend who needs to see this.", "Promo"))
        db.templateDao().insert(TemplateEntity(0, "Engagement Question", "Quick poll: {question} — drop your answer in the comments!", "Engagement"))
        db.templateDao().insert(TemplateEntity(0, "New Launch", "Big news! {announcement} — launching {date}. Stay tuned!", "Announcement"))
        db.templateDao().insert(TemplateEntity(0, "Testimonial Ask", "Loved your experience with us? Leave a review — it means the world to our small team.", "Engagement"))
    }
}
