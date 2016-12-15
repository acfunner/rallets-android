package com.rallets

import java.io.ByteArrayOutputStream
import java.util

import android.graphics.Bitmap.CompressFormat
import android.graphics.{Bitmap, BitmapFactory}
import android.os.{Build, Bundle, Handler, Message}
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.View.OnClickListener
import android.widget.Button
import com.github.rallets._
import com.tencent.mm.opensdk.modelmsg.{SendMessageToWX, WXMediaMessage, WXTextObject, WXWebpageObject}
import com.tencent.mm.opensdk.openapi.{IWXAPI, WXAPIFactory}
import com.umeng.analytics.MobclickAgent

object ShareActivity {
  private final val TAG = "ShareActivity"
}

class ShareActivity extends AppCompatActivity {
  import ShareActivity._

  var btnShareWx: Button = _

  private var api: IWXAPI = _
  def regToWx(): Unit = {
  }

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.layout_share)
  }

  def bmpToByteArray(bmp: Bitmap, needRecycle: Boolean): Array[Byte] = {
    val output = new ByteArrayOutputStream
    bmp.compress(CompressFormat.JPEG, 80, output)
    if (needRecycle) bmp.recycle()
    val result = output.toByteArray
    try
      output.close()
    catch {
      case e: Exception =>
        e.printStackTrace()
    }
    result
  }

  def shareToWx(): Unit = {
  }

  override def onResume(): Unit = {
    super.onResume()
    MobclickAgent.onResume(this)
  }

  override def onPause(): Unit = {
    super.onPause()
    MobclickAgent.onPause(this)
  }
}

