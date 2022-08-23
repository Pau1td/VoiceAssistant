package com.example.voiceassistant

import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Display
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.SimpleAdapter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.wolfram.alpha.WAEngine
import com.wolfram.alpha.WAPlainText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.collections.HashMap


class MainActivity : AppCompatActivity() {

    val TAG: String = "MainActivity"

    lateinit var requestInput: TextInputEditText
    lateinit var podsAdapter: SimpleAdapter

    lateinit var progressBar: ProgressBar

    lateinit var waEngine: WAEngine

    lateinit var textToSpeech: TextToSpeech

    var isTtsReady : Boolean = false

    val VOICE_RECOGNITION_REQUEST_CODE: Int = 777

    val pods = mutableListOf<HashMap<String, String>>(
  /*      HashMap<String, String>().apply {
            put("Title", "Title 1")
            put("Content", "Content 1")
            Log.d(TAG, "Content 1")
        },
        HashMap<String, String>().apply {
            put("Title", "Title 2")
            put("Content", "Content 2")
            Log.d(TAG, "Content 2")
        },
        HashMap<String, String>().apply {
            put("Title", "Title 3")
            put("Content", "Content 3")
            Log.d(TAG, "Content 3")
        },
        HashMap<String, String>().apply {
            put("Title", "Title 4")
            put("Content", "Content 4")
            Log.d(TAG, "Content 4")
        }
   */
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

/*
        val TAG: String = "MainActivity"
        Log.d(TAG, "start of onCreate function")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val name: String = "Ivan"
        val surname: String = "Ivanov"
        val age: Int = 37
        val height: Double = 172.2

       // height = height * 2

        val sum: String = "name: $name surname: $surname age: $age height: $height"

        val output1: TextView = findViewById(R.id.output)
        output1.text = sum

        Log.w(TAG, sum)
        Log.d(TAG, "end of onCreate function")
*/
        initViews()
        initWalframEngine()
        initTts()

    }

    fun initViews() {
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        requestInput = findViewById(R.id.text_input_edit)
        requestInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                pods.clear()
                podsAdapter.notifyDataSetChanged()

                val question = requestInput.text.toString()
                askWolfram(question)
            }

            return@setOnEditorActionListener false
        }

        val podsList: ListView = findViewById(R.id.pods_list)

        podsAdapter = SimpleAdapter(
            applicationContext,
            pods,
            R.layout.item_pod,
            arrayOf("Title", "Content"),
            intArrayOf(R.id.title, R.id.content)
        )
        podsList.adapter = podsAdapter
        podsList.setOnItemClickListener { parent, view, position, id ->
            if (isTtsReady){
                val title = pods[position]["Title"]
                val content = pods[position]["Content"]
                textToSpeech.speak(content, TextToSpeech.QUEUE_FLUSH, null, title)
            }
        }

        val voiceInputButton: FloatingActionButton = findViewById(R.id.voice_input_button)
        voiceInputButton.setOnClickListener {
            Log.d(TAG, "FAB")
            pods.clear()
            podsAdapter.notifyDataSetChanged()

            if (isTtsReady){
                textToSpeech.stop()

                showVoiceInputDialog()
            }
        }

        progressBar = findViewById(R.id.progress_bar)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_stop -> {
                Log.d(TAG, "action stop")
                if (isTtsReady){
                    textToSpeech.stop()
                }
            }
            R.id.action_clear -> {
                Log.d(TAG, "action clear")
                requestInput.text?.clear()
                pods.clear()
                podsAdapter.notifyDataSetChanged()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    fun initWalframEngine() {
        waEngine = WAEngine().apply {
            appID = "YJTLQ5-QY5J6WW488"
            addFormat("plaintext")
        }
    }

    fun showSnackbar(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_INDEFINITE)
            .apply {
                setAction(android.R.string.ok) {
                    dismiss()
                }
                show()
            }
    }

    fun askWolfram(request: String) {
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val query = waEngine.createQuery().apply { input = request }
            runCatching {
                waEngine.performQuery(query)
            }.onSuccess { result ->
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (result.isError){
                        showSnackbar(result.errorMessage)
                        return@withContext
                    }

                    if (!result.isSuccess) {
                        requestInput.error = getString(R.string.error_something_went_wrong)
                        return@withContext
                    }

                    for (pod in result.pods){
                        if (pod.isError) continue
                        val content = StringBuilder ()
                        for (subpod in pod.subpods) {
                            for (element in subpod.contents){
                                if (element is WAPlainText) {
                                    content.append(element.text)
                                }
                            }
                        }
                       pods.add(0,HashMap<String, String>().apply{
                           put("Title", pod.title)
                           put("Content", content.toString())
                       })
                    }
                podsAdapter.notifyDataSetChanged()
                }
            }.onFailure { t ->
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    showSnackbar(t.message ?: getString(R.string.error_something_went_wrong))
                }
            }
        }

    }


    fun initTts(){
        textToSpeech = TextToSpeech(this) { code ->
            if (code != TextToSpeech.SUCCESS){
                Log.e(TAG, "TTS error code: $code")
                showSnackbar(getString(R.string.error_tts_is_not_ready))
            } else {
                isTtsReady = true
            }
        }
        textToSpeech.language = Locale.US
    }

    fun showVoiceInputDialog(){
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.request_hint))
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
        }
        runCatching {
            startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE )

        }.onFailure { t ->
                showSnackbar(t.message ?: getString(R.string.error_voice_recognition_unavailable) )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK){
            data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)?.let{question ->
                requestInput.setText(question)
                askWolfram(question)
            }
        }
    }


}
