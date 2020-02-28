/*
 Name:		smart_garage_arduino.ino
 Created:	12/16/2019 4:26:41 PM
 Author:	matt
*/

// the setup function runs once when you press reset or power the board
#include "smart-garage-arduino.h"
//#define LOCAL_DEBUG true						// Uncomment to restrict debug statements to local Serial Port

String garageStatus = STATUS_OPEN;				// Global variable for current state of garage door
GarageAction action;							// Global variable for received actions		
AutoCloseOptions autoCloseOptions;				// Options related to the "auto close" functionality
AutoClose autoClose;							// Variables pertaining to the "auto close" functionality

bool isInitialAutoCloseOptions = true;			// Used to determine whether to update defaults node

FirebaseData firebaseData;						// FirebaseESP8266 data object
FirebaseData firebaseSendData;					// FirebaseESP8266 data object used to send updates

void setup() 
{
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

	// Initialize "auto close" options
	autoCloseOptions.enabled = false;
	autoCloseOptions.timeout = 0;
	autoCloseOptions.warningEnabled = false;
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

void connectToWifi() 
{
	WiFi.begin("HawkswayBase", "F4d29095dc");

	while (WiFi.status() != WL_CONNECTED) 
	{
    	delay(500);
    	Serial.print(".");
  	}

	Serial.println();
	Serial.print("Connected with IP: ");
	Serial.println(WiFi.localIP());
	Serial.println();
}

void connectToFirebase() 
{
	Firebase.begin(FIREBASE_HOST, FIREBASE_AUTH);
	Firebase.reconnectWiFi(true);
	Firebase.setMaxRetry(firebaseData, 4);

	sendDebugMessage("Controller connected to Firebase.");

	delay(500);

	// Fetch Auto Close Options
	FirebaseJson jsonObject;
	if (Firebase.get(firebaseData, PATH_BASE + PATH_DEFAULTS + PATH_AUTO_CLOSE_OPTIONS))
	{
#ifdef LOCAL_DEBUG
		Serial.println("Fetching Initial Auto Close Options");
#endif // LOCAL_DEBUG

		jsonObject.setJsonData(firebaseData.jsonString());
		handleOptionsChange(jsonObject);
		isInitialAutoCloseOptions = false;
	}
	else
	{
		String debugMessage = "Unable to fetch Initial Auto Close Options: ";
		debugMessage += firebaseData.errorReason();
		sendDebugMessage(debugMessage);
		isInitialAutoCloseOptions = false;
	}

	// Begin Streaming
	Serial.print("Streaming to: ");
	Serial.println(String(PATH_BASE + "controller"));

	Firebase.setStreamCallback(firebaseData, streamCallback);

	if (!Firebase.beginStream(firebaseData, PATH_BASE + "controller"))
	{
		Serial.println(firebaseData.errorReason());
		ESP.reset();
	}
}

// Callback Logic:
//  Ensure data type is JSON -> return error if not
//  If this is the first event -> Handle first time event
//	Else If this is an action event -> Handle new action event
//	Else If this is an options event -> Hanle new options
void streamCallback(StreamData data)
{
	String eventPath = data.dataPath();
#ifdef LOCAL_DEBUG
	Serial.println("Stream Data1 available...");
	Serial.println("STREAM PATH: " + data.streamPath());
	Serial.println("EVENT PATH: " + data.dataPath());
	Serial.println("DATA TYPE: " + data.dataType());
	Serial.println("EVENT TYPE: " + data.eventType());
#endif // LOCAL_DEBUG

	// Handle null data type (action acknowledgements)
	if (data.dataType() == "null")
	{
#ifdef LOCAL_DEBUG
		Serial.println("Ignoring acknowledgement...");
#endif // LOCAL_DEBUG
		return;
	}

	String debugMessage;
	// Handle invalid type error
	if (data.dataType() != "json") 
	{
		debugMessage = "Recieved data that was not of type JSON. (type = ";
		debugMessage += data.dataType();
		debugMessage += ")";
		sendDebugMessage(debugMessage);
		return;
	}

	FirebaseJson jsonObject;
	jsonObject.setJsonData(data.jsonString());
	FirebaseJsonData jsonData;

	// Handle new action
	if (jsonObject.get(jsonData, "a_timestamp"))
	{
		jsonObject.get(jsonData, "type");
		sendDebugMessage("Handling new action");
		handleNewAction(jsonData.stringValue);
	}
	// Handle new Auto Close Options
	else if (jsonObject.get(jsonData, "o_timestamp"))
	{
		sendDebugMessage("Handling Auto Close Options change");
		handleOptionsChange(jsonObject);
	}
	// Ignore defaults responses
	else if (eventPath.indexOf("defaults") > 0)
	{
#ifdef LOCAL_DEBUG
		Serial.println("Igoring defaults response...");
#endif // LOCAL_DEBUG
	}
	// Error: Unknown response
	else 
	{
		debugMessage = "Recieved unknown response: ";
		debugMessage += data.jsonString();
		sendDebugMessage(debugMessage);
	}
}

void sendToFirebase(const String path, FirebaseJson& obj) 
{
#ifdef LOCAL_DEBUG
	Serial.print("Sending object to: ");
	Serial.println(path);
#endif // LOCAL_DEBUG

	if (!Firebase.setJSON(firebaseSendData, path, obj)) 
	{
		Serial.print("sendToFirebase: FAILED - ");
		Serial.println(firebaseSendData.errorReason());
	}
}

void toggleGarageDoor() 
{
#ifdef LOCAL_DEBUG
	Serial.print("Sending garage pulse... ");
#endif // LOCAL_DEBUG
	digitalWrite(PIN_GARAGE_CONTROL, HIGH);
	delay(500);
	digitalWrite(PIN_GARAGE_CONTROL, LOW);
#ifdef LOCAL_DEBUG
	Serial.println("Done!");
#endif // LOCAL_DEBUG
}

// Controller Loop:
// 1) Check for Firebase failure -> restart
// 2) Check for new status -> Update current status accordingly
// 3) If Enabled, Check auto close functionality ->
//		If warning enabled and is time -> Send warning
//		If auto close enabled and is time -> Auto close door

// Firebase listener logic has been moved to new Callback function:

void loop() 
{
	delay(100);

	// ---- Handle Firebase Crash ----
	if (!Firebase.readStream(firebaseData))
		handleFirebaseFailure();

	// ---- Update Status ----
	String newStatus = getStatus();
	if (newStatus != garageStatus)
		updateStatus(newStatus);

	// ---- Check "Auto Close" ----
	if (autoCloseOptions.enabled)
	{
		if (autoCloseOptions.warningEnabled && autoClose.warningTime != 0 && millis() >= autoClose.warningTime)
			sendAutoCloseWarning();

		if (autoClose.closeTime != 0 && millis() >= autoClose.closeTime)
			autoCloseDoor();
	}
}

void handleFirebaseFailure()
{
	String errStr = "Streaming Error: ";
	errStr += firebaseData.errorReason();
	Serial.println(errStr);

	sendDebugMessage(errStr);

	ESP.restart();
}

String getStatus() 
{
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

void updateStatus(String newStatus)
{
#ifdef LOCAL_DEBUG
	Serial.print("New status detected! (");
	Serial.print(newStatus);
	Serial.println(")");
#endif // LOCAL_DEBUG

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
	FirebaseJson newObj;
	newObj.add("type", garageStatus);
	sendToFirebase(PATH_BASE + "status", newObj);
}


void sendAutoCloseWarning()
{
	autoClose.warningTime = 0;

	FirebaseJson warningObject;
	warningObject.add("timeout", (double) autoCloseOptions.warningTimeout);
	warningObject.add("timestamp", (double) millis());

	sendToFirebase(PATH_BASE + "notifications/auto_close_warning", warningObject);

	sendDebugMessage("Sending warning message regarding auto close.");
}

void autoCloseDoor()
{
	autoClose.closeTime = 0;

	if (garageStatus != STATUS_CLOSED && garageStatus != STATUS_CLOSING)
	{
		// Set action to reflect "auto close" action
		action.type = ACTION_CLOSE;

		// Close garage door
		toggleGarageDoor();

		sendDebugMessage("Sending pulse to CLOSE garage door (auto close).");
	}
}

void handleNewAction(String actionStr)
{
#ifdef LOCAL_DEBUG
	Serial.print("Received Action: ");
	Serial.println(actionStr);
#endif // LOCAL_DEBUG
	Serial.print("Received Action: ");
	Serial.println(actionStr);


	if (actionStr == ACTION_CLOSE)
	{
		action.type = actionStr;
		if (garageStatus != STATUS_CLOSED && garageStatus != STATUS_CLOSING)
		{
			toggleGarageDoor();

			sendDebugMessage("Sending pulse to CLOSE garage door.");
		}
		else
		{
			sendDebugMessage("Received CLOSE command, but garage door is aleardy closed.");
		}
	}
	else if (actionStr == ACTION_OPEN)
	{
		action.type = actionStr;
		if (garageStatus != STATUS_OPEN && garageStatus != STATUS_OPENING)
		{
			toggleGarageDoor();

			sendDebugMessage("Sending pulse to OPEN garage door.");
		}
		else
		{
			sendDebugMessage("Received OPEN command, but garage door is aleardy open.");
		}
	}
	else if (actionStr == ACTION_STOP_AUTO_CLOSE)
	{
		autoClose.closeTime = 0;
		autoClose.warningTime = 0;

		sendDebugMessage("Stopping the garage from auto closing on user's behest.");
	}
	else
	{
		String debugMessage = ERR_ACTION;
		debugMessage += "(";
		debugMessage += actionStr;
		debugMessage += ")";
		Serial.println(debugMessage);
		sendDebugMessage(debugMessage);
	}

	Firebase.deleteNode(firebaseSendData, PATH_BASE + "controller/action");
}

void handleOptionsChange(FirebaseJson& options)
{
	FirebaseJsonData optionData;

	if (options.get(optionData, "enabled"))
		autoCloseOptions.enabled = optionData.boolValue;
	if (options.get(optionData, "timeout"))
		autoCloseOptions.timeout = optionData.doubleValue;
	if (options.get(optionData, "warningTimeout"))
		autoCloseOptions.warningTimeout = optionData.doubleValue;
	if (options.get(optionData, "warningEnabled"))
		autoCloseOptions.warningEnabled = optionData.boolValue;

	if (!isInitialAutoCloseOptions) {
		Firebase.deleteNode(firebaseSendData, PATH_BASE + "controller/auto_close_options");

		FirebaseJson defaultsObject;
		defaultsObject.add("enabled", autoCloseOptions.enabled);
		defaultsObject.add("timeout", (double) autoCloseOptions.timeout);
		defaultsObject.add("warningEnabled", autoCloseOptions.warningEnabled);
		defaultsObject.add("warningTimeout", (double) autoCloseOptions.warningTimeout);

		sendToFirebase(PATH_BASE + PATH_DEFAULTS + PATH_AUTO_CLOSE_OPTIONS, defaultsObject);
	}

#ifdef LOCAL_DEBUG
	Serial.println("AutoClose Options:");
	Serial.print("\tenabled: ");
	Serial.println(autoCloseOptions.enabled);
	Serial.print("\ttimeout: ");
	Serial.println(autoCloseOptions.timeout);
	Serial.print("\twarningEnabled: ");
	Serial.println(autoCloseOptions.warningEnabled);
	Serial.print("\twarningTimeout: ");
	Serial.println(autoCloseOptions.warningTimeout);
#endif // LCOAL_DEBUG
}

// Debug
void sendDebugMessage(String debugMessage)
{
#ifdef LOCAL_DEBUG
	Serial.println(debugMessage);
#endif // LOCAL_DEBUG

#ifndef LOCAL_DEBUG
	FirebaseJson debugObject;
	debugObject.add("message", debugMessage);
	/*Firebase.pushJSON(firebaseSendData, PATH_BASE + PATH_DEBUG, debugObject);*/
	sendToFirebase(PATH_BASE + PATH_DEBUG + millis(), debugObject);
#endif // !LOCAL_DEBUG
}