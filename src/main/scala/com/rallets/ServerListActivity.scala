package com.rallets

import java.util

import android.app.{Activity, ProgressDialog}
import android.content.{BroadcastReceiver, _}
import android.os.{Build, Bundle, Handler, Message}
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import com.github.rallets._
import com.rallets.TitledRecyclerView.OnItemClickListener
import com.github.rallets.ShadowsocksApplication.app
import com.github.rallets.database.Profile
import com.rallets
import com.umeng.analytics.MobclickAgent

object ServerListActivity {
  private final val TAG = "ServerListActivity"
}

class ServerListActivity extends AppCompatActivity with OnItemClickListener {
  import ServerListActivity._

  private val handler = new Handler()
  private var serverListView: TitledRecyclerView = _
  private var serverList: List[Profile] = _

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.layout_server_list)
    val toolbar = findViewById(R.id.toolbar).asInstanceOf[Toolbar]
    setSupportActionBar(toolbar)
    getSupportActionBar.setDisplayHomeAsUpEnabled(true)
    getSupportActionBar.setTitle(R.string.server_list)
    getSupportActionBar.setSubtitle(R.string.server_list_hint)

    serverList = app.profileManager.getAllProfiles.orNull
    val profileItems = new util.ArrayList[TitledRecyclerView.Item]()
    for (p <- serverList) {
      profileItems.add(new rallets.TitledRecyclerView.Item(RUtils.getCountryCodeFlagRes(p.countryCode), p.name, "", false))
    }
    serverListView = findViewById(R.id.serverList).asInstanceOf[TitledRecyclerView]
    serverListView.setOnItemClickListener(this)
    serverListView.setDataSet(profileItems.toArray(Array.ofDim[TitledRecyclerView.Item](profileItems.size)))
    setResult(Activity.RESULT_CANCELED)
  }

  override def onSupportNavigateUp(): Boolean = {
    onBackPressed()
    return true
  }

  override def onItemClick(item: TitledRecyclerView.Item, position: Int): Unit = {
    app.switchProfile(serverList(position).id)
    setResult(Activity.RESULT_OK)
    finish()
  }

  override def onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
  }

  private def ifNotLoggedIn() {
    if (!RUtils.loggedIn) {
      finish()
    }
  }

  private def ralletsReceiver: BroadcastReceiver = new BroadcastReceiver() {
    override def onReceive(context: Context, intent: Intent) = ifNotLoggedIn
  }

  override def onStart() {
    super.onStart()
  }

  override def onResume(): Unit = {
    super.onResume()
    MobclickAgent.onResume(this)
  }

  override def onPause(): Unit = {
    super.onPause()
    MobclickAgent.onPause(this)
  }

  override def onBackPressed(): Unit = {
    super.onBackPressed()
  }

  override def onStop() {
    super.onStop()
  }

  override def onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
  }

  override def onDestroy() {
    super.onDestroy()
  }

}

