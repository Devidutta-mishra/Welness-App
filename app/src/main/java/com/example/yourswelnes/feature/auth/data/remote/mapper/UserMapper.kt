package com.example.yourswelnes.feature.auth.data.remote.mapper

import com.example.yourswelnes.domain.model.AuthUser
import com.example.yourswelnes.feature.auth.data.remote.dto.UserDto

fun UserDto.toDomain(): AuthUser = AuthUser(
    id = id.orEmpty(),
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
