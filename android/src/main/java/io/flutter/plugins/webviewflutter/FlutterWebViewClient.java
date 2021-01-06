// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.webviewflutter;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.webkit.WebViewClientCompat;

import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodChannel;

import java.util.HashMap;
import java.util.Map;

// We need to use WebViewClientCompat to get
// shouldOverrideUrlLoading(WebView view, WebResourceRequest request)
// invoked by the webview on older Android devices, without it pages that use iframes will
// be broken when a navigationDelegate is set on Android version earlier than N.
class FlutterWebViewClient {
    private static final String TAG = "FlutterWebViewClient";
    private final MethodChannel methodChannel;
    private Context context;
    private boolean hasNavigationDelegate;
    private String mReffer;

//  FlutterWebViewClient(MethodChannel methodChannel) {
//
//  }

    public FlutterWebViewClient(MethodChannel methodChannel, Context context) {
        this.methodChannel = methodChannel;
        this.context = context;
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {

      String url = request.getUrl().toString();
      if (url == null) {
        return false;
      }
//H5调起微信app支付添加 Referer
      if (url.contains("wx.tenpay.com/cgi-bin")) {
        Map<String, String> extraHeaders = new HashMap<String, String>();
        extraHeaders.put("Referer", mReffer);
        view.loadUrl(url, extraHeaders);
        return true;
      }
      //H5调起微信app支付方法二（可使用）
      if (url.startsWith("weixin://wap/pay?")) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setData(Uri.parse(url));
        try {
          this.context.startActivity(intent);
        }catch (Exception e){
          Log.e("FUCK?",e.getMessage());
          Toast.makeText(context,"您没有安装微信，请安装后重试",Toast.LENGTH_SHORT).show();
        }

        return true;
      }

        if (!hasNavigationDelegate) {
            return false;
        }
        notifyOnNavigationRequest(
                request.getUrl().toString(), request.getRequestHeaders(), view, request.isForMainFrame());
        // We must make a synchronous decision here whether to allow the navigation or not,
        // if the Dart code has set a navigation delegate we want that delegate to decide whether
        // to navigate or not, and as we cannot get a response from the Dart delegate synchronously we
        // return true here to block the navigation, if the Dart delegate decides to allow the
        // navigation the plugin will later make an addition loadUrl call for this url.
        //
        // Since we cannot call loadUrl for a subframe, we currently only allow the delegate to stop
        // navigations that target the main frame, if the request is not for the main frame
        // we just return false to allow the navigation.
        //
        // For more details see: https://github.com/flutter/flutter/issues/25329#issuecomment-464863209
        return request.isForMainFrame();
    }

