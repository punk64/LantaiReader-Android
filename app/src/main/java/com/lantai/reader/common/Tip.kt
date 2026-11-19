package com.lantai.reader.common

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.lantai.reader.LtApp

object Tip {
    private val handler = Handler(Looper.getMainLooper())

    fun show(message: String) {
        if (message.isEmpty()) return
        handler.post {
            try {
                Toast.makeText(LtApp.instance, message, Toast.LENGTH_SHORT).show()
            } catch (e: Throwable) {
            }
        }
    }
}

fun toast(message: String) = Tip.show(message)
