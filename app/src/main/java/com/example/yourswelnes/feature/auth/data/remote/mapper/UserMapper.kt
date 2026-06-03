package com.example.yourswelnes.feature.auth.data.remote.mapper

import com.example.yourswelnes.feature.auth.domain.model.AuthUser
import com.example.yourswelnes.feature.auth.data.remote.dto.UserDto

fun UserDto.toDomain(): AuthUser = AuthUser(
    id = when (val rawId = id) {
        is Number -> rawId.toLong().toString()   // Gson deserialises JSON int 4488 as Double(4488.0); toLong() → "4488"
        else      -> rawId?.toString() ?: userId.orEmpty()
    },
    userId = userId.normalize(),
    name = name.orEmpty(),
    email = email.normalize(),
    phone = phone.normalize(),
    gender = gender.normalize(),
    role = role.normalize(),
    status = status.normalize(),
    level = level.normalize(),
    imageUrl = image.normalize()
)

private fun String?.normalize(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
