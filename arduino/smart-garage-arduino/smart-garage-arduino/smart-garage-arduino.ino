/*
 Name:		smart_garage_arduino.ino
 Created:	12/16/2019 4:26:41 PM
 Author:	matt
*/

// the setup function runs once when you press reset or power the board
#include "smart-garage-arduino.h"

String garageStatus = STATUS_OPEN;				// Global variable for current state of garage door
GarageAction action;							// Global variable for received actions		
bool ignoreListener = true;						// Initial result from firebase listener should be ignored	
AutoCloseOptions autoCloseOptions;				// Options related to the "auto close" functionality
AutoClose autoClose;							// Variables pertaining to the "auto close" functionality


void setup() {
	Serial.begin(115200);

	// Set pin modes
	pinMode(LED_BUILTIN, OUTPUT);
	pinMode(PIN_STATUS_OPEN, INPUT_PULLUP);
	pinMode(PIN_STATUS_CLOSE, INPUT_PULLUP);
	pinMode(PIN_GARAGE_CONTROL, OUTPUT);

	Serial.println("");
	garageStatus = digitalRead(PIN_STATUS_CLOSE) == LOW ? STATUS_CLOSED : STATUS_OPEN;
	Serial.print("initial status is ");
	Serial.println(garageStatus);

	ignoreListener = true;

	// Initialize "auto close" options
	autoCloseOptions.enabled = false;
	autoCloseOptions.timeout = 0;
	autoCloseOptions.warningTimeout = 0;

	// Initialize "auto close" variables
	autoClose.closeTime = 0;
	autoClose.warningTime = 0;
	autoClose.hasSentWarning = false;

	// Initialize global action variable
	action.type = ACTION_CLOSE;					// This is needed to determine transient state on first button press
	action.timestamp = NONE;
	action.uid = NONE;

	Serial.print("Connecting to WiFi");
	connectToWifi();
	Serial.println("Connected!");

	Serial.println("Connecting to Firebase...");
	connectToFirebase();
	Serial.println("Conntected!");
}

void connectToWifi() {
	WiFi.begin("HawkswayBase", "F4d29095dc");

	while (WiFi.status() != WL_CONNECTED) {
    	delay(500);
    	Serial.print(".");
  	}

	Serial.println();
	Serial.print("Connected with IP: ");
	Serial.println(WiFi.localIP());
	Serial.println();
}

void connectToFirebase() {
	Firebase.begin(FIREBASE_HOST, FIREBASE_AUTH);

	delay(500);

	//FirebaseObject initialAction = FirebaseObject(actionToJson(ACTION_CLOSE).c_str());
	//sendToFirebase(PATH_ACTION, initialAction.getJsonVariant("/"));
	//delay(350);

	if (Firebase.failed()) {
		Serial.println(F("Failed to set initial action..."));
		ESP.reset();
	}

	Firebase.stream(PATH_BASE + "controller");
}

void sendToFirebase(const String& path, const JsonVariant& obj) {
	const static int maxRetries = 4;
	const static int retry_delay = 500;

	for (int i = 0; i < maxRetries; i++)
	{
		Firebase.set(path, obj);
		delay(retry_delay);

		if (Firebase.failed())
		{
			Serial.print("Failed to send... (");
			Serial.print(i + 1);
			Serial.print("/");
			Serial.print(maxRetries);
			Serial.println(")");
			delay(retry_delay);
		}
		else
		{
			return;
		}
	}
}

String actionToJson(String actionType) {

	return "{" + JSON_TYPE + actionType + "\"}";
}

String statusToJson(String statusType) {
	return "{" + JSON_TYPE + statusType + "\"}";
}

void toggleGarageDoor() {
	Serial.print("Sending garage pulse... ");
	digitalWrite(PIN_GARAGE_CONTROL, HIGH);
	delay(500);
	digitalWrite(PIN_GARAGE_CONTROL, LOW);
	Serial.println("Done!");
}

