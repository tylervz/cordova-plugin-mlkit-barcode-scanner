#import "CameraViewController.h"
#import <AVFoundation/AVFoundation.h>
#import <Cordova/CDV.h>
#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

@class UIViewController;

@interface CDViOSScanner : CDVPlugin {
  NSString *_callback;
  Boolean _scannerOpen;
  AVAudioPlayer *_player;
  Boolean _beepOnSuccess;
  Boolean _vibrateOnSuccess;
}

@property(nonatomic, retain) CameraViewController *cameraViewController;

- (void)startScan:(CDVInvokedUrlCommand *)command;

@end
