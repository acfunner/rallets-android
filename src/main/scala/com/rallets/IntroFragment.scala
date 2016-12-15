package com.rallets

import java.net.{HttpURLConnection, URL}

import android.app.{Activity, AlertDialog, Dialog, DialogFragment}
import android.content.Intent
import com.github.rallets.{MainActivity, R, ToolbarFragment}
import android.os.{AsyncTask, Bundle, Handler}
import android.support.design.widget.FloatingActionButton
import android.support.v4.content.ContextCompat
import android.view.{LayoutInflater, View, ViewGroup}
import com.github.rallets.utils.CloseUtils.autoDisconnect
import android.widget._
import com.github.jorgecastilloprz.FABProgressCircle
import com.rallets.Model.Notification
import com.rallets.TitledRecyclerView.Item
import com.github.rallets.ShadowsocksApplication.app
import com.github.rallets.utils.{State, TrafficMonitor, Utils}
import com.loopj.android.http.AsyncHttpResponseHandler
import com.umeng.analytics.MobclickAgent
import cz.msebera.android.httpclient.Header
import org.json.JSONObject

class IntroFragment extends ToolbarFragment {

  private val handler = new Handler()
  private var fab: FloatingActionButton = _
  private var fabProgressCircle: FABProgressCircle = _
  private var serverSection: TitledRecyclerView = _
  private var discountSection: TitledRecyclerView = _
  private var serverSectionItems: Array[Item] = _
  private var statusText: TextView = _
  private var serverIcon: ImageView = _
  private var serverName: TextView = _
  private var testCount: Int = _

  private var mainActivity: MainActivity = _
  private var initialized = false

  def tr(i: Int): String = {
    return getResources.getString(i)
  }

  def showServerList(): Unit = {
    val intent = new Intent(getActivity, classOf[ServerListActivity])
    startActivityForResult(intent, RequestCode.SELECT_SERVER)
    MobclickAgent.onEvent(getActivity, "EnterServerList")
  }

  def showPaymentList(): Unit = {
    val intent = new Intent(getActivity, classOf[PaymentActivity])
    startActivity(intent)
    MobclickAgent.onEvent(getActivity, "EnterPaymentList")
  }

  def showSharePage(): Unit = {
    val intent = new Intent(getActivity, classOf[ShareActivity])
    startActivity(intent)
    MobclickAgent.onEvent(getActivity, "EnterSharePage")
  }

  def showCouponDialog(): Unit = {
    RUtils.showEditDialog(getActivity, getString(R.string.enter_coupon), (text: String) => {
      val params = RUtils.defaultParams
      params.put("code", text)
      RUtils.postProcessBar(getActivity, "coupons/use", params, new AsyncHttpResponseHandler() {
        override def onFailure(statusCode: Int, headers: Array[Header], responseBody: Array[Byte], error: Throwable): Unit = {
          RUtils.showToast(getActivity, R.string.retry_later, R.drawable.ic_clear_white_48dp)
        }
        override def onSuccess(statusCode: Int, headers: Array[Header], responseBody: Array[Byte]): Unit = {
          val ret = new JSONObject(new String(responseBody))
          if (ret.getBoolean("ok")) {
            MobclickAgent.onEvent(getActivity, "UseCouponSuccess")
            RUtils.showToast(getActivity, R.string.use_coupon_success, R.drawable.ic_done_white_48dp)
            app.ralletsNotification()
          } else {
            MobclickAgent.onEvent(getActivity, "UseCouponFail")
            if (ret.optString("message", "") != "") {
              RUtils.showToast(getActivity, ret.optString("message", ""), R.drawable.ic_clear_white_48dp)
            } else {
              RUtils.showToast(getActivity, R.string.use_coupon_failed, R.drawable.ic_clear_white_48dp)
            }
          }
        }
      })
    })
    MobclickAgent.onEvent(getActivity, "EnterCouponDialog")
  }

  def setCurrentServerName(s: String): Unit = {
    serverSectionItems(0).subText = s
    serverSection.notifyDataSetChanged
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    resultCode match {
      case Activity.RESULT_OK => {
        handler.post(() => mainActivity.startConnection())
      }
      case _ => ()
    }
  }

  private lazy val darkBlueTint100 = ContextCompat.getColorStateList(getActivity, R.color.dark_blue_100)
  private lazy val greenTint500 = ContextCompat.getColorStateList(getActivity, R.color.green_5FF)
  private def hideCircle() = try fabProgressCircle.hide() catch {
    case _: NullPointerException =>
  }

  var state: Int = _
  override def onStateChanged(s: Int, profileName: String = null, m: String = null) {
    if (!initialized) return
    val currProfile = app.currentProfile.orNull
    s match {
      case State.CONNECTING =>
        fab.setImageResource(R.drawable.ic_flash_off)
        fabProgressCircle.show()
        statusText.setText(R.string.connecting)
      case State.CONNECTED =>
        if (state == State.CONNECTING) fabProgressCircle.beginFinalAnimation()
        else fabProgressCircle.postDelayed(() => hideCircle, 1000)
        fab.setImageResource(R.drawable.ic_flash_on)
        if (currProfile != null) {
          statusText.setText(if (app.isNatEnabled) R.string.nat_connected else R.string.vpn_connected)
          serverName.setText(currProfile.name)
          setCurrentServerName(currProfile.name)
          serverIcon.setImageResource(RUtils.getCountryCodeFlagRes(currProfile.countryCode))
        }
        serverSection.notifyDataSetChanged
      case State.STOPPING =>
        fab.setImageResource(R.drawable.ic_flash_off)
        if (state == State.CONNECTED) fabProgressCircle.show()  // ignore for stopped
        statusText.setText(R.string.stopping)
        serverIcon.setImageResource(R.drawable.flag_china)
      case _ =>
        fab.setImageResource(R.drawable.ic_flash_off)
        fabProgressCircle.postDelayed(() => hideCircle, 1000)
        statusText.setText(R.string.not_connected)
        serverName.setText(tr(R.string.select_line))
        serverIcon.setImageResource(R.drawable.flag_china)
        setCurrentServerName(tr(R.string.not_connected))
    }
    state = s
    if (state == State.CONNECTED) fab.setBackgroundTintList(greenTint500) else {
      fab.setBackgroundTintList(darkBlueTint100)
      testCount += 1  // suppress previous test messages
    }
    fab.setEnabled(false)
    if (state == State.CONNECTED || state == State.STOPPED)
      handler.postDelayed(() => fab.setEnabled(state == State.CONNECTED || state == State.STOPPED), 1000)
  }

  override def onTrafficUpdated(profileId: Int, txRate: Long, rxRate: Long, txTotal: Long, rxTotal: Long) {
    if (!initialized) return
    serverSectionItems(2).subText =
      "↑" + TrafficMonitor.formatTraffic(txRate) + "/s" + " " +
      "↓" + TrafficMonitor.formatTraffic(rxRate) + "/s"
    serverSection.notifyDataSetChanged
  }

