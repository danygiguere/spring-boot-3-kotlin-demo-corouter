package com.example.corouterdemo.repository

import com.example.corouterdemo.support.IntegrationTest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Each relationship of the model, on the seed: user u belongs to enterprise (u - 1) / 10 + 1; post p belongs to user
 * (p - 1) / 4 + 1, has (p % 4) images and (p % 7) likes.
 */
class RelationshipsTest : IntegrationTest() {
    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var profileRepository: ProfileRepository

    @Autowired lateinit var postRepository: PostRepository

    @Autowired lateinit var imageRepository: ImageRepository

    @Autowired lateinit var postLikeRepository: PostLikeRepository

    @Test
    fun `belongsTo - post 5 is Bob's, in enterprise 1`() =
        runTest {
            val post = postRepository.findById(5)!!
            val author = userRepository.findById(post.userId)!!

            assertThat(author.name).isEqualTo("Bob")
            assertThat(author.enterpriseId).isEqualTo(1)
        }

    @Test
    fun `hasOne - each user has one profile`() =
        runTest {
            assertThat(profileRepository.findByUserId(1)!!.userId).isEqualTo(1)
        }

    @Test
    fun `hasMany - user posts and post images in order`() =
        runTest {
            val posts = postRepository.findByAuthor(1, 1, Long.MAX_VALUE, 100).toList()
            val images = imageRepository.findAllByPostIdOrderByPositionAsc(3).toList()

            assertThat(posts.map { it.id }).containsExactly(4, 3, 2, 1)
            assertThat(images.map { it.position }).containsExactly(1, 2, 3)
            assertThat(imageRepository.findAllByPostIdOrderByPositionAsc(4).toList()).isEmpty()
        }

    @Test
    fun `hasManyThrough - enterprise 3 has 40 posts`() =
        runTest {
            val posts = postRepository.findFeed(3, Long.MAX_VALUE, 100).toList()

            assertThat(posts).hasSize(40)
            assertThat(posts).allSatisfy { assertThat(it.userId).isBetween(21L, 30L) }
        }

    @Test
    fun `manyToMany - both directions`() =
        runTest {
            val likers = userRepository.findLikersInEnterprise(6, 1, 0, 100).toList()

            assertThat(likers).hasSize(6)
            assertThat(postLikeRepository.countByPostId(6)).isEqualTo(6)
            likers.forEach { liker ->
                assertThat(postRepository.findLikedBy(liker.id!!, 1, Long.MAX_VALUE, 100).toList().map { it.id }).contains(6)
            }
        }

    @Test
    fun `tenant-scoped queries return nothing across enterprises`() =
        runTest {
            assertThat(userRepository.findInEnterprise(11, 1)).isNull()
            assertThat(postRepository.findVisible(41, 1)).isNull()
            assertThat(postRepository.findVisible(41, 2)).isNotNull
        }
}
