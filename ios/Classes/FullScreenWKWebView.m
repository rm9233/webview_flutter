//
//  FullScreenWKWebView.m
//  Runner
//
//  Created by 任明 on 2020/12/11.
//  Copyright © 2020 The Chromium Authors. All rights reserved.
//

#import "FullScreenWKWebView.h"

@implementation FullScreenWKWebView

/*
// Only override drawRect: if you perform custom drawing.
// An empty implementation adversely affects performance during animation.
- (void)drawRect:(CGRect)rect {
    // Drawing code
}
*/

- (UIEdgeInsets)safeAreaInsets {
    return UIEdgeInsetsMake(0.0f, 0.0f, 0.0f, 0.0f);
}

@end
