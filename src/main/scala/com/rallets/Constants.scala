package com.rallets

object Regex {
  val PHONE = """^(?=\d{11}$)^1(?:3\d|4[57]|5[^4\D]|7[^249\D]|8\d)\d{8}$""".r
  val EMAIL = """(\w+)@([\w\.]+)""".r
  val PASSWORD = """((?=.*\d)(?=.*[a-zA-Z]).{6,20})""".r
}

object Store {
  val SESSION_ID = "SESSION_ID"
  val EMAIL_OR_MOBILE = "EMAIL_OR_MOBILE"
  val PASSWORD = "PASSWORD"
  val REFERRAL_CODE = "REFERRAL_CODE"
  val NOTIFICATION = "NOTIFICATION"
  val FIRST_OPEN = "FIRST_OPEN"
}

object IntentID {
  val ralletsNotification = "com.rallets.notification"
}

object RequestCode {
  val SELECT_SERVER = 10
}