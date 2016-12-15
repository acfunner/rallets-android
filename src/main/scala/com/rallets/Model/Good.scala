package com.rallets.Model

class GoodData {
  var days: Int = _
  var `type`: Int = _
  var level: Int = _
  var basic_traffic: String = _
  var premium_traffic: String = _
}

class Good {
  var id: String = _
  var price: Int = _
  var original_price: Int = _
  var title: String = _
  var description: String = _
  var data = new GoodData
}