// First, check status pins to see if there is a change in state for the garage door.
//	If there is a change, update the status on Firebase and any local state variables.
// Then, check to see if we have received a new action from Firebase.
//	If we have, determine whether or not to send a toggle signal to the garage door
//	based on the current state.
void loop() {
	delay(300);

	// ---- Handle Firebase Crash ----
	if (Firebase.failed()) {
		debugMessage = "streaming error - " + Firebase.error();
		sendDebugMessage();

		Serial.println("streaming error");
		Serial.println(Firebase.error());

		ignoreListener = true;

		connectToFirebase();
	}

	// ---- Update Status ----
	String newStatus = checkStatus();
	if (newStatus != garageStatus)
	{
		Serial.print("New status detected! (");
		Serial.print(newStatus);
		Serial.println(")");
		// Update type to reflect an action not initiated via the app
		if (newStatus == STATUS_CLOSED)
			action.type = ACTION_CLOSE;
		else if (newStatus == STATUS_OPEN)
			action.type = ACTION_OPEN;

		// Update "auto close" functionality
		if (newStatus == STATUS_OPEN || newStatus == STATUS_OPENING)
		{
			autoClose.hasSentWarning = false;
			autoClose.closeTime = millis() + autoCloseOptions.timeout;
			autoClose.warningTime = millis() + autoCloseOptions.warningTimeout;
		}
		else
		{
			autoClose.closeTime = 0;
			autoClose.warningTime = 0;
		}

		garageStatus = newStatus;

		// Update Firebase status
		FirebaseObject statusObject = FirebaseObject(statusToJson(garageStatus).c_str());
		sendToFirebase(PATH_BASE + "status", statusObject.getJsonVariant("/"));
	}

	// ---- Check "Auto Close" ----
	if (autoCloseOptions.enabled)
	{
		if (!autoClose.warningTime != 0 && autoClose.warningTime >= millis())
		{
			autoClose.warningTime = 0;
			// todo - send warning notification

			debugMessage = "Sending warning message regarding auto close.";
			sendDebugMessage();
		}

		if (autoClose.closeTime != 0 && autoClose.closeTime >= millis()) 
		{
			autoClose.closeTime = 0;
			if (garageStatus != STATUS_CLOSED && garageStatus != STATUS_CLOSING)
			{
				toggleGarageDoor();

				debugMessage = "Sending pulse to CLOSE garage door (auto close).";
				sendDebugMessage();
			}
		}
	}


	// ---- Handle Firebase Listener ----
	if (!Firebase.available())
		return;
	FirebaseObject event = Firebase.readEvent();
	if (event.getString("type") != "put")
		return;

	// Received a valid event 
	String temp = event.getString(FIREBASE_ACTION_TYPE);
	if (temp != "")
	{
		action.type = temp;
		if (ignoreListener) 
		{
			ignoreListener = false;
			debugMessage = "Ignoring first event from firebase.";
			sendDebugMessage();
			return;
		}

		Serial.print("Received Action: ");
		Serial.println(action.type);
		if (action.type == ACTION_CLOSE)
		{
			if (garageStatus != STATUS_CLOSED && garageStatus != STATUS_CLOSING)
			{	
				toggleGarageDoor();

				debugMessage = "Sending pulse to CLOSE garage door.";
				sendDebugMessage();
			}
			else 
			{
				debugMessage = "Received CLOSE command, but garage door is aleardy closed.";
				sendDebugMessage();
			}
		}
		else if (action.type == ACTION_OPEN)
		{
			if (garageStatus != STATUS_OPEN && garageStatus != STATUS_OPENING)
			{
				toggleGarageDoor();

				debugMessage = "Sending pulse to OPEN garage door.";
				sendDebugMessage();
			}
			else 
			{
				debugMessage = "Received OPEN command, but garage door is aleardy open.";
				sendDebugMessage();
			}
		}
		else if (action.type == ACTION_STOP_AUTO_CLOSE)
		{
			autoClose.closeTime = 0;
			autoClose.warningTime = 0;

			debugMessage = "Stopping the garage from auto closing on user's behest.";
			sendDebugMessage();
		}
		else
		{
			debugMessage = ERR_ACTION;
			sendDebugMessage();
			Serial.println(ERR_ACTION);
		}
	}
	else
	{
		Serial.println("Event wasn't an action.. assuming option event");
		Serial.println(event.getString(FIREBASE_OPTIONS));

		AutoCloseOptions tempOptions;
		tempOptions.enabled = event.getBool(FIREBASE_OPTIONS + "/enabled");
		if (!event.success())
			tempOptions.enabled = autoCloseOptions.enabled;
		tempOptions.timeout = event.getFloat(FIREBASE_OPTIONS + "/timeout");
		if (!event.success())
			tempOptions.timeout = autoCloseOptions.timeout;
		tempOptions.warningTimeout = event.getFloat(FIREBASE_OPTIONS + "/warningTimeout");
		if (!event.success())
			tempOptions.warningTimeout = autoCloseOptions.warningTimeout;
	}

	//if (hasReceivedFirebaseAction()) 
	//{
	//	if (bIgnoreListener) {
	//		bIgnoreListener = false;
	//		Serial.println("Ignoring first event from firebase");
	//	}

	//	Serial.print("Received Action: ");
	//	Serial.println(action.type);
	//	if (action.type == ACTION_CLOSE)
	//	{
	//		if (garageStatus != STATUS_CLOSED && garageStatus != STATUS_CLOSING)
	//		{	
	//			debugMessage = "Sending pulse to CLOSE garage door.";
	//			sendDebugMessage();

	//			toggleGarageDoor();
	//		}
	//		else 
	//		{
	//			debugMessage = "Received CLOSE command, but garage door is aleardy closed.";
	//			sendDebugMessage();
	//		}
	//	}
	//	else if (action.type == ACTION_OPEN)
	//	{
	//		if (garageStatus != STATUS_OPEN && garageStatus != STATUS_OPENING)
	//		{
	//			debugMessage = "Sending pulse to OPEN garage door.";
	//			sendDebugMessage();

	//			toggleGarageDoor();
	//		}
	//		else 
	//		{
	//			debugMessage = "Received OPEN command, but garage door is aleardy open.";
	//			sendDebugMessage();
	//		}
	//	}
	//	else
	//	{
	//		debugMessage = ERR_ACTION;
	//		sendDebugMessage();
	//		Serial.println(ERR_ACTION);
	//	}
	//}
}

String checkStatus() {
	String retStr = "";
	int openPin = digitalRead(PIN_STATUS_OPEN);
	int closePin = digitalRead(PIN_STATUS_CLOSE);

	if (closePin == LOW)
	{
		return STATUS_CLOSED;
	}
	else if (openPin == LOW)
	{
		return STATUS_OPEN;
	}
	else {
		// TRANSIENT STATES
		if (garageStatus == STATUS_CLOSED)
		{
			return STATUS_OPENING;
		}
		else if (garageStatus == STATUS_OPEN)
		{
			return STATUS_CLOSING;
		} else 
		{
			return garageStatus;
		}
	}
}

bool hasReceivedFirebaseAction() {
	if (!Firebase.available())
		return false;

	FirebaseObject event = Firebase.readEvent();
	if (event.getString("type") == "put") 
	{
		Serial.print("Received FirebaseObject: ");
		Serial.print(event.getString("data"));
		Serial.print(" | ");
		Serial.println(event.getString("data/type"));

		action.type = event.getString();
		if (action.type == "") {
			//Serial.println("\ttrying data...");
			action.type = event.getString("data");
		}
		if (action.type == "") {
			//Serial.println("\ttrying data/type...");
			action.type = event.getString("data/type");
		}
		return true;
	}
	else
	{
		return false;
	}
}


// Debug
FirebaseObject getDebugObject() {
	return FirebaseObject(
		String("{\"meesage\":\"" + debugMessage + "\"}").c_str()
		);
}

void sendDebugMessage() {
	sendToFirebase(PATH_DEBUG + millis(), getDebugObject().getJsonVariant("/"));
}