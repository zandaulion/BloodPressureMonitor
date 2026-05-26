package com.example.bpdigitizer.domain.repository

import com.example.bpdigitizer.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserProfileRepository {
    fun observe(): Flow<UserProfile>
    suspend fun save(profile: UserProfile)
}
