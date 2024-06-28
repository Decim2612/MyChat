package com.example.mychat.data

open class Events <out T> (val content:T){
    var hasBeenHandled=false
    fun getContentorNull():T?{
        return if (hasBeenHandled)null
        else{
            hasBeenHandled=false
            content
        }
    }
}