package com.rallets

import android.app.{Activity, AlertDialog, ProgressDialog}
import android.content.DialogInterface
import android.webkit.{WebView, WebViewClient}
import android.widget.Toast
import com.github.rallets.R

class RalletsWebClient(activity: Activity, title: String) extends WebViewClient {
  val alertDialog = new AlertDialog.Builder(activity).create()
  val progressBar = ProgressDialog.show(activity, title, activity.getString(R.string.loading))

  override def onReceivedSslError(view: _root_.android.webkit.WebView, handler: _root_.android.webkit.SslErrorHandler, error: _root_.android.net.http.SslError): Unit = {
    import android.content.DialogInterface
    val builder = new AlertDialog.Builder(activity)
    builder.setMessage(R.string.ssl_error_alert)
    builder.setPositiveButton(R.string.continue_browse, new DialogInterface.OnClickListener() {
      override def onClick(dialog: DialogInterface, which: Int): Unit = {
        handler.proceed
      }
    })
    builder.setNegativeButton(R.string.cancel_browse, new DialogInterface.OnClickListener() {
      override def onClick(dialog: DialogInterface, which: Int): Unit = {
        handler.cancel
      }
    })
    val dialog = builder.create
    dialog.show()
  }

  override def shouldOverrideUrlLoading(view: WebView, url: String): Boolean =  {
    view.loadUrl(url)
    return false
  }

  override def onPageFinished(view: WebView, url: String): Unit = {
    RUtils.log("Finished loading URL: " + url)
    if (progressBar.isShowing()) {
      progressBar.dismiss()
    }
  }

  override def onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String): Unit = {
    RUtils.log("Error: " + description)
    Toast.makeText(activity, "Oh no! " + description, Toast.LENGTH_SHORT).show()
    alertDialog.setTitle("Error")
    alertDialog.setMessage(description)
    alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
      def onClick(dialog: DialogInterface, which: Int) {}
    })
    alertDialog.show()
  }
}
