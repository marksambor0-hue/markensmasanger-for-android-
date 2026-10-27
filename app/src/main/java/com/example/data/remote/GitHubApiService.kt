package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GitHubCommitItem(
    @Json(name = "sha") val sha: String = "",
    @Json(name = "commit") val commit: GitHubCommitDetail = GitHubCommitDetail(),
    @Json(name = "html_url") val htmlUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubCommitDetail(
    @Json(name = "message") val message: String = "",
    @Json(name = "author") val author: GitHubCommitAuthor? = null
)

@JsonClass(generateAdapter = true)
data class GitHubCommitAuthor(
    @Json(name = "name") val name: String = "GitHub Dev",
    @Json(name = "date") val date: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubRepoInfo(
    @Json(name = "full_name") val fullName: String = "",
    @Json(name = "description") val description: String? = null,
    @Json(name = "stargazers_count") val stars: Int = 0,
    @Json(name = "open_issues_count") val openIssues: Int = 0,
    @Json(name = "default_branch") val defaultBranch: String = "main"
)

interface GitHubApiService {
    @Headers("Accept: application/vnd.github.v3+json", "User-Agent: GitPulse-Android-Messenger")
    @GET("repos/{owner}/{repo}/commits")
    suspend fun getRecentCommits(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 5
    ): List<GitHubCommitItem>

    @Headers("Accept: application/vnd.github.v3+json", "User-Agent: GitPulse-Android-Messenger")
    @GET("repos/{owner}/{repo}")
    suspend fun getRepositoryInfo(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRepoInfo

    companion object {
        fun create(): GitHubApiService {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
            return Retrofit.Builder()
                .baseUrl("https://api.github.com/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(GitHubApiService::class.java)
        }
    }
}
