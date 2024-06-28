package com.example.mychat

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.text.isDigitsOnly
import androidx.lifecycle.ViewModel
import com.example.mychat.data.CHATS
import com.example.mychat.data.ChatData
import com.example.mychat.data.ChatUser
import com.example.mychat.data.Events
import com.example.mychat.data.MESSAGE
import com.example.mychat.data.USER_NODE
import com.example.mychat.data.UserData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.toObject
import com.google.firebase.firestore.toObjects
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import kotlin.Exception
import com.example.mychat.data.STATUS
import com.example.mychat.data.Status

@HiltViewModel
class LCViewModal @Inject constructor(
    val auth: FirebaseAuth,
    var db: FirebaseFirestore,
    val storage: FirebaseStorage
) : ViewModel() {

    var inProgress = mutableStateOf(false)
    var inProcessChat = mutableStateOf(false)
    val eventMutableState = mutableStateOf<Events<String>?>(null)
    var signIn = mutableStateOf(false)
    val userData = mutableStateOf<UserData?>(null)
    val chats = mutableStateOf<List<ChatData>>(listOf())
    val chatMessages= mutableStateOf<List<com.example.mychat.data.Message>>(listOf())
    val inProgressChatMessage= mutableStateOf(false)
    var currentChatMessageListener:ListenerRegistration?=null

    val status = mutableStateOf<List<Status>>(listOf())
    val inProgressStatus= mutableStateOf(false)

    init {
        val currentUser = auth.currentUser
        signIn.value = currentUser != null
        currentUser?.uid?.let {
            getUserData(it)
        }
    }
    fun populateMessages(chatID: String){
        inProgressChatMessage.value=true
        currentChatMessageListener=db.collection(CHATS).document(chatID).collection(MESSAGE).addSnapshotListener{
            value,error->
            if (error!=null){
                handleException(error)
            }
            if(value!=null){
                chatMessages.value=value.documents.mapNotNull {
                    it.toObject<com.example.mychat.data.Message>()
                }.sortedBy { it.timestamp }
                inProgressChatMessage.value=false
            }
        }
    }
    fun dePopulate(){
        chatMessages.value= listOf()
        currentChatMessageListener=null
    }
    fun populateChats() {
        inProcessChat.value = true
        db.collection(CHATS).where(
            Filter.or(
                Filter.equalTo("user1.userId", userData.value?.userId),
                Filter.equalTo("user2.userId", userData.value?.userId)

            )
        ).addSnapshotListener { value, error ->
            if (error != null) {
                handleException(error)

            }
            if (value != null) {
                chats.value = value.documents.mapNotNull {
                    it.toObject<ChatData>()
                }
                inProcessChat.value = false
            }
        }

    }

    fun onsendReply(chatID: String, message: String) {
        val time = Calendar.getInstance().time.toString()
        val msg = com.example.mychat.data.Message(userData.value?.userId, message, time)
        db.collection(CHATS).document(chatID).collection(MESSAGE).document().set(msg)
    }

    fun signUp(name: String, number: String, email: String, password: String) {
        inProgress.value = true
        if (name.isEmpty() || number.isEmpty() || email.isEmpty() || password.isEmpty()) {
            handleException(customMessage = "Please Fill All fields")
            return
        }
        inProgress.value = true
        db.collection(USER_NODE).whereEqualTo("number", number).get().addOnSuccessListener {
            if (it.isEmpty) {
                auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener {
                    if (it.isSuccessful) {
                        signIn.value = true
                        createOrUpdateProfile(name, number)
                        Log.d("TAG", "Signup is successful")
                    } else {
                        handleException(it.exception, customMessage = "Sign Up failed")
                    }
                }
            } else {
                handleException(customMessage = "numner already existed ")
                inProgress.value = false
            }
        }

    }

    fun loginIn(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            handleException(customMessage = "Please Fill All Fields")
        } else {
            inProgress.value = true
            auth.signInWithEmailAndPassword(email, password).addOnCompleteListener {
                if (it.isSuccessful) {
                    signIn.value = true
                    inProgress.value = false
                    auth.currentUser?.uid?.let {
                        getUserData(it)
                    }
                } else {
                    handleException(exception = it.exception, customMessage = "Login Failed")
                }
            }
        }
    }

    fun uploadProfileImage(uri: Uri) {
        uploadImage(uri) {
            createOrUpdateProfile(imageurl = it.toString())
        }
    }

    fun uploadImage(uri: Uri, onSuccess: (Uri) -> Unit) {
        inProgress.value = true
        val storageRef = storage.reference
        val uuid = UUID.randomUUID()
        val imageRef = storageRef.child("images/$uuid")
        val uploadTask = imageRef.putFile(uri)
        uploadTask.addOnSuccessListener {
            val result = it.metadata?.reference?.downloadUrl

            result?.addOnSuccessListener(onSuccess)
            inProgress.value = false

        }
            .addOnFailureListener {
                handleException(it)
            }

    }

    fun createOrUpdateProfile(
        name: String? = null,
        number: String? = null,
        imageurl: String? = null
    ) {
        var uid = auth.currentUser?.uid
        val userData = UserData(
            userId = uid,
            name = name ?: userData.value?.name,
            number = number ?: userData.value?.number,
            imageUrl = imageurl ?: userData.value?.imageUrl
        )
        uid?.let {
            inProgress.value = true
            db.collection(USER_NODE).document(uid).get().addOnSuccessListener {
                if (it.exists()) {
                    val userData = it.toObject<UserData>()
                    userData?.let { existingUserData ->
                        val updatedName = name ?: existingUserData.name
                        val updatedNumber = number ?: existingUserData.number
                        val updatedImageUrl = imageurl ?: existingUserData.imageUrl

                        val updatedUserData = UserData(
                            userId = uid,
                            name = updatedName,
                            number = updatedNumber,
                            imageUrl = updatedImageUrl
                        )

                        db.collection(USER_NODE).document(uid).set(updatedUserData)
                            .addOnSuccessListener {
                                // Successfully updated
                                inProgress.value = false
                            }
                            .addOnFailureListener {
                                // Handle failure
                                inProgress.value = false
                                handleException(it,"error")
                            }
                    }


                } else {
                    db.collection(USER_NODE).document(uid).set(userData)
                    inProgress.value = false
                    getUserData(uid)
                }

            }
                .addOnFailureListener {
                    handleException(it, "Cannot Retrieve User")
                }

        }
    }

    private fun getUserData(uid: String) {
        inProgress.value = true
        db.collection(USER_NODE).document(uid).addSnapshotListener { value, error ->
            if (error != null) {
                handleException(error, "Cannot Retrieve User")
            }
            if (value != null) {
                var user = value.toObject<UserData>()
                userData.value = user
                inProgress.value = false
                populateChats()
                populateStatuses()
            }
        }
    }

    fun handleException(exception: Exception? = null, customMessage: String = "") {
        Log.e("LiveChatApp", "Live Chat exception", exception)
        exception?.printStackTrace()
        val errorMsg = exception?.localizedMessage ?: ""
        val message = if (customMessage.isNullOrEmpty()) errorMsg else customMessage
        eventMutableState.value = Events(message)
        inProgress.value = false
    }

    fun logout() {
        auth.signOut()
        signIn.value = false
        userData.value = null
        dePopulate()
        currentChatMessageListener=null
        eventMutableState.value = Events("Logged Out")
    }

    fun onAddChat(number: String) {
        if (number.isEmpty() or !number.isDigitsOnly()) {
            handleException(customMessage = "number must be contained digits only")
        } else {
            db.collection(CHATS).where(
                Filter.or(
                    Filter.and(
                        Filter.equalTo("user1.number", number),
                        Filter.equalTo("user2.number", userData.value?.number)
                    ),
                    Filter.and(
                        Filter.equalTo("user1.number", userData.value?.number),
                        Filter.equalTo("user2.number", number)
                    )
                )
            ).get().addOnSuccessListener {
                if (it.isEmpty) {
                    db.collection(USER_NODE).whereEqualTo("number", number).get()
                        .addOnSuccessListener {
                            if (it.isEmpty) {
                                handleException(customMessage = "number not found")
                            } else {
                                val chatPartner = it.toObjects<UserData>()[0]
                                val id = db.collection(CHATS).document()
                                    .id
                                val chat = ChatData(
                                    chatId = id,
                                    ChatUser(
                                        userData.value?.userId,
                                        userData.value?.name,
                                        userData.value?.imageUrl,
                                        userData.value?.number
                                    ),
                                    ChatUser(
                                        chatPartner.userId,
                                        chatPartner.name,
                                        chatPartner.imageUrl,
                                        chatPartner.number
                                    )
                                )
                                db.collection(CHATS).document(id).set(chat)
                            }
                        }
                        .addOnFailureListener {
                            handleException(it)
                        }

                } else {
                    handleException(customMessage = "Chat Already exists")
                }
            }
        }
    }

    fun uploadStatus(uri: Uri) {
            uploadImage(uri){
                createStatus(it.toString())
            }
    }
    fun createStatus(imageurl: String){
        val newStatus=Status(
            ChatUser(
                userData.value?.userId,
                userData.value?.name,
                userData.value?.imageUrl,
                userData.value?.number,

            ),
            imageurl,
            System.currentTimeMillis()
        )
        db.collection(STATUS).document().set(newStatus)
    }
    fun populateStatuses() {
        val timeDelta= 24L *60 *60 *1000
        val cutOff=System.currentTimeMillis()-timeDelta
        inProgressStatus.value = true
        db.collection(CHATS).where(
            Filter.or(
                Filter.equalTo("user1.userId", userData.value?.userId),
                Filter.equalTo("user2.userId", userData.value?.userId)
            )
        ).addSnapshotListener {
        value, error->
        if (error != null) {
            handleException(error)
        }
        if (value != null) {
            val currentConnections = arrayListOf(userData.value?.userId)

            val chats=value.toObjects<ChatData>()
            chats.forEach{
                chat->
                if(chat.user1.userId==userData.value?.userId){
                    currentConnections.add(chat.user2.userId)
                }
                else{
                    currentConnections.add(chat.user1.userId)
                }
            }
            db.collection(STATUS).whereGreaterThan("timestamp",cutOff).whereIn("user.userId",currentConnections).addSnapshotListener{
                value,error->
                if(error!=null){
                    handleException(error)
                }
                if(value!=null){
                    status.value=value.toObjects()
                    inProgressStatus.value=false
                }
            }
        }
    }
    }

}
