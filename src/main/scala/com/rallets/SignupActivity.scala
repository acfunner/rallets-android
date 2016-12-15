package com.rallets

import java.util

import android.content._
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.support.v7.widget.Toolbar
import android.widget._
import com.github.rallets.{MainActivity, R, ShadowsocksApplication}
import com.loopj.android.http.{AsyncHttpResponseHandler, RequestParams}
import com.umeng.analytics.MobclickAgent
import org.json.JSONObject
import cz.msebera.android.httpclient.Header

object SignupActivity {
  val TAG = "SignupActivity"
}

class SignupActivity extends AppCompatActivity {
  lazy val signupButton = findViewById(R.id.signupButton).asInstanceOf[Button]
  lazy val emailOrMobileET = findViewById(R.id.emailOrMobile).asInstanceOf[EditText]
  lazy val passwordET = findViewById(R.id.editTextPassword).asInstanceOf[EditText]
  lazy val passwordConfirmationET = findViewById(R.id.passwordConfirmation).asInstanceOf[EditText]
  lazy val referralCodeET = findViewById(R.id.referralCode).asInstanceOf[EditText]
  lazy val signupMessageTV = findViewById(R.id.loginMessageTV).asInstanceOf[TextView]
  lazy val areaSpinner = findViewById(R.id.area).asInstanceOf[Spinner]


  val signupActivity = this
  val defaultSelectedAreaPos = 0
  var areaValues: Array[String] = _
  var area: String = _

  private class AreaSpinnerListener extends AdapterView.OnItemSelectedListener {
    override def onNothingSelected(parent: AdapterView[_]): Unit = {}
    override def onItemSelected(parent: AdapterView[_], view: View, position: Int, id: Long): Unit = {
      area = areaValues(position)
    }
  }

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.layout_signup)
    val toolbar = findViewById(R.id.toolbar).asInstanceOf[Toolbar]
    setSupportActionBar(toolbar)
    getSupportActionBar.setDisplayHomeAsUpEnabled(true)
    getSupportActionBar.setTitle(R.string.signup)
    val settings = RUtils.sharedPreferences()
    signupButton.setOnClickListener(_ => signup())
    areaSpinner.setOnItemSelectedListener(new AreaSpinnerListener())
    areaSpinner.setSelection(defaultSelectedAreaPos)
    areaValues = getResources.getStringArray(R.array.area_values)
    area = areaValues(defaultSelectedAreaPos)
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

  def signup(): Unit = {
    val emailOrMobile = emailOrMobileET.getText.toString
    val password = passwordET.getText.toString
    val passwordConfirmation = passwordConfirmationET.getText.toString
    val referralCode = referralCodeET.getText.toString
    RUtils.hideKeyboard(this)

    val sp = RUtils.sharedPreferences()
    val editor = sp.edit()
    editor.putString(Store.EMAIL_OR_MOBILE, emailOrMobile)
    editor.putString(Store.PASSWORD, password)
    editor.putString(Store.REFERRAL_CODE, referralCode)
    editor.commit()

    var ok = true
    if (Regex.PHONE.findFirstIn(emailOrMobile) == None && Regex.EMAIL.findFirstIn(emailOrMobile) == None) {
      ok = false
      emailOrMobileET.setError(signupActivity.getString(R.string.incorrect_email_or_mobile))
    } else {
      emailOrMobileET.setError(null)
    }

    if (Regex.PASSWORD.findFirstIn(password) == None) {
      ok = false
      passwordET.setError(signupActivity.getString(R.string.incorrect_password))
    } else {
      passwordET.setError(null)
    }

    if (passwordConfirmation != passwordET.getText.toString) {
      ok = false
      passwordConfirmationET.setError(signupActivity.getString(R.string.dismatch_passwords))
    } else {
      passwordConfirmationET.setError(null)
    }
    if (!ok) return
    val params: RequestParams = RUtils.defaultParams
    val data = new util.HashMap[String, Any]()
    val settings = new util.HashMap[String, String]()
    settings.put("area", area)
    data.put("settings", settings)
    data.put("email_or_mobile", emailOrMobile)
    data.put("password", password)
    data.put("password_confirmation", password)
    data.put("referrer_short_id", referralCode)
    params.put("data", data)
    signupButton.setEnabled(false)
    RUtils.postProcessBar(this, "users/register", params, new AsyncHttpResponseHandler() {
      override def onFailure(statusCode: Int, headers: Array[Header], responseBody: Array[Byte], error: Throwable): Unit = {
        signupButton.setEnabled(true)
        signupActivity.showSnackbar(getString(R.string.unknown_error, getString(R.string.signup)))
      }
      override def onSuccess(statusCode: Int, headers: Array[Header], responseBody: Array[Byte]): Unit = {
        signupButton.setEnabled(true)
        val ret = new JSONObject(new String(responseBody))
        if (ret.getBoolean("ok")) {
          editor.putString(Store.SESSION_ID, ret.getString("session_id")).apply()
          startActivity(new Intent(signupActivity, classOf[MainActivity]))
          ShadowsocksApplication.app.ralletsNotification()
          finish()
        } else {
          signupActivity.showSnackbar(ret.getString("message"))
        }
      }
    })
  }

  def showSnackbar(message: String): Unit = {
    if (!Option(message).getOrElse("").isEmpty) {
      def snackbar: Snackbar = Snackbar.make(findViewById(R.id.signup), message, Snackbar.LENGTH_LONG)
      snackbar.show()
    }
  }

  def showMessage(ret: JSONObject): Unit = {
    if (ret.has("message")) {
      showSnackbar(ret.getString("message"))
    }
  }
}
