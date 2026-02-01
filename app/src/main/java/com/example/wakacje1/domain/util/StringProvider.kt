package com.example.wakacje1.domain.util

interface StringProvider {
    fun getString(resId: Int, vararg args: Any): String
}