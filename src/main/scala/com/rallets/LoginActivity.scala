package com.rallets

import android.content._
import android.graphics.{Color, PorterDuff}
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.{Button, EditText, TextView}
import com.github.rallets.{MainActivity, R, ShadowsocksApplication}
import com.loopj.android.http.{AsyncHttpResponseHandler, RequestParams}
import com.umeng.analytics.MobclickAgent
import org.json.JSONObject
import cz.msebera.android.httpclient.Header

object LoginActivity {
  val TAG = "LoginActivity"
}

class LoginActivity extends AppCompatActivity {
  lazy val loginButton = findViewById(R.id.loginButton).asInstanceOf[Button]
  lazy val signupButton = findViewById(R.id.signupButton).asInstanceOf[Button]
  lazy val findPasswordButton = findViewById(R.id.findPasswordButton).asInstanceOf[Button]
  lazy val emailOrMobileET = findViewById(R.id.emailOrMobile).asInstanceOf[EditText]
  lazy val passwordET = findViewById(R.id.password).asInstanceOf[EditText]
  lazy val loginMessageTV = findViewById(R.id.loginMessageTV).asInstanceOf[TextView]
  lazy val loginActivity = this
  var isInFront = false
  var settings = RUtils.sharedPreferences()

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.layout_login)
    if (settings.getBoolean(Store.FIRST_OPEN, true)) startActivity(new Intent(this, classOf[SignupActivity]))
    loginButton.setOnClickListener(_ => login())
    signupButton.setOnClickListener(_ => startActivity(new Intent(this, classOf[SignupActivity])))
    findPasswordButton.setOnClickListener(_ => startActivity(new Intent(this, classOf[FindPasswordActivity])))
    emailOrMobileET.setText(settings.getString(Store.EMAIL_OR_MOBILE, ""))
    passwordET.setText(settings.getString(Store.PASSWORD, ""))
    settings.edit().putBoolean(Store.FIRST_OPEN, false).commit()
  }

  override def onResume(): Unit = {
    super.onResume()
    MobclickAgent.onResume(this)
  }

  override def onPause(): Unit = {
    super.onPause()
    MobclickAgent.onPause(this)
  }

  def login(): Unit = {
    Log.d(LoginActivity.TAG, "Login")
    val emailOrMobile = emailOrMobileET.getText.toString
    val password = passwordET.getText.toString
    RUtils.hideKeyboard(this)

    val editor = settings.edit()
    editor.putString(Store.EMAIL_OR_MOBILE, emailOrMobile)
    editor.putString(Store.PASSWORD, password)
    editor.commit()
    val params: RequestParams = RUtils.defaultParams
    params.put("email_or_mobile", emailOrMobile)
    params.put("login_password", password)
    loginButton.setEnabled(false)
    RUtils.postProcessBar(this, "login", params, new AsyncHttpResponseHandler() {
      override def onFailure(statusCode: Int, headers: Array[Header], responseBody: Array[Byte], error: Throwable): Unit = {
        loginButton.setEnabled(true)
        loginActivity.showSnackbar(getString(R.string.unknown_error, getString(R.string.login)))
      }
      override def onSuccess(statusCode: Int, headers: Array[Header], responseBody: Array[Byte]): Unit = {
        loginButton.setEnabled(true)
        val ret = new JSONObject(new String(responseBody))
        if (ret.getBoolean("ok")) {
          editor.putString(Store.SESSION_ID, ret.getString("session_id")).apply()
          startActivity(new Intent(loginActivity, classOf[MainActivity]))
          ShadowsocksApplication.app.ralletsNotification()
          finish()
        } else {
          loginActivity.showSnackbar(ret.getString("message"))
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
