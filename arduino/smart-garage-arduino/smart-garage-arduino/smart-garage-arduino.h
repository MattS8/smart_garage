#pragma once

#include <Arduino.h>
#include "FirebaseESP8266.h"
#include <ESP8266WiFi.h>

typedef struct GarageAction {
	String timestamp;
	String type;
	String uid;
} GarageAction;

typedef struct AutoCloseOptions {
	bool enabled;						// Determines whether to run "auto close" functionality
	long timeout;						// Time before garage door closes
	bool warningEnabled;				// Determines whether to warn users before closing door
	long warningTimeout;				// Time before warning is sent to users
} AutoCloseOptions;

typedef struct AutoClose {
	long closeTime;
	long warningTime;
	bool hasSentWarning;
} AutoClose;

static const String FIREBASE_HOST = "smart-garage-door-3d0f1.firebaseio.com";
static const String FIREBASE_AUTH = "7qlG7H7EERalI9cqSAJkM4EfZrw2jI3lwenO6dBV";


/* -------------------- Arduino Constants -------------------- */
static const int PIN_STATUS_CLOSE = 13;		// D7
static const int PIN_STATUS_OPEN = 14;		// D5
static const int PIN_GARAGE_CONTROL = 12;	// D6

static const String STATUS_OPEN = "OPEN";
static const String STATUS_CLOSED = "CLOSED";
static const String STATUS_OPENING = "OPENING";
static const String STATUS_CLOSING = "CLOSING";

static const String ERR_ACTION = "Received action was neither \"OPEN\" nor \"CLOSE\".";

/* -------------------- Firebase Constants -------------------- */
static const String ACTION_NONE = "NONE";
static const String ACTION_OPEN = "OPEN";
static const String ACTION_CLOSE = "CLOSE";
static const String ACTION_STOP_AUTO_CLOSE = "STOP_AUTO_CLOSE";

static const String PATH_BASE = "/garages/home_garage/";
static const String PATH_DEBUG = "debug/controller/";
static const String PATH_DEFAULTS = "controller/defaults/";
static const String PATH_AUTO_CLOSE_OPTIONS = "auto_close_options";


static const String NONE = "_none_";


/* -------------------- Forward Declarations -------------------- */

// Debug
String debugMessage;
//void sendDebugMessage();
//FirebaseObject getDebugObject();

void streamCallback(StreamData data);

