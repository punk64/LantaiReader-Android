package com.lantai.reader.net

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.lantai.reader.LtApp
import com.lantai.reader.common.J
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object TtsService {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var items: List<String> = emptyList()
    private var idx = 0
    private var total = 0
    private var speed = 1.0f
    private var running = false
    private var paused = false
    private var tone = 0
    private var toneList: List<Pair<String, String>> = emptyList()
    private var stopping = false

    var notify: ((String) -> Unit)? = null
    var onBar: ((Int, Int, Boolean, Boolean) -> Unit)? = null

    private fun publish() {
        onBar?.invoke(if (idx < 0) 0 else idx, items.size, running && !paused, items.isNotEmpty())
    }

    fun position(): Int = idx

    fun count(): Int = items.size

    fun playing(): Boolean = running && !paused

    fun ensure(ctx: Context = LtApp.instance) {
        if (tts != null) return
        tts = TextToSpeech(ctx) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.CHINA
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                    }

                    override fun onDone(utteranceId: String?) {
                        if (stopping || !running || paused) return
                        advance()
                    }

                    @Deprecated("deprecated in API 21")
                    override fun onError(utteranceId: String?) {
                        fail()
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        fail()
                    }
                })
                refreshTones()
            } else {
                push(JSONObject().put("type", "err").toString())
            }
        }
    }

    private fun refreshTones() {
        val engine = tts ?: return
        val list = ArrayList<Pair<String, String>>()
        try {
            val voices = engine.voices ?: emptySet()
            for (v in voices) {
                if (v.locale.language != "zh") continue
                list.add(v.name to v.name)
            }
        } catch (e: Throwable) {
        }
        if (list.isEmpty()) {
            list.add("zh-CN" to "系统语音")
        }
        toneList = list
        if (tone >= toneList.size) tone = 0
    }

    fun start(json: String, ctx: Context = LtApp.instance) {
        ensure(ctx)
        val body = J.parse(json)
        val arr = J.arr(body, "items")
        val list = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val v = arr.opt(i)
            if (v is String && v.isNotEmpty()) list.add(v)
        }
        if (list.isEmpty()) {
            push(JSONObject().put("type", "err").toString())
            return
        }
        items = list
        total = list.size
        speed = J.dbl(body, "speed").toFloat().let { if (it <= 0f) 1.0f else it }
        idx = J.num(body, "from").coerceIn(0, total - 1)
        paused = false
        running = true
        stopping = false
        speakAt(idx)
        publish()
    }

    private fun speakAt(i: Int) {
        val engine = tts ?: return
        if (!ready) {
            push(JSONObject().put("type", "err").toString())
            return
        }
        if (i < 0 || i >= items.size) return
        idx = i
        applyTone(engine)
        engine.setSpeechRate(speed)
        val params = android.os.Bundle()
        val id = "lt-$i"
        engine.speak(items[i], TextToSpeech.QUEUE_FLUSH, params, id)
        push(JSONObject().put("type", "start").put("idx", i).put("total", total).toString())
        publish()
    }

    private fun advance() {
        val next = idx + 1
        if (next >= items.size) {
            running = false
            push(JSONObject().put("type", "end").toString())
            publish()
            return
        }
        speakAt(next)
    }

    private fun fail() {
        if (stopping) return
        running = false
        push(JSONObject().put("type", "err").toString())
        publish()
    }

    private fun applyTone(engine: TextToSpeech) {
        if (toneList.isEmpty()) return
        val pair = toneList.getOrNull(tone) ?: return
        try {
            val voice = engine.voices?.firstOrNull { it.name == pair.first }
            if (voice != null) engine.voice = voice
        } catch (e: Throwable) {
        }
    }

    fun pause() {
        paused = true
        tts?.stop()
        publish()
    }

    fun resume() {
        if (!running) return
        paused = false
        speakAt(idx)
    }

    fun seek(i: Int) {
        if (items.isEmpty()) return
        stopped()
        running = true
        paused = false
        speakAt(i.coerceIn(0, items.size - 1))
    }

    fun stop() {
        stopping = true
        running = false
        paused = false
        items = emptyList()
        idx = 0
        try {
            tts?.stop()
        } catch (e: Throwable) {
        }
        stopping = false
        publish()
    }

    private fun stopped() {
        stopping = true
        try {
            tts?.stop()
        } catch (e: Throwable) {
        }
        stopping = false
    }

    fun once(text: String, ctx: Context = LtApp.instance) {
        ensure(ctx)
        if (text.isEmpty()) return
        val engine = tts ?: return
        if (!ready) return
        applyTone(engine)
        engine.setSpeechRate(speed)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, android.os.Bundle(), "lt-once")
    }

    fun voicesJson(): String {
        val arr = JSONArray()
        for ((name, label) in toneList) {
            arr.put(
                JSONObject()
                    .put("name", label)
                    .put("tone", name)
                    .put("lang", "zh-CN")
                    .put("ready", ready)
            )
        }
        return arr.toString()
    }

    fun toneNow(): String = if (toneList.isEmpty()) "" else toneList.getOrNull(tone)?.first ?: ""

    fun setTone(n: Int) {
        tone = if (toneList.isEmpty()) 0 else n.coerceIn(0, toneList.size - 1)
        applyTone(tts ?: return)
        push(JSONObject().put("type", "tone").put("tone", toneNow()).toString())
    }

    fun reloadVoices() {
        ensure()
        refreshTones()
    }

    fun loadVoices() {
        ensure()
        refreshTones()
        push(JSONObject().put("type", "voices").put("list", JSONArray(voicesJson())).toString())
    }

    private fun push(json: String) {
        notify?.invoke(json)
    }
}
