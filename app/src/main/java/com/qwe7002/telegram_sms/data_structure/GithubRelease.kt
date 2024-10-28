package com.qwe7002.telegram_sms.data_structure

import com.google.gson.annotations.SerializedName

data class GitHubRelease(
    val url: String,
    @SerializedName("assets_url")
    val assetsUrl: String,
    @SerializedName("upload_url")
    val uploadUrl: String,
    @SerializedName("html_url")
    val htmlUrl: String,
    val id: Long,
    val author: Author,
    @SerializedName("node_id")
    val nodeId: String,
    @SerializedName("tag_name")
    val tagName: String,
    @SerializedName("target_commitish")
    val targetCommitish: String,
    val name: String,
    val draft: Boolean,
    val prerelease: Boolean,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("published_at")
    val publishedAt: String,
    val assets: List<Asset>,
    @SerializedName("tarball_url")
    val tarballUrl: String,
    @SerializedName("zipball_url")
    val zipballUrl: String,
    val body: String
)

data class Author(
    val login: String,
    val id: Long,
    @SerializedName("node_id")
    val nodeId: String,
    @SerializedName("avatar_url")
    val avatarUrl: String,
    @SerializedName("gravatar_id")
    val gravatarId: String,
    val url: String,
    @SerializedName("html_url")
    val htmlUrl: String,
    @SerializedName("followers_url")
    val followersUrl: String,
    @SerializedName("following_url")
    val followingUrl: String,
    @SerializedName("gists_url")
    val gistsUrl: String,
    @SerializedName("starred_url")
    val starredUrl: String,
    @SerializedName("subscriptions_url")
    val subscriptionsUrl: String,
    @SerializedName("organizations_url")
    val organizationsUrl: String,
    @SerializedName("repos_url")
    val reposUrl: String,
    @SerializedName("events_url")
    val eventsUrl: String,
    @SerializedName("received_events_url")
    val receivedEventsUrl: String,
    val type: String,
    @SerializedName("user_view_type")
    val userViewType: String,
    @SerializedName("site_admin")
    val siteAdmin: Boolean
)

data class Asset(
    val url: String,
    val id: Long,
    @SerializedName("node_id")
    val nodeId: String,
    val name: String,
    val label: String?,
    val uploader: Author,
    @SerializedName("content_type")
    val contentType: String,
    val state: String,
    val size: Long,
    @SerializedName("download_count")
    val downloadCount: Int,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String,
    @SerializedName("browser_download_url")
    val browserDownloadUrl: String
)