    private boolean shouldOverrideUrlLoading(WebView view, String url) {

      if (url == null) {
        return false;
      }
//H5调起微信app支付添加 Referer
      if (url.contains("wx.tenpay.com/cgi-bin")) {
        Map<String, String> extraHeaders = new HashMap<String, String>();
        extraHeaders.put("Referer", mReffer);
        view.loadUrl(url, extraHeaders);
        return true;
      }
//H5调起微信app支付方法二（可使用）
      if (url.startsWith("weixin://wap/pay?")) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setData(Uri.parse(url));
        try {
          context.startActivity(intent);
        }catch (Exception e){
          Toast.makeText(context,"您没有安装微信，请安装后重试",Toast.LENGTH_SHORT).show();
        }

        return true;
      }
        if (!hasNavigationDelegate) {
            return false;
        }
        // This version of shouldOverrideUrlLoading is only invoked by the webview on devices with
        // webview versions  earlier than 67(it is also invoked when hasNavigationDelegate is false).
        // On these devices we cannot tell whether the navigation is targeted to the main frame or not.
        // We proceed assuming that the navigation is targeted to the main frame. If the page had any
        // frames they will be loaded in the main frame instead.
        Log.w(
                TAG,
                "Using a navigationDelegate with an old webview implementation, pages with frames or iframes will not work");
        notifyOnNavigationRequest(url, null, view, true);
        return true;
    }

    private void onPageStarted(WebView view, String url) {
        Map<String, Object> args = new HashMap<>();
        args.put("url", url);
        methodChannel.invokeMethod("onPageStarted", args);
    }

    private void onPageFinished(WebView view, String url) {
        Map<String, Object> args = new HashMap<>();
        args.put("url", url);
        methodChannel.invokeMethod("onPageFinished", args);
    }

    private void notifyOnNavigationRequest(
            String url, Map<String, String> headers, WebView webview, boolean isMainFrame) {
        HashMap<String, Object> args = new HashMap<>();
        args.put("url", url);
        args.put("isForMainFrame", isMainFrame);
        if (isMainFrame) {
            methodChannel.invokeMethod(
                    "navigationRequest", args, new OnNavigationRequestResult(url, headers, webview));
        } else {
            methodChannel.invokeMethod("navigationRequest", args);
        }
    }

    // This method attempts to avoid using WebViewClientCompat due to bug
    // https://bugs.chromium.org/p/chromium/issues/detail?id=925887. Also, see
    // https://github.com/flutter/flutter/issues/29446.
    WebViewClient createWebViewClient(boolean hasNavigationDelegate) {
        this.hasNavigationDelegate = hasNavigationDelegate;

        if (!hasNavigationDelegate || android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return internalCreateWebViewClient();
        }

        return internalCreateWebViewClientCompat();
    }

    private WebViewClient internalCreateWebViewClient() {
        return new WebViewClient() {

            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                ///获取请求uir
                String url = request.getUrl().toString();
                ///获取RequestHeader中的所有 key value
                Map<String, String> lRequestHeaders = request.getRequestHeaders();
                if (lRequestHeaders.containsKey("Referer")) {
                    mReffer = lRequestHeaders.get("Referer");
                }
                return super.shouldInterceptRequest(view, request);
            }


            @TargetApi(Build.VERSION_CODES.N)
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return FlutterWebViewClient.this.shouldOverrideUrlLoading(view, request);
            }

          @Override
          public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return FlutterWebViewClient.this.shouldOverrideUrlLoading(view, url);
          }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                FlutterWebViewClient.this.onPageStarted(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                FlutterWebViewClient.this.onPageFinished(view, url);
            }

            @Override
            public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
                // Deliberately empty. Occasionally the webview will mark events as having failed to be
                // handled even though they were handled. We don't want to propagate those as they're not
                // truly lost.
            }
        };
    }

    private WebViewClientCompat internalCreateWebViewClientCompat() {
        return new WebViewClientCompat() {

            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                ///获取请求uir
                String url = request.getUrl().toString();
                ///获取RequestHeader中的所有 key value
                Map<String, String> lRequestHeaders = request.getRequestHeaders();
                if (lRequestHeaders.containsKey("Referer")) {
                    mReffer = lRequestHeaders.get("Referer");
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return FlutterWebViewClient.this.shouldOverrideUrlLoading(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return FlutterWebViewClient.this.shouldOverrideUrlLoading(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                FlutterWebViewClient.this.onPageStarted(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                FlutterWebViewClient.this.onPageFinished(view, url);
            }

            @Override
            public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
                // Deliberately empty. Occasionally the webview will mark events as having failed to be
                // handled even though they were handled. We don't want to propagate those as they're not
                // truly lost.
            }
        };
    }



  private static class OnNavigationRequestResult implements MethodChannel.Result {
        private final String url;
        private final Map<String, String> headers;
        private final WebView webView;

        private OnNavigationRequestResult(String url, Map<String, String> headers, WebView webView) {
            this.url = url;
            this.headers = headers;
            this.webView = webView;
        }

        @Override
        public void success(Object shouldLoad) {
            Boolean typedShouldLoad = (Boolean) shouldLoad;
            if (typedShouldLoad) {
                loadUrl();
            }
        }

        @Override
        public void error(String errorCode, String s1, Object o) {
            throw new IllegalStateException("navigationRequest calls must succeed");
        }

        @Override
        public void notImplemented() {
            throw new IllegalStateException(
                    "navigationRequest must be implemented by the webview method channel");
        }

        private void loadUrl() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                webView.loadUrl(url, headers);
            } else {
                webView.loadUrl(url);
            }
        }
    }
}
