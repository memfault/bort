package com.memfault.bort

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.work.WorkManager
import com.memfault.bort.requester.BugReportRequester


class UploadBugReportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload)
    }

    fun createBugReport(@Suppress("UNUSED_PARAMETER") v: View) =
        BugReportRequester(
            this
        ).request().also {
            WorkManager.getInstance(this)
                .getWorkInfoByIdLiveData(it)
                .observe(this, Observer { workInfo ->
                    workInfo ?: return@Observer
                    val message = "Request now in state: ${workInfo.state}"
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    Logger.d(message)
                })
        }
}
