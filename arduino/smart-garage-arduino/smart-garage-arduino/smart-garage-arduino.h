#pragma once

#include <Arduino.h>
#include <FirebaseArduino.h>
#include <ESP8266WiFi.h>

typedef struct GarageAction {
	String timestamp;
	String type;
	String uid;
} GarageAction;

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

/* -------------------- Firebase Constants -------------------- */
static const String ACTION_NONE = "NONE";
static const String ACTION_OPEN = "OPEN";
static const String ACTION_CLOSE = "CLOSE";

static const String PATH_ACTION = "/garages/home_garage/action";
static const String PATH_STATUS = "/garages/home_garage/status";

static const String JSON_ACTION_1 = "{\"timestamp\": \"";
static const String JSON_ACTION_2 = "\", \"type\": \"";
static const String JSON_ACTION_3 = "\", \"uid\": \"";
static const String JSON_ACTION_4 = "\"}";

static const String JSON_TYPE = "\"type\": \"";

static const String JSON_STATUS_1 = "{\"type\": \"";

static const String NONE = "_none_";

