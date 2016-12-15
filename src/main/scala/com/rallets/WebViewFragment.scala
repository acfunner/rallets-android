package com.rallets

import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import android.webkit.WebView
import com.github.rallets.{R, ToolbarFragment}

class WebViewFragment(titleId: Int, url: String) extends ToolbarFragment {
  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.layout_webview, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle) {
    super.onViewCreated(view, savedInstanceState)
    val title = getString(titleId)
    toolbar.setTitle(title)
    val web = view.findViewById(R.id.web_view).asInstanceOf[WebView]
    val settings = web.getSettings()
    settings.setJavaScriptEnabled(true)
    web.setWebViewClient(new RalletsWebClient(getActivity, title))
    web.loadUrl(url)
  }
}
