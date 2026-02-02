package com.example.wakacje1.data.session

import com.example.wakacje1.domain.session.SessionProvider
import com.google.firebase.auth.FirebaseAuth

class FirebaseSessionProvider(
    private val firebaseAuth: FirebaseAuth
) : SessionProvider {

    override fun currentUid(): String? = firebaseAuth.currentUser?.uid
}
