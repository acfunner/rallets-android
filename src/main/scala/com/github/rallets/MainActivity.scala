/*******************************************************************************/
/*                                                                             */
/*  Copyright (C) 2017 by Max Lv <max.c.lv@gmail.com>                          */
/*  Copyright (C) 2017 by Mygod Studio <contact-shadowsocks-android@mygod.be>  */
/*                                                                             */
/*  This program is free software: you can redistribute it and/or modify       */
/*  it under the terms of the GNU General Public License as published by       */
/*  the Free Software Foundation, either version 3 of the License, or          */
/*  (at your option) any later version.                                        */
/*                                                                             */
/*  This program is distributed in the hope that it will be useful,            */
/*  but WITHOUT ANY WARRANTY; without even the implied warranty of             */
/*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              */
/*  GNU General Public License for more details.                               */
/*                                                                             */
/*  You should have received a copy of the GNU General Public License          */
/*  along with this program. If not, see <http://www.gnu.org/licenses/>.       */
/*                                                                             */
/*******************************************************************************/

package com.github.rallets

import java.util.Locale

import android.app.backup.BackupManager
import android.app.{Activity, ProgressDialog}
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content._
import android.net.{Uri, VpnService}
import android.nfc.{NdefMessage, NfcAdapter}
import android.os.{Build, Bundle, Handler, Message}
import android.support.customtabs.CustomTabsIntent
import android.support.design.widget.{FloatingActionButton, Snackbar}
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.content.res.AppCompatResources
import android.support.v7.widget.RecyclerView.ViewHolder
import android.support.v7.widget.Toolbar
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.{ImageView, RelativeLayout, TextView, Toast}
import com.github.jorgecastilloprz.FABProgressCircle
import com.github.rallets.ShadowsocksApplication.app
import com.github.rallets.acl.CustomRulesFragment
import com.github.rallets.aidl.IShadowsocksServiceCallback
import com.github.rallets.utils.CloseUtils.autoDisconnect
import com.github.rallets.utils._
import com.mikepenz.crossfader.Crossfader
import com.mikepenz.crossfader.view.CrossFadeSlidingPaneLayout
import com.mikepenz.materialdrawer.interfaces.ICrossfader
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem
import com.mikepenz.materialdrawer.model.{PrimaryDrawerItem, SecondaryDrawerItem, _}
import com.mikepenz.materialdrawer.{AccountHeader, AccountHeaderBuilder, Drawer, DrawerBuilder}
import com.loopj.android.http.{AsyncHttpResponseHandler, RequestParams}
import com.rallets.Model.Notification
import com.rallets._
import cz.msebera.android.httpclient.Header
import org.json.JSONObject
import android.content.BroadcastReceiver
import android.view.animation.{Animation, AnimationUtils}
import com.pingplusplus.android.{Pingpp, PingppLog}
import com.umeng.analytics.MobclickAgent

trait TrafficCallback {
  def update(txRate: Long, rxRate: Long, txTotal: Long, rxTotal: Long)
}

object MainActivity {
  private final val TAG = "MainActivity"
  private final val REQUEST_CONNECT = 1

  private final val DRAWER_INTRO = 0L
  private final val DRAWER_GLOBAL_SETTINGS = 1L
  private final val DRAWER_RECOVERY = 2L
  private final val DRAWER_ABOUT = 3L
  private final val DRAWER_FAQ = 4L
  private final val DRAWER_CUSTOM_RULES = 5L
  private final val DRAWER_ACCOUNT = 6L
  private final val DRAWER_LOGOUT = 7L
  private final val DRAWER_AGREEMENT = 8L
}

