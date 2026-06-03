package com.example.yourswelnes.domain.model

data class AuthUser(
    val id: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val gender: String? = null,
    val role: String? = null,
    val status: String? = null,
    val level: String? = null,
    val imageUrl: String? = null,
    val profileImage: String? = null,
    val redirect: String? = null
)
