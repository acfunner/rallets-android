package com.rallets.Model

import java.text.DateFormat
import java.util

import com.rallets.{RUtils, Store}
import com.google.gson.{Gson, GsonBuilder}
import org.json.JSONObject

object Notification {
  private var  _one: Notification = new Notification
  val gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").create()

  def one = _one
  def one_=(result: String) {
    _one = gson.fromJson(result, classOf[Notification])
  }
}

class Notification {
  var ok: Boolean = false
  var self: User = new User
  var goods: util.ArrayList[Good] = new util.ArrayList[Good]()
  var systemNotification = new SystemNotification
}
