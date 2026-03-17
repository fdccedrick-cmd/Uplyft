package com.example.uplyft.data.remote.firebase

import com.example.uplyft.domain.model.User
import com.example.uplyft.utils.Constants.USERS_COLLECTION
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthFirebaseSource {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    suspend fun login(email: String, password: String): User {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val uid = result.user!!.uid
        val doc = db.collection(USERS_COLLECTION).document(uid).get().await()
        return doc.toObject(User::class.java)!!.copy(uid = uid)
    }

    suspend fun signup(email: String, password: String, fullName: String): User {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user!!.uid
        val user = User(uid = uid, fullName = fullName, email = email)
        db.collection(USERS_COLLECTION).document(uid).set(user).await()
        return user
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun logout() = auth.signOut()

}