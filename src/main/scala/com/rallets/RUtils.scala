package com.rallets

import java.security.KeyStore
import java.util.Date

import android.app._
import android.content.{Context, DialogInterface, Intent, SharedPreferences}
import android.net.Uri
import android.os.{Build, Handler}
import android.preference.PreferenceManager
import android.text.{Editable, TextWatcher}
import android.util.Log
import android.view._
import android.view.inputmethod.InputMethodManager
import android.widget._
import com.github.rallets.{BuildConfig, MainActivity, R, ShadowsocksApplication}
import com.loopj.android.http.{AsyncHttpClient, AsyncHttpResponseHandler, RequestParams}
import org.json.{JSONArray, JSONObject}
import com.github.rallets.database.{DBHelper, Profile, ProfileManager}
import android.support.v7.app.AppCompatActivity
import android.app.AlertDialog
import android.support.v4.app.NotificationCompat
import okhttp3._
import com.rallets.Model.{Notification, SystemNotification}
import cz.msebera.android.httpclient.Header

object RUtils {
  def log(msg: String): Unit = {
    Log.i("Rallets", msg)
  }

  def hideKeyboard(activity: Activity): Unit = {
    val view: View = activity.getCurrentFocus
    if (view != null) {
      val imm: InputMethodManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE).asInstanceOf[InputMethodManager]
      imm.hideSoftInputFromWindow(view.getWindowToken, 0)
    }
  }

  def addOnInputChange(input: EditText, onInputChange: (CharSequence) => Unit): Unit = {
    input.addTextChangedListener(new TextWatcher {
      override def beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int): Unit = {}

      override def onTextChanged(s: CharSequence, start: Int, before: Int, count: Int): Unit = onInputChange(s)

      override def afterTextChanged(s: Editable): Unit = {}
    })
  }

  private val client: AsyncHttpClient = new AsyncHttpClient()
  private val handler = new Handler()

  def sharedPreferences(): SharedPreferences = {
    return PreferenceManager.getDefaultSharedPreferences(ShadowsocksApplication.app)
  }

  val BASE_URL: String = "https://rallets.com/"

  private def getAbsoluteUrl(relativeUrl: String): String = {
    return BASE_URL + relativeUrl
  }

  def defaultParams: RequestParams = {
    val params: RequestParams = new RequestParams()
    params.put("DEVICE_TYPE", "ANDROID")
    params.put("VERSION", BuildConfig.VERSION_NAME)
    params.put("session_id", sharedPreferences().getString(Store.SESSION_ID, ""))
    return params
  }

  def post(url: String, params: RequestParams, responseHandler: AsyncHttpResponseHandler) {
    client.post(getAbsoluteUrl(url), params, responseHandler)
  }

  def postProcessBar(context: Context, url: String, params: RequestParams, responseHandler: AsyncHttpResponseHandler) {
    val pg = new ProgressDialog(context)
    pg.setMessage(context.getString(R.string.querying_data))
    pg.setProgressStyle(ProgressDialog.STYLE_SPINNER)
    pg.setIndeterminate(true)
    pg.show()
    client.post(getAbsoluteUrl(url), params, new AsyncHttpResponseHandler() {
      override def onFailure(statusCode: Int, headers: Array[Header], responseBody: Array[Byte], error: Throwable): Unit = {
        pg.dismiss()
        responseHandler.onFailure(statusCode, headers, responseBody, error)
      }
      override def onSuccess(statusCode: Int, headers: Array[Header], responseBody: Array[Byte]): Unit = {
        pg.dismiss()
        responseHandler.onSuccess(statusCode, headers, responseBody)
      }
    })
  }

  def syncPost(url: String, json: String): String = {
    val `type`: MediaType = MediaType.parse("application/json; charset=utf-8")
    val body: RequestBody = RequestBody.create(`type`, json)
    val request: Request = new Request.Builder().url(getAbsoluteUrl(url)).post(body).build()
    val client: OkHttpClient = new OkHttpClient()
    val response: Response = client.newCall(request).execute()
    response.body().string()
  }

  def loggedIn: Boolean = {
    sharedPreferences().getString(Store.SESSION_ID, "") != ""
  }

  def logout(activity: Activity): Unit = {
    sharedPreferences().edit().remove(Store.SESSION_ID).commit()
    if (activity != null) {
      activity.startActivity(new Intent(activity, classOf[LoginActivity]))
    }
  }

  private var configs: JSONArray = new JSONArray()

  def clearUp(): Unit = {
    configs = new JSONArray()
    ShadowsocksApplication.app.profileManager.delAllProfile
  }

  def setConfigs(ret: JSONObject, force: Boolean = false): Unit = {
    try {
      val configs: JSONArray = ret.getJSONObject("self").getJSONArray("ssconfigs")
      if (force || this.configs.toString != configs.toString) {
        this.configs = configs
        val manager = ShadowsocksApplication.app.profileManager
        var newProfileIds = Set[String]()
        // update or create new profile
        for (i <- 0 to configs.length - 1) {
          val newConfig = configs.getJSONObject(i)
          manager.createOrUpdateProfile(Profile.fromJSONObject(newConfig))
          newProfileIds += newConfig.getString("id")
        }
        // delete old unused profile
        val oldProfiles = manager.getAllProfiles.getOrElse(List.empty[Profile])
        oldProfiles.foreach(profile => {
          if (!newProfileIds.contains(profile._id)) {
            manager.delProfile(profile.id)
          }
        })
        RUtils.log("server updated, server num=" + configs.length().toString)
      } else {
        RUtils.log("server not changed, server num=" + configs.length().toString)
      }
    } catch {
      case e: Exception => RUtils.log(e.toString)
    }
  }

  def showMsg(activity: Activity, title: String, msg: String): Unit = {
    val builder = new AlertDialog.Builder(activity)
    builder.setTitle(title)
    builder.setMessage(msg)
    builder.setPositiveButton("OK", null)
    builder.create().show()
  }

  def showAlert(activity: Activity, title: String, message: String, confirmFunc: () => _): Unit = {
    val alertDialog = new AlertDialog.Builder(activity)
    alertDialog.setTitle(title)
      .setIcon(R.drawable.logo_white_128)
      .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, id: Int) {
          confirmFunc()
        }
      })
      .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int): Unit = {
          dialog.dismiss()
        }
      })
    if (message != null) {
      alertDialog.setMessage(message)
    }
    alertDialog.show()
  }

  def showEditDialog(activity: Activity, title: String, confirmFunc: (String) => _): Unit = {
    val editText = new EditText(activity)
    val alertDialog = new AlertDialog.Builder(activity)
    alertDialog.setTitle(title)
      .setIcon(R.drawable.logo_white_128)
      .setView(editText)
      .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, id: Int) {
          val text = editText.getText.toString
          if (text.trim == "") return
          confirmFunc(text)
        }
      })
      .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int): Unit = {
          dialog.dismiss()
        }
      })
    alertDialog.show()
    handler.postDelayed(() => {
      import android.content.Context
      import android.view.inputmethod.InputMethodManager
      val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE).asInstanceOf[InputMethodManager]
      imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }, 300)
  }

  def showNotification(activity: Activity, returnClass: Class[_], message: String, link: String): Unit = {
    val mBuilder = new NotificationCompat.Builder(activity)
      .setSmallIcon(R.drawable.logo_transparent_100)
      .setContentTitle(activity.getString(R.string.app_name))
      .setContentText(message)
    val resultIntent: Intent = if (!isEmpty(link) && android.util.Patterns.WEB_URL.matcher(link).matches()) {
      new Intent(Intent.ACTION_VIEW, Uri.parse(link))
    } else {
      new Intent(activity, returnClass)
    }

    // The stack builder object will contain an artificial back stack for the
    // started Activity.
    // This ensures that navigating backward from the Activity leads out of
    // your application to the Home screen.
    val stackBuilder: TaskStackBuilder = TaskStackBuilder.create(activity)
    // Adds the back stack for the Intent (but not the Intent itself)
    stackBuilder.addParentStack(returnClass)
    // Adds the Intent that starts the Activity to the top of the stack
    stackBuilder.addNextIntent(resultIntent)
    val resultPendingIntent: PendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
    mBuilder.setContentIntent(resultPendingIntent)
    val mNotificationManager: NotificationManager = activity.getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]
    mNotificationManager.notify(523, mBuilder.build())
  }

  def showToast(context: Context, strRes: Int, iconRes: Int = 0): Unit = {
    showToast(context, context.getString(strRes), iconRes)
  }

  def showToast(context: Context, str: String, iconRes: Int): Unit = {
    val myInflater = LayoutInflater.from(context)
    val view = myInflater.inflate(R.layout.layout_toast, null)
    val text = view.findViewById(R.id.toastText).asInstanceOf[TextView]
    val icon = view.findViewById(R.id.toastIcon).asInstanceOf[ImageView]
    text.setText(str)
    if (iconRes != 0) {
      icon.setImageResource(iconRes)
    } else {
      icon.setVisibility(View.GONE)
    }
    val mytoast = new Toast(context)
    mytoast.setView(view)
    mytoast.setGravity(Gravity.CENTER, 0, 0)
    mytoast.setDuration(Toast.LENGTH_SHORT)
    mytoast.show()
  }

  def getCountryCodeFlagRes(countryCode: String): Int = {
    countryCode match {
      case "AU" => R.drawable.flag_australia
      case "CA" => R.drawable.flag_canada
      case "CN" => R.drawable.flag_china
      case "HK" => R.drawable.flag_hk
      case "JP" => R.drawable.flag_japan
      case "SG" => R.drawable.flag_singapore
      case "GB" => R.drawable.flag_uk
      case "US" => R.drawable.flag_usa
      case _ => R.drawable.flag_china
    }

  }

  def hideBars(activity: AppCompatActivity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      val w: Window = activity.getWindow()
      w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }
  }

  def timeDiffTextFromNow(a: Date, context: Context): String = {
    val minutes = (a.getTime - (new Date).getTime) / 1000 / 60
    val hours = minutes / 60
    return s"${hours / 24}${context.getString(R.string.day)} ${hours % 24}${context.getString(R.string.hour)} ${minutes % 60}${context.getString(R.string.minute)}"
  }

  def disableButton(b: Button): Unit = {
    b.setEnabled(false)
  }

  def enableButton(b: Button): Unit = {
    b.setEnabled(true)
  }

  def isEmpty(s: String) = s == null || s.isEmpty

  /**
    * Compares two version strings.
    *
    * Use this instead of String.compareTo() for a non-lexicographical
    * comparison that works for version strings. e.g. "1.10".compareTo("1.6").
    *
    * @note It does not work if "1.10" is supposed to be equal to "1.10.0".
    * @param str1 a string of ordinal numbers separated by decimal points.
    * @param str2 a string of ordinal numbers separated by decimal points.
    * @return The result is a negative integer if str1 is _numerically_ less than str2.
    *         The result is a positive integer if str1 is _numerically_ greater than str2.
    *         The result is zero if the strings are _numerically_ equal.
    */
  def versionCompare(str1: String, str2: String): Int = {
    if (isEmpty(str1) && isEmpty(str2)) return 0
    if (isEmpty(str1)) return -1
    if (isEmpty(str2)) return 1
    val vals1 = str1.split("\\.")
    val vals2 = str2.split("\\.")
    var i = 0
    // set index to first non-equal ordinal or length of shortest version string
    while (i < vals1.length && i < vals2.length && vals1(i).equals(vals2(i))) {
      i += 1
    }
    // compare first non-equal ordinal number
    if (i < vals1.length && i < vals2.length) {
      val diff = Integer.valueOf(vals1(i)).compareTo(Integer.valueOf(vals2(i)))
      return Integer.signum(diff)
    }
    // the strings are equal or one string is a substring of the other
    // e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
    return Integer.signum(vals1.length - vals2.length)
  }

  var preVersion = BuildConfig.VERSION_NAME
  var preMessage: String = _
  def processSystemNotification(activity: Activity): Unit = {
    val s = Notification.one.systemNotification
    if (RUtils.versionCompare(s.version, preVersion) > 0) {
      preVersion = s.version
      showNotification(activity: Activity, classOf[MainActivity], activity.getString(R.string.new_version_avaiable, s.version), s.download_link)
    }
    if (s.show && !isEmpty(s.message) && s.message != preMessage) {
      preMessage = s.message
      showAlert(activity, activity.getString(R.string.new_message), s.message, () => {
        if (!isEmpty(s.link) && android.util.Patterns.WEB_URL.matcher(s.link).matches() && activity.isInstanceOf[MainActivity]) {
          activity.asInstanceOf[MainActivity].displayWebFragment(R.string.message_link, s.link)
        }
      })
    }
  }

  def isTrafficEmpty(): Boolean = {
    return Notification.one.self.traffic.premium <= 0
  }
}

class RUtils {
}
