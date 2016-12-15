package com.rallets

import android.content._
import android.os.{Bundle, Handler}
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.widget.{Button, EditText, TextView}
import com.github.rallets.R
import com.loopj.android.http.{AsyncHttpResponseHandler, RequestParams}
import com.umeng.analytics.MobclickAgent
import org.json.JSONObject
import cz.msebera.android.httpclient.Header

object FindPasswordActivity {
  val TAG = "FindPasswordActivity"
}

class FindPasswordActivity extends AppCompatActivity {
  lazy val emailOrMobileET = findViewById(R.id.emailOrMobile).asInstanceOf[EditText]
  lazy val codeET = findViewById(R.id.code).asInstanceOf[EditText]
  lazy val sendVerificationCodeButton = findViewById(R.id.sendVerificationCodeButton).asInstanceOf[Button]
  lazy val findPasswordButton = findViewById(R.id.findPasswordButton).asInstanceOf[Button]
  lazy val loginMessageTV = findViewById(R.id.loginMessageTV).asInstanceOf[TextView]
  lazy val passwordET = findViewById(R.id.editTextPassword).asInstanceOf[EditText]
  lazy val passwordConfirmationET = findViewById(R.id.passwordConfirmation).asInstanceOf[EditText]
  lazy val activity = this
  var isInFront = false

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.layout_find_password)
    val toolbar = findViewById(R.id.toolbar).asInstanceOf[Toolbar]
    setSupportActionBar(toolbar)
    getSupportActionBar.setDisplayHomeAsUpEnabled(true)
    getSupportActionBar.setTitle(R.string.find_password)
    sendVerificationCodeButton.setOnClickListener(_ => sendFindPasswordCode())
    findPasswordButton.setOnClickListener(_ => setPassword())
    RUtils.disableButton(findPasswordButton)
  }

  override def onSupportNavigateUp(): Boolean = {
    onBackPressed()
    return true
  }

  override def onResume(): Unit = {
    super.onResume()
    MobclickAgent.onResume(this)
  }

  override def onPause(): Unit = {
    super.onPause()
    MobclickAgent.onPause(this)
  }

  def setPassword(): Unit = {
    RUtils.hideKeyboard(activity)
    val password = passwordET.getText.toString
    val passwordConfirmation = passwordConfirmationET.getText.toString
    var ok = true
    if (Regex.PASSWORD.findFirstIn(password) == None) {
      ok = false
      passwordET.setError(activity.getString(R.string.incorrect_password))
    } else {
      passwordET.setError(null)
    }
    if (passwordConfirmation != passwordET.getText.toString) {
      ok = false
      passwordConfirmationET.setError(activity.getString(R.string.dismatch_passwords))
    } else {
      passwordConfirmationET.setError(null)
    }
    if (!ok) return
    val params = RUtils.defaultParams
    params.put("email_or_mobile", emailOrMobileET.getText.toString)
    params.put("password", password)
    params.put("code", codeET.getText.toString)

    findPasswordButton.setEnabled(false)
    RUtils.postProcessBar(this, "users/set_password", params, new AsyncHttpResponseHandler() {
      override def onFailure(statusCode: Int, headers: Array[Header], responseBody: Array[Byte], error: Throwable): Unit = {
        findPasswordButton.setEnabled(true)
        activity.showSnackbar(getString(R.string.unknown_error, getString(R.string.find_password)))
      }
      override def onSuccess(statusCode: Int, headers: Array[Header], responseBody: Array[Byte]): Unit = {
        findPasswordButton.setEnabled(true)
        val ret = new JSONObject(new String(responseBody))
        activity.showSnackbar(ret.getString("message"))
        if (ret.getBoolean("ok")) {
          RUtils.sharedPreferences().edit().putString(Store.PASSWORD, password).commit()
          new Handler().postDelayed(new Runnable {
            override def run(): Unit = {
              startActivity(new Intent(activity, classOf[LoginActivity]))
            }
          }, 2000)
        }
      }
    })
  }

  def sendFindPasswordCode(): Unit = {
    Log.d(FindPasswordActivity.TAG, "sendFindPasswordCode")
    val emailOrMobile = emailOrMobileET.getText.toString
    RUtils.hideKeyboard(this)

    val settings = RUtils.sharedPreferences()
    val editor = settings.edit()
    editor.putString(Store.EMAIL_OR_MOBILE, emailOrMobile).commit()
    val params: RequestParams = RUtils.defaultParams
    params.put("email_or_mobile", emailOrMobile)
    sendVerificationCodeButton.setEnabled(false)
    RUtils.postProcessBar(this, "users/send_find_password_code", params, new AsyncHttpResponseHandler() {
      override def onFailure(statusCode: Int, headers: Array[Header], responseBody: Array[Byte], error: Throwable): Unit = {
        sendVerificationCodeButton.setEnabled(true)
        activity.showSnackbar(getString(R.string.unknown_error, getString(R.string.find_password)))
      }
      override def onSuccess(statusCode: Int, headers: Array[Header], responseBody: Array[Byte]): Unit = {
        val ret = new JSONObject(new String(responseBody))
        activity.showSnackbar(ret.getString("message"))
        if (ret.getBoolean("ok")) {
          sendVerificationCodeButton.setText(R.string.already_sent)
          RUtils.disableButton(sendVerificationCodeButton)
          RUtils.enableButton(findPasswordButton)
        } else {
          sendVerificationCodeButton.setEnabled(true)
        }
      }
    })
  }

  def showSnackbar(message: String): Unit = {
    if (!Option(message).getOrElse("").isEmpty) {
      def snackbar: Snackbar = Snackbar.make(findViewById(R.id.login), message, Snackbar.LENGTH_LONG)
      snackbar.show()
    }
  }

  def showMessage(ret: JSONObject): Unit = {
    if (ret.has("message")) {
      showSnackbar(ret.getString("message"))
    }
  }
}
