package com.rallets

import java.util
import java.util.{Collections, Comparator}

import android.app.{Activity, Dialog, ProgressDialog}
import android.content.{BroadcastReceiver, _}
import android.os.{Build, Bundle, Handler, Message}
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.View
import android.widget.{Button, TextView, Toast}
import com.github.rallets._
import com.rallets.TitledRecyclerView.OnItemClickListener
import com.github.rallets.ShadowsocksApplication.app
import com.github.rallets.database.Profile
import com.google.gson.GsonBuilder
import com.loopj.android.http.AsyncHttpResponseHandler
import com.pingplusplus.android.Pingpp
import com.rallets
import com.rallets.Model.{Good, Notification}
import com.umeng.analytics.MobclickAgent
import cz.msebera.android.httpclient.Header
import org.json.JSONObject

object PaymentActivity {
  private final val TAG = "PaymentActivity"
}

class PaymentActivity extends AppCompatActivity with OnItemClickListener {
  import PaymentActivity._

  private val handler = new Handler()
  private var paymentListView: TitledRecyclerView = _
  private var paymentList: util.ArrayList[Good] = _
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.layout_payment_list)
    val toolbar = findViewById(R.id.toolbar).asInstanceOf[Toolbar]
    setSupportActionBar(toolbar)
    getSupportActionBar.setDisplayHomeAsUpEnabled(true)
    getSupportActionBar.setTitle(R.string.payments)
    paymentList = new util.ArrayList[Good]()
    paymentListView = findViewById(R.id.paymentList).asInstanceOf[TitledRecyclerView]
    paymentListView.setOnItemClickListener(this)
    retrieveData()
  }

  override def onResume(): Unit = {
    super.onResume()
    MobclickAgent.onResume(this)
  }

  override def onPause(): Unit = {
    super.onPause()
    MobclickAgent.onPause(this)
  }

  override def onSupportNavigateUp(): Boolean = {
    onBackPressed()
    return true
  }

  override def onItemClick(item: TitledRecyclerView.Item, position: Int): Unit = {
  }

  def notifyDataSetChanged(): Unit = {
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
  }

  def retrieveData() {
  }
}

