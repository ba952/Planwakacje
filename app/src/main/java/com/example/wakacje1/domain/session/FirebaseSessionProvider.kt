package com.example.wakacje1.domain.session

import com.google.firebase.auth.FirebaseAuth

class FirebaseSessionProvider(
    private val firebaseAuth: FirebaseAuth
) : SessionProvider {

    override fun currentUid(): String? = firebaseAuth.currentUser?.uid
}