class MainActivity extends Activity with ServiceBoundContext with Drawer.OnDrawerItemClickListener
  with OnSharedPreferenceChangeListener {
  import MainActivity._

  // UI
  private val handler = new Handler()
  var crossfader: Crossfader[CrossFadeSlidingPaneLayout] = _
  var drawer: Drawer = _

  private var rocket: ImageView = _
  private var currentFragment: ToolbarFragment = _
  private lazy val introFragment = new IntroFragment()
  private lazy val myProfileFragment = new MyProfileFragment()
  private lazy val customRulesFragment = new CustomRulesFragment()
  private lazy val globalSettingsFragment = new GlobalSettingsFragment()
  private lazy val allFragments: Array[ToolbarFragment] = Array (
    introFragment,
    myProfileFragment,
    customRulesFragment,
    globalSettingsFragment
  )
  private lazy val DEFAULT_FRAGMENT = introFragment
  private val DEFAULT_DRAWER_SELECTED = DRAWER_INTRO
  private lazy val customTabsIntent = new CustomTabsIntent.Builder()
    .setToolbarColor(ContextCompat.getColor(this, R.color.primary_500))
    .build()
  def launchUrl(url: String): Unit = try customTabsIntent.launchUrl(this, Uri.parse(url)) catch {
    case _: ActivityNotFoundException => // Ignore
  }

  val mainActivity = this
  var drawerHeader: AccountHeader = _
  var isInFront = false
  var trafficEmptyShowed = false

  // Services
  var state: Int = State.IDLE
  private val callback = new IShadowsocksServiceCallback.Stub {
    def stateChanged(s: Int, profileName: String, m: String): Unit = handler.post(() => {
      changeState(s, profileName, m)
    })
    def trafficUpdated(profileId: Int, txRate: Long, rxRate: Long, txTotal: Long, rxTotal: Long): Unit =
      handler.post(() => {
        updateTraffic(profileId, txRate, rxRate, txTotal, rxTotal)
      })
    override def trafficPersisted(profileId: Int): Unit = handler.post(() => {
      for (f <- allFragments) {
        if (f.isVisible)
          f.onTrafficPersisted(profileId)
      }
    })
  }

  private def changeState(s: Int, profileName: String = null, m: String = null) {
    s match {
      case State.CONNECTING => ()
      case State.STOPPING => rocketDown()
      case State.CONNECTED => if (state != State.CONNECTED) rocketUp()
      case _ => {
        if (m != null) {
          val snackbar = Snackbar.make(findViewById(R.id.snackbar),
            getString(R.string.vpn_error).formatLocal(Locale.ENGLISH, m), Snackbar.LENGTH_LONG)
          if (m == getString(R.string.nat_no_root)) addDisableNatToSnackbar(snackbar)
          snackbar.show()
          Log.e(TAG, "Error to start VPN service: " + m)
        }
      }
    }
    state = s
    // Notify all fragments
    for (f <- allFragments) {
      if (f.isVisible)
        f.onStateChanged(s, profileName, m)
    }
    if (state != State.CONNECTED) {
      updateTraffic(-1, 0, 0, 0, 0)
    }
  }

  def updateTraffic(profileId: Int, txRate: Long, rxRate: Long, txTotal: Long, rxTotal: Long) {
    // Notify all fragments
    for (f <- allFragments) {
      if (f.isVisible)
        f.onTrafficUpdated(profileId, txRate, rxRate, txTotal, rxTotal)
    }
  }

  private def rocketAnim(animRes: Int): Unit = {
    rocket.setVisibility(View.VISIBLE)
    val anim = AnimationUtils.loadAnimation(this, animRes)
    anim.setAnimationListener(new Animation.AnimationListener {
      override def onAnimationEnd(animation: Animation): Unit = {
        rocket.setVisibility(View.GONE)
      }
      override def onAnimationRepeat(animation: Animation): Unit = {}
      override def onAnimationStart(animation: Animation): Unit = {}
    })
    rocket.startAnimation(anim)
  }

  private def rocketUp(): Unit = {
    rocket.setScaleY(-1)
    rocketAnim(R.anim.rocket_up)
  }

  private def rocketDown(): Unit = {
    rocket.setScaleY(1)
    rocketAnim(R.anim.rocket_down)
  }

  override def onServiceConnected() {
    changeState(bgService.getState)
    if (Build.VERSION.SDK_INT >= 21 && app.isNatEnabled) {
      val snackbar = Snackbar.make(findViewById(R.id.snackbar), R.string.nat_deprecated, Snackbar.LENGTH_LONG)
      addDisableNatToSnackbar(snackbar)
      snackbar.show()
    }
  }
  override def onServiceDisconnected(): Unit = {
    changeState(State.IDLE)
  }

  private def addDisableNatToSnackbar(snackbar: Snackbar) = snackbar.setAction(R.string.switch_to_vpn, (_ =>
    if (state == State.STOPPED) app.editor.putBoolean(Key.isNAT, false)): View.OnClickListener)

  override def binderDied() {
    detachService()
    app.crashRecovery()
    attachService(callback)
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    resultCode match {
      case Activity.RESULT_OK => bgService.use(app.profileId)
      case _ => Log.e(TAG, "Failed to start VpnService")
    }
  }

  def drawerMenuItem(identifier: Long, name: Int, icon: Int): PrimaryDrawerItem =
    new PrimaryDrawerItem().withName(name).withIcon(icon).withIdentifier(identifier).withIconTintingEnabled(true)

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    PingppLog.DEBUG = true

    setContentView(R.layout.layout_main)
    rocket = findViewById(R.id.rocket).asInstanceOf[ImageView]
    val drawerBuilder = new DrawerBuilder()
      .withActivity(this)
      .withTranslucentStatusBar(true)
      .withHeader(R.layout.layout_header)
      .addDrawerItems(
        drawerMenuItem(DRAWER_INTRO, R.string.home, R.drawable.ic_home_white_24dp),
        drawerMenuItem(DRAWER_ACCOUNT, R.string.account, R.drawable.ic_account_circle_white_24dp),
        drawerMenuItem(DRAWER_CUSTOM_RULES, R.string.custom_rules, R.drawable.ic_assignment_white_24dp),
        drawerMenuItem(DRAWER_GLOBAL_SETTINGS, R.string.settings, R.drawable.ic_settings_white_24dp)
      )
      .addStickyDrawerItems(
        drawerMenuItem(DRAWER_FAQ, R.string.faq, R.drawable.ic_help_outline_white_24dp).withSelectable(false),
        drawerMenuItem(DRAWER_AGREEMENT, R.string.agreement_title, R.drawable.ic_error_white_24dp).withSelectable(false),
        drawerMenuItem(DRAWER_RECOVERY, R.string.recovery, R.drawable.ic_refresh_white_24dp).withSelectable(false),
        drawerMenuItem(DRAWER_ABOUT, R.string.about, R.drawable.ic_copyright_white_24dp).withSelectable(false),
        drawerMenuItem(DRAWER_LOGOUT, R.string.logout, R.drawable.ic_power_settings_new_white_24dp).withSelectable(false)
      )
      .withOnDrawerItemClickListener(this)
      .withActionBarDrawerToggle(true)
      .withSavedInstance(savedInstanceState)
      .withSliderBackgroundDrawableRes(R.drawable.page_bg)
    val miniDrawerWidth = getResources.getDimension(R.dimen.material_mini_drawer_item)
    if (getResources.getDisplayMetrics.widthPixels >= getResources.getDimension(R.dimen.profile_item_max_width) + miniDrawerWidth) {
      drawer = drawerBuilder.withGenerateMiniDrawer(true).buildView()
      crossfader = new Crossfader[CrossFadeSlidingPaneLayout]()
      crossfader.withContent(findViewById(android.R.id.content))
        .withFirst(drawer.getSlider, getResources.getDimensionPixelSize(R.dimen.material_drawer_width))
        .withSecond(drawer.getMiniDrawer.build(this), miniDrawerWidth.toInt)
        .withSavedInstance(savedInstanceState)
        .build()
      crossfader.getFirst.setBackgroundResource(R.drawable.page_bg)
      crossfader.getSecond.setBackgroundResource(R.color.dark_blue_700)
      if (getResources.getConfiguration.getLayoutDirection == View.LAYOUT_DIRECTION_RTL)
        crossfader.getCrossFadeSlidingPaneLayout.setShadowDrawableRight(
          AppCompatResources.getDrawable(this, R.drawable.material_drawer_shadow_right))
      else crossfader.getCrossFadeSlidingPaneLayout.setShadowDrawableLeft(
        AppCompatResources.getDrawable(this, R.drawable.material_drawer_shadow_left))
      drawer.getMiniDrawer.withCrossFader(new ICrossfader { // a wrapper is needed
        def isCrossfaded: Boolean = crossfader.isCrossFaded
        def crossfade(): Unit = crossfader.crossFade()
      })
    } else drawer = drawerBuilder.build()
    drawer.setSelection(DEFAULT_DRAWER_SELECTED)
    drawer.getStickyFooter.setBackgroundResource(android.R.color.transparent)
    val header = drawer.getHeader
    val title = header.findViewById(R.id.drawer_title).asInstanceOf[TextView]
    val tf = Typefaces.get(this, "fonts/Iceland.ttf")
    if (tf != null) title.setTypeface(tf)

    if (savedInstanceState == null) displayFragment(DEFAULT_FRAGMENT)

    handler.post(() => attachService(callback))
    app.settings.registerOnSharedPreferenceChangeListener(this)

    val intent = getIntent
    if (intent != null) handleShareIntent(intent)
  }

  def startConnection(): Unit = {
    MobclickAgent.onEvent(this, "StartConnection")
    Utils.ThrowableFuture {
      if (app.isNatEnabled) bgService.use(app.profileId) else {
        val intent = VpnService.prepare(this)
        if (intent != null) startActivityForResult(intent, REQUEST_CONNECT)
        else handler.post(() => onActivityResult(REQUEST_CONNECT, Activity.RESULT_OK, null))
      }
    }
  }

  def stopConnection(): Unit = {
    MobclickAgent.onEvent(this, "StopConnection")
    bgService.use(-1)
  }

  override def onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleShareIntent(intent)
  }

  def handleShareIntent(intent: Intent) {
    val sharedStr = intent.getAction match {
      case Intent.ACTION_VIEW => intent.getData.toString
      case NfcAdapter.ACTION_NDEF_DISCOVERED =>
        val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        if (rawMsgs != null && rawMsgs.nonEmpty)
          new String(rawMsgs(0).asInstanceOf[NdefMessage].getRecords()(0).getPayload)
        else null
      case _ => null
    }
    if (TextUtils.isEmpty(sharedStr)) return
    val profiles = Parser.findAll(sharedStr).toList
    if (profiles.isEmpty) {
      Snackbar.make(findViewById(R.id.snackbar), R.string.profile_invalid_input, Snackbar.LENGTH_LONG).show()
      return
    }
    new AlertDialog.Builder(this)
      .setTitle(R.string.add_profile_dialog)
      .setPositiveButton(R.string.yes, ((_, _) =>
        profiles.foreach(app.profileManager.createOrUpdateProfile)): DialogInterface.OnClickListener)
      .setNegativeButton(R.string.no, null)
      .setMessage(profiles.mkString("\n"))
      .create()
      .show()
  }

  def onSharedPreferenceChanged(pref: SharedPreferences, key: String): Unit = key match {
    case Key.isNAT => handler.post(() => {
      detachService()
      attachService(callback)
    })
    case _ =>
  }

  private def displayFragment(fragment: ToolbarFragment) {
    currentFragment = fragment
    getFragmentManager.beginTransaction().replace(R.id.fragment_holder, fragment).commitAllowingStateLoss()
    drawer.closeDrawer()
  }

  override def onItemClick(view: View, position: Int, drawerItem: IDrawerItem[_, _ <: ViewHolder]): Boolean = {
    drawerItem.getIdentifier match {
      case DRAWER_ACCOUNT => displayFragment(myProfileFragment)
      case DRAWER_INTRO => displayFragment(introFragment)
      case DRAWER_RECOVERY =>
        app.track("GlobalConfigFragment", "reset")
        if (bgService != null) bgService.use(-1)
        val dialog = ProgressDialog.show(this, "", getString(R.string.recovering), true, false)
        val handler = new Handler {
          override def handleMessage(msg: Message): Unit = if (dialog.isShowing && !isDestroyed) dialog.dismiss()
        }
        Utils.ThrowableFuture {
          app.crashRecovery()
          app.copyAssets()
          handler.sendEmptyMessage(0)
        }
      case DRAWER_GLOBAL_SETTINGS => displayFragment(globalSettingsFragment)
      case DRAWER_AGREEMENT => displayWebFragment(R.string.agreement_title, "https://rallets.com/agreement")
      case DRAWER_FAQ => displayWebFragment(R.string.faq, "https://rallets.com/faq")
      case DRAWER_ABOUT => displayWebFragment(R.string.about, "file:///android_asset/pages/about.html")
      case DRAWER_CUSTOM_RULES => displayFragment(customRulesFragment)
      case DRAWER_LOGOUT => RUtils.showAlert(this, getString(R.string.confirm_logout_title), null, () => {
        bgService.use(-1)
        RUtils.logout(this)
        finish()
      })
    }
    true  // unexpected cases will throw exception
  }


  private def ensureLogin() {
    if (isInFront && !RUtils.loggedIn) {
      if (bgService != null) bgService.use(-1)
      startActivity(new Intent(mainActivity, classOf[LoginActivity]))
      finish()
    } else {
      introFragment.notifyDataSetChanged()
      myProfileFragment.notifyDataSetChanged()
    }
  }

  private val ralletsReceiver: BroadcastReceiver = new BroadcastReceiver() {
    override def onReceive(context: Context, intent: Intent) = {
      RUtils.processSystemNotification(MainActivity.this)
      if (!trafficEmptyShowed && Notification.one.ok && RUtils.isTrafficEmpty()) {
        MobclickAgent.onEvent(MainActivity.this, "AlertTrafficEmpty")
        if (bgService != null) bgService.use(-1)
        RUtils.showAlert(MainActivity.this, getString(R.string.new_message), getString(R.string.traffic_empty_alert), () => {
          val intent = new Intent(MainActivity.this, classOf[PaymentActivity])
          startActivity(intent)
          MobclickAgent.onEvent(MainActivity.this, "AlertTrafficEmpty-Confirmed")
        })
        trafficEmptyShowed = true
      }
      ensureLogin()
    }
  }

  protected override def onResume() {
    super.onResume()
    isInFront = true
    ShadowsocksApplication.app.ralletsNotification()
    Log.d(MainActivity.TAG, "registerReceiver(ralletsReceiver)")
    registerReceiver(ralletsReceiver, new IntentFilter(IntentID.ralletsNotification))
    app.refreshContainerHolder()
    ensureLogin()
    MobclickAgent.onResume(this)
  }

  protected override def onPause(): Unit = {
    isInFront = false
    try unregisterReceiver(ralletsReceiver) catch {
      case e: Exception => Log.d(MainActivity.TAG, "unregisterReceiver(ralletsReceiver) failed" + e.toString)
    }
    super.onPause()
    MobclickAgent.onPause(this)
  }

  override def onStart() {
    super.onStart()
    setListeningForBandwidth(true)
  }

  override def onBackPressed(): Unit =
    if (drawer.isDrawerOpen) drawer.closeDrawer() else if (currentFragment != introFragment) {
      displayFragment(introFragment)
      drawer.setSelection(DRAWER_INTRO)
    } else super.onBackPressed()

  override def onStop() {
    setListeningForBandwidth(false)
    super.onStop()
  }

  override def onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    drawer.saveInstanceState(outState)
    if (crossfader != null) crossfader.saveInstanceState(outState)
  }

  override def onDestroy() {
    super.onDestroy()
    app.settings.unregisterOnSharedPreferenceChangeListener(this)
    detachService()
    new BackupManager(this).dataChanged()
    handler.removeCallbacksAndMessages(null)
  }

  def showSnackbar(message: String): Unit = {
    if (!Option(message).getOrElse("").isEmpty) {
      val snackbar = Snackbar.make(findViewById(R.id.snackbar), message, Snackbar.LENGTH_LONG)
      snackbar.show()
    }
  }

  def displayWebFragment(titleId: Int, url: String): Unit = {
    displayFragment(new WebViewFragment(titleId, url))
  }
}

