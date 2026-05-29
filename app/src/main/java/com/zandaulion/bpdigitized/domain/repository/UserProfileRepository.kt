package com.zandaulion.bpdigitized.domain.repository

import com.zandaulion.bpdigitized.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserProfileRepository {
    fun observe(): Flow<UserProfile>
    suspend fun save(profile: UserProfile)
}
