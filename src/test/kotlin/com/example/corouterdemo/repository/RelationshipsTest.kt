package com.example.corouterdemo.repository

import com.example.corouterdemo.support.IntegrationTest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/** Each relationship of the model, on the seed: post p belongs to user (p - 1) / 4 + 1, has (p % 4) images and (p % 7) likes. */
class RelationshipsTest : IntegrationTest() {
    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var profileRepository: ProfileRepository

    @Autowired lateinit var postRepository: PostRepository

    @Autowired lateinit var imageRepository: ImageRepository

    @Autowired lateinit var postLikeRepository: PostLikeRepository

    @Test
    fun `belongsTo - post 5 is Bob's`() =
        runTest {
            val post = postRepository.findById(5)!!

            assertThat(userRepository.findById(post.userId)!!.name).isEqualTo("Bob")
        }

    @Test
    fun `hasOne - each user has one profile`() =
        runTest {
            assertThat(profileRepository.findByUserId(1)!!.userId).isEqualTo(1)
        }

    @Test
    fun `hasMany - user posts and post images in order`() =
        runTest {
            val posts = postRepository.findByAuthor(1, Long.MAX_VALUE, 100).toList()
            val images = imageRepository.findAllByPostIdOrderByPositionAsc(3).toList()

            assertThat(posts.map { it.id }).containsExactly(4, 3, 2, 1)
            assertThat(posts.map { it.imageCount }).containsExactly(0, 3, 2, 1)
            assertThat(posts).allSatisfy { assertThat(it.authorName).isEqualTo("Alice") }
            assertThat(images.map { it.position }).containsExactly(1, 2, 3)
            assertThat(imageRepository.findAllByPostIdOrderByPositionAsc(4).toList()).isEmpty()
        }

    @Test
    fun `hasManyThrough - user 1 has 6 images through posts 1 to 3`() =
        runTest {
            val images = imageRepository.findByUser(1, Long.MAX_VALUE, 100).toList()

            assertThat(images.map { it.id }).containsExactly(6, 5, 4, 3, 2, 1)
            assertThat(images.map { it.postId }.distinct()).containsExactly(3, 2, 1)
            assertThat(imageRepository.findByUser(1, 4, 2).toList().map { it.id }).containsExactly(3, 2)
        }

    @Test
    fun `manyToMany - both directions`() =
        runTest {
            val likers = userRepository.findLikers(6, 0, 100).toList()

            assertThat(likers).hasSize(6)
            assertThat(postLikeRepository.countByPostId(6)).isEqualTo(6)
            likers.forEach { liker ->
                assertThat(postRepository.findLikedBy(liker.id!!, Long.MAX_VALUE, 100).toList().map { it.id }).contains(6)
            }
        }
}
