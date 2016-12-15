package com.rallets.Model

import java.util.Date

import com.github.rallets.R


class Settings {
  var area: String = _
}

class Traffic {
  var basic: Long = 0
  var premium: Long = 0
}

class User {
  var email: String = _
  var nickname: String = _
  var mobile: String = _
  var max_logins_text: String = _
  var settings = new Settings
  var balance: Int = _
  var end_time = new Date
  var traffic = new Traffic
  var short_id: String = _
}
