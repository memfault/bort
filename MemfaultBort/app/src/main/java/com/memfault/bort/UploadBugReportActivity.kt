package com.memfault.bort

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.memfault.bort.requester.BugReportRequester


class UploadBugReportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload)
    }

    fun createBugReport(@Suppress("UNUSED_PARAMETER") v: View) {
        val message = "Requesting bug report; see logcat for details"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        BugReportRequester(this).request()
    }
}
