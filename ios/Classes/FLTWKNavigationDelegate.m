// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "FLTWKNavigationDelegate.h"

@implementation FLTWKNavigationDelegate {
  FlutterMethodChannel* _methodChannel;
}

- (instancetype)initWithChannel:(FlutterMethodChannel*)channel {
  self = [super init];
  if (self) {
    _methodChannel = channel;
  }
  return self;
}

#pragma mark - WKNavigationDelegate conformance

- (void)webView:(WKWebView*)webView didStartProvisionalNavigation:(WKNavigation*)navigation {
  [_methodChannel invokeMethod:@"onPageStarted" arguments:@{@"url" : webView.URL.absoluteString}];
}

//URLEncode
-(NSString*)encodeString:(NSString*)unencodedString{
    NSString *encodedString = (NSString *)
    CFBridgingRelease(CFURLCreateStringByAddingPercentEscapes(kCFAllocatorDefault,
                    (CFStringRef)unencodedString,
                     NULL,
                     (CFStringRef)@"!*'();:@&=+$,/?%#[]",
                     kCFStringEncodingUTF8));
    
    return encodedString;
}


//URLDEcode
-(NSString *)decodeString:(NSString*)encodedString
{
    NSString *decodedString = (__bridge_transfer NSString *)CFURLCreateStringByReplacingPercentEscapesUsingEncoding(NULL,
                              (__bridge CFStringRef)encodedString,CFSTR(""),
                               CFStringConvertNSStringEncodingToEncoding(NSUTF8StringEncoding));
    return decodedString;
}


- (void)webView:(WKWebView *)webView decidePolicyForNavigationAction:(WKNavigationAction *)navigationAction decisionHandler:(void (^)(WKNavigationActionPolicy))decisionHandler{
    
      NSURLRequest *request        = navigationAction.request;
      NSString     *scheme         = [request.URL scheme];
      // decode for all URL to avoid url contains some special character so that it wasn't load.
      NSString     *absoluteString = [navigationAction.request.URL.absoluteString stringByRemovingPercentEncoding];
      NSLog(@"Current URL is %@",absoluteString);
      
      static NSString *endPayRedirectURL = nil;
      static NSString *CompanyFirstDomainByWeChatRegister = @"superkid.top";
      
      if ([absoluteString hasPrefix:@"https://wx.tenpay.com/cgi-bin/mmpayweb-bin/checkmweb"] && ![absoluteString hasSuffix:[NSString stringWithFormat:@"redirect_url=app.%@://",CompanyFirstDomainByWeChatRegister]]) {
          decisionHandler(WKNavigationActionPolicyCancel);
         
          NSString *redirectUrl = nil;
          if ([absoluteString containsString:@"redirect_url="]) {
              NSRange redirectRange = [absoluteString rangeOfString:@"redirect_url"];
              endPayRedirectURL =  [absoluteString substringFromIndex:redirectRange.location+redirectRange.length+1];
              redirectUrl = [[absoluteString substringToIndex:redirectRange.location] stringByAppendingString:[NSString stringWithFormat:@"redirect_url=app.%@://",CompanyFirstDomainByWeChatRegister]];
          }else {
              redirectUrl = [absoluteString stringByAppendingString:[NSString stringWithFormat:@"&redirect_url=app.%@://",CompanyFirstDomainByWeChatRegister]];
          }
          
          NSMutableURLRequest *newRequest = [NSMutableURLRequest requestWithURL:[NSURL URLWithString:redirectUrl] cachePolicy:NSURLRequestUseProtocolCachePolicy timeoutInterval:10];
          newRequest.allHTTPHeaderFields = request.allHTTPHeaderFields;
          newRequest.URL = [NSURL URLWithString:redirectUrl];
          [webView loadRequest:newRequest];
          return;
      }else if(([scheme isEqualToString:@"https"] || [scheme isEqualToString:@"http"]) && ![absoluteString hasPrefix:@"https://wx.tenpay.com/cgi-bin/mmpayweb-bin/checkmweb"]){
          self.localUrl = absoluteString;
         
      }
      
      // Judge is whether to jump to other app.
      if (![scheme isEqualToString:@"https"] && ![scheme isEqualToString:@"http"] && ![scheme isEqualToString:@"js"] && ![scheme isEqualToString:@"file"]) {
          decisionHandler(WKNavigationActionPolicyCancel);
          if ([scheme isEqualToString:@"weixin"]) {
              // The var endPayRedirectURL was our saved origin url's redirect address. We need to load it when we return from wechat client.
              if (endPayRedirectURL) {
                  [webView loadRequest:[NSURLRequest requestWithURL:[NSURL URLWithString:endPayRedirectURL] cachePolicy:NSURLRequestUseProtocolCachePolicy timeoutInterval:10]];
              }
          }else if ([scheme isEqualToString:[NSString stringWithFormat:@"app.%@",CompanyFirstDomainByWeChatRegister]]) {
            
//              if(self.localUrl != nil){
//                  NSLog(@"%@",self.localUrl);
//                  NSMutableURLRequest *newRequest = [NSMutableURLRequest requestWithURL:[NSURL URLWithString:self.localUrl] cachePolicy:NSURLRequestUseProtocolCachePolicy timeoutInterval:10];
//                  newRequest.allHTTPHeaderFields = request.allHTTPHeaderFields;
//                  newRequest.URL = [NSURL URLWithString:self.localUrl];
//                  [webView loadRequest:newRequest];
//              }
             
          }
          
          BOOL canOpen = [[UIApplication sharedApplication] canOpenURL:request.URL];
          if (canOpen) {
              [[UIApplication sharedApplication] openURL:request.URL];
            
          }
          return;
      }
      
    if (!self.hasDartNavigationDelegate) {
      decisionHandler(WKNavigationActionPolicyAllow);
      return;
    }
    
    NSDictionary* arguments = @{
      @"url" : navigationAction.request.URL.absoluteString,
      @"isForMainFrame" : @(navigationAction.targetFrame.isMainFrame)
    };
    [_methodChannel invokeMethod:@"navigationRequest"
                       arguments:arguments
                          result:^(id _Nullable result) {
                            if ([result isKindOfClass:[FlutterError class]]) {
                              NSLog(@"navigationRequest has unexpectedly completed with an error, "
                                    @"allowing navigation.");
                              decisionHandler(WKNavigationActionPolicyAllow);
                              return;
                            }
                            if (result == FlutterMethodNotImplemented) {
                              NSLog(@"navigationRequest was unexepectedly not implemented: %@, "
                                    @"allowing navigation.",
                                    result);
                              decisionHandler(WKNavigationActionPolicyAllow);
                              return;
                            }
                            if (![result isKindOfClass:[NSNumber class]]) {
                              NSLog(@"navigationRequest unexpectedly returned a non boolean value: "
                                    @"%@, allowing navigation.",
                                    result);
                              decisionHandler(WKNavigationActionPolicyAllow);
                              return;
                            }
                            NSNumber* typedResult = result;
                            decisionHandler([typedResult boolValue] ? WKNavigationActionPolicyAllow
                                                                    : WKNavigationActionPolicyCancel);
                          }];
}



- (void)webView:(WKWebView*)webView didFinishNavigation:(WKNavigation*)navigation {
  [_methodChannel invokeMethod:@"onPageFinished" arguments:@{@"url" : webView.URL.absoluteString}];
}
@end