  override def onTrafficPersisted(profileId: Int): Unit = {
    if (!initialized) return
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.layout_intro, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle) {
    super.onViewCreated(view, savedInstanceState)
    toolbar.setTitle(R.string.rallets)
    mainActivity = getActivity.asInstanceOf[MainActivity]
    serverSection = view.findViewById(R.id.serverSection).asInstanceOf[TitledRecyclerView]
    discountSection = view.findViewById(R.id.discountSection).asInstanceOf[TitledRecyclerView]
    statusText = view.findViewById(R.id.statusText).asInstanceOf[TextView]
    serverName = view.findViewById(R.id.serverName).asInstanceOf[TextView]
    serverIcon = view.findViewById(R.id.serverIcon).asInstanceOf[ImageView]
    fab = view.findViewById(R.id.fab).asInstanceOf[FloatingActionButton]
    fabProgressCircle = view.findViewById(R.id.fabProgressCircle).asInstanceOf[FABProgressCircle]
    fab.setOnClickListener(_ => {
      if (state == State.CONNECTED) mainActivity.stopConnection()
      else {
        if (app.currentProfile.orNull == null) {
          val firstProfile = app.profileManager.getFirstProfile.orNull
          if (firstProfile == null) return
          app.switchProfile(firstProfile.id)
        }
        mainActivity.startConnection()
      }
    })
    serverSectionItems = Array(
      new Item(null, tr(R.string.current_line), tr(R.string.not_connected), true),
      new Item(null, tr(R.string.remaining), tr(R.string.querying), true),
      new Item(null, tr(R.string.current_speed), "", true)
    )
    serverSection.setDataSet(serverSectionItems)
    serverSection.setOnItemClickListener(new TitledRecyclerView.OnItemClickListener {
      override def onItemClick(item: TitledRecyclerView.Item, position: Int): Unit = {
        position match {
          case 0 => showServerList()
          case 1 => showPaymentList()
          case _ => ()
        }
      }
    })
    discountSection.setDataSet(Array(
      new Item(null, tr(R.string.bill), "", true),
      new Item(null, tr(R.string.coupon), tr(R.string.coupon_hint), true),
      new Item(null, tr(R.string.invite), tr(R.string.invite_hint), true)
    ))
    discountSection.setOnItemClickListener(new TitledRecyclerView.OnItemClickListener {
      override def onItemClick(item: TitledRecyclerView.Item, position: Int): Unit = {
        position match {
          case 0 => showPaymentList()
          case 1 => showCouponDialog()
//          case 2 => showSharePage()
          case _ => RUtils.showToast(getActivity, R.string.coming_soon)
        }
      }
    })
    view.findViewById(R.id.serverWrap).setOnClickListener(_ => if (state == State.CONNECTED && app.isVpnEnabled) {
      testCount += 1
      statusText.setText(R.string.connection_test_testing)
      val id = testCount  // it would change by other code
      Utils.ThrowableFuture {
        // Based on: https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/connectivity/NetworkMonitor.java#640
        MobclickAgent.onEvent(getActivity, "TestConnectionLatency")
        autoDisconnect(new URL("https", "www.google.com", "/generate_204").openConnection()
          .asInstanceOf[HttpURLConnection]) { conn =>
          conn.setConnectTimeout(5 * 1000)
          conn.setReadTimeout(5 * 1000)
          conn.setInstanceFollowRedirects(false)
          conn.setUseCaches(false)
          if (testCount == id) {
            var result: String = null
            var success = true
            try {
              val start = System.currentTimeMillis
              conn.getInputStream
              val elapsed = System.currentTimeMillis - start
              val code = conn.getResponseCode
              if (code == 204 || code == 200 && conn.getContentLength == 0)
                result = getString(R.string.connection_test_available, elapsed: java.lang.Long)
              else throw new Exception(getString(R.string.connection_test_error_status_code, code: Integer))
            } catch {
              case e: Exception =>
                success = false
                result = getString(R.string.connection_test_error, e.getMessage)
            }
            if (testCount == id) handler.post(() => if (success) statusText.setText(result) else {
              statusText.setText(R.string.connection_test_fail)
              serverIcon.setImageResource(R.drawable.flag_china)
              mainActivity.showSnackbar(result)
            })
          }
        }
      }
    })
    val currProfile = app.currentProfile.getOrElse(null)
    val currProfileName = if (currProfile != null) currProfile.name else tr(R.string.not_connected)
    onStateChanged(mainActivity.state, currProfileName)
    initialized = true
    notifyDataSetChanged()
  }

  def notifyDataSetChanged(): Unit = {
    if (!isVisible) return
    val self = Notification.one.self
    serverSectionItems(1).subText = s"${self.traffic.premium / 1048576} MB"
    serverSection.notifyDataSetChanged
  }

}
