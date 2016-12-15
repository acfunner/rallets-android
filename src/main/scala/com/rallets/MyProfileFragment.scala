package com.rallets

import android.app.{Activity, Fragment}
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatDelegate
import android.support.v7.preference.{EditTextPreference, ListPreference, Preference}
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.Toast
import be.mygod.preference.PreferenceFragment
import com.github.rallets.{R, ToolbarFragment}
import com.github.rallets.utils.Key
import com.loopj.android.http.AsyncHttpResponseHandler
import com.rallets.Model.Notification
import cz.msebera.android.httpclient.Header
import org.json.JSONObject

class MyProfileFragment extends ToolbarFragment {

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.layout_my_profile, container, false)

  var content: MyProfilePreferenceFragment = _
  override def onViewCreated(view: View, savedInstanceState: Bundle) {
    super.onViewCreated(view, savedInstanceState)
    toolbar.setTitle(R.string.account)

    notifyDataSetChanged()
    val fm = getChildFragmentManager
    content = new MyProfilePreferenceFragment()
    fm.beginTransaction().replace(R.id.content, content).commit()
    fm.executePendingTransactions()
  }

  def notifyDataSetChanged(): Unit = {
    if (content != null) {
      content.updateUI()
    }
  }

  override def onDetach() {
    super.onDetach()

    try {
      val childFragmentManager = classOf[Fragment].getDeclaredField("mChildFragmentManager")
      childFragmentManager.setAccessible(true)
      childFragmentManager.set(this, null)
    } catch {
      case _: Exception =>  // ignore
    }
  }
}


class MyProfilePreferenceFragment extends PreferenceFragment {
  var email: Preference = _
  var mobile: Preference = _
  var maxLogins: Preference = _
  var nickname: EditTextPreference = _
  var area: ListPreference = _
  var balance: Preference = _
  var premiumTraffic: Preference = _
  var invitationCode: Preference = _

  override def onCreatePreferences(bundle: Bundle, key: String) {
    addPreferencesFromResource(R.xml.my_profile_preference)
    email = findPreference(Key.email)
    mobile = findPreference(Key.mobile)
    maxLogins = findPreference(Key.maxLogins)
    balance = findPreference(Key.balance)
    premiumTraffic = findPreference(Key.premiumTraffic)
    invitationCode = findPreference(Key.invitationCode)

    val clickAndPay = new Preference.OnPreferenceClickListener {
      override def onPreferenceClick(preference: Preference): Boolean = {
        val intent = new Intent(getActivity, classOf[PaymentActivity])
        startActivity(intent)
        return true
      }
    }

    premiumTraffic.setOnPreferenceClickListener(clickAndPay)
    balance.setOnPreferenceClickListener(clickAndPay)

    area = findPreference(Key.area).asInstanceOf[ListPreference]
    area.setOnPreferenceChangeListener((_, value) => {
      val params = RUtils.defaultParams
      params.add("area", value.toString)
      RUtils.postProcessBar(getActivity, "users/save_area", params, new AsyncHttpResponseHandler() {
        override def onFailure(statusCode: Int, headers: Array[Header], responseBody: Array[Byte], error: Throwable) = {
          Toast.makeText(getActivity, getString(R.string.save_failed), Toast.LENGTH_LONG).show()
        }
        override def onSuccess(statusCode: Int, headers: Array[Header], responseBody: Array[Byte]) = {
          val ret = new JSONObject(new String(responseBody))
          if (ret.getBoolean("ok")) {
            Notification.one.self.settings.area = value.toString
            Toast.makeText(getActivity(), getString(R.string.save_success), Toast.LENGTH_LONG).show()
            setArea
          } else {
            if (ret.optString("message", "") != "") {
              Toast.makeText(getActivity, ret.optString("message", ""), Toast.LENGTH_LONG).show()
            } else {
              Toast.makeText(getActivity, getString(R.string.save_failed), Toast.LENGTH_LONG).show()
            }
          }
        }
      })
      true
    })

    nickname = findPreference(Key.nickname).asInstanceOf[EditTextPreference]
    nickname.setOnPreferenceChangeListener((_, value) => {
      val nicknameStr = value.toString
      val params = RUtils.defaultParams
      params.add("nickname", nicknameStr)
      RUtils.postProcessBar(getActivity, "users/set_nickname", params, new AsyncHttpResponseHandler() {
        override def onFailure(statusCode: Int, headers: Array[Header], responseBody: Array[Byte], error: Throwable): Unit = {
          Toast.makeText(getActivity(), getString(R.string.save_failed), Toast.LENGTH_LONG).show()
        }
        override def onSuccess(statusCode: Int, headers: Array[Header], responseBody: Array[Byte]): Unit = {
          val ret = new JSONObject(new String(responseBody))
          Toast.makeText(getActivity(), getString(R.string.save_success), Toast.LENGTH_LONG).show()
          if (ret.getBoolean("ok")) {
            val self = Notification.one.self
            self.nickname = nicknameStr
            nickname.setSummary(nicknameStr)
          }
        }
      })
      true
    })
    updateUI()
  }

  def setArea: Unit ={
    area.setSummary(
      Notification.one.self.settings.area match {
        case "EC" => getString(R.string.EC)
        case "SC" => getString(R.string.SC)
        case "NC" => getString(R.string.NC)
        case "CC" => getString(R.string.CC)
        case "WC" => getString(R.string.WC)
        case _ => getString(R.string.not_set)
      }
    )
  }

  def updateUI(): Unit = {
    val activity = getActivity
    if (activity != null) {
      val self = Notification.one.self
      email.setSummary(self.email)
      mobile.setSummary(self.mobile)
      maxLogins.setSummary(self.max_logins_text)
      nickname.setSummary(self.nickname)
      nickname.setText(self.nickname)
      area.setValue(self.settings.area)
      premiumTraffic.setSummary(s"${self.traffic.premium / 1048576} MB")
      invitationCode.setSummary(self.short_id)
      balance.setSummary((self.balance / 100.0) + " " + getString(R.string.yuan))
      setArea
    }
  }
}
