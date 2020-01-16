# Smart Garage Opener

Turn any old garage door into a smart garage! Open or close your garage anywhere with the push of a button. This project includes code for the physical device attached to the garage door (Arduino code) and an Android application. Additional features allow you to automatically close your garage door if it has accidentally been left open for a certain amount of time. You can also enable push notification warnings before the door is automatically closed.

## Project Structure
This project has three main components: 
1.	The physical hardware attached to the garage door (and accompanying Arduino code)
2.	 The back-end solution (Firebase)
3.	 The user-facing solution (android app).

Both the physical hardware and user-facing solution are kept independent from each other, only indirectly communicating with each other via the Firebase back-end solution. This abstraction not only helps with security, but it also allows for a many-to-one and/or many-to-many relation with respect to user-facing solutions and physical garage door hardware. 

The following describes each component in greater detail.

## Hardware and Arduino Code
The Arduino module is an [ESP8266](https://en.wikipedia.org/wiki/ESP8266) device. This device is connected to the user's home WiFi and is used to send and receive information from Firebase.
The code uses a state driven implementation to constantly check the state of the garage door and physical hardware in order to determine what code to fire off. The Arduino code has two primary functions:
1. Observe and report the status of the garage door (whether it is **open**, **closed**, **opening**, or **closing**).
2. Listen for and respond to action requests from the Firebase back-end.

### Observe Garage Status
Within the loop function of the Arduino code, the hardware is constantly polled for the status of the **open pin** and **closed pin**. These two pins are exclusively set to HIGH or LOW depending on the position of the garage door. Whenever a change is state is detected, the ESP8266 sends a status update to Firebase. If the status changes from a close to an OPEN state and the device has *auto close* enabled, the code will note the time and send a warning object to Firebase and/or a close command in the future.

### Respond to Firebase Data
Upon boot-up of the physical device, a listener is attached to the garage door's endpoint in the back-end solution. This listener will send *actions* to the device in a consistent structure. Currently supported actions are:
1. Open garage door
2. Close garage door
3. Update garage door options
4. Stop *auto close*

## Firebase
Firebase is used to implement a noSQL database solution. Each unique garage door is given its own endpoint from which listeners can be attached to detect changes (from either the android app or the Arduino devices). Each endpoint has three subsection endpoints:
1. Action - This endpoint is used by the Android App to notify a garage door of some user input. It is used by the Arduino device to do some action.
2. Status - This endpoint is used by the Android App to listen for changes to the garage door's status. It is used by the Arduino device to notify of a change to the garage door's position.
3. Options - This endpoint is used to communicate extra options that a user can set for a garage door, such as whether or not to automatically close the garage door after a certain amount of time and whether or not to send a warning before auto close.

## Android Application
The android application is currently the only user-facing solution. This app allows a user to sign in with their google account, providing authorization security. After signing in, the application begins loading current data for any garage doors associated with the account. 

After successful sign in, a background service is kicked off which attaches a firebase listener to the garage door(s) endpoint(s). This listener in turn sets [Observable](https://developer.android.com/reference/android/databinding/ObservableField) objects which can be listened to by an open activity or widget. When a status update is detected, a [Broadcast](https://developer.android.com/guide/components/broadcasts) is sent to the device. Activities set up broadcast receivers that act upon these broadcasts. This implementation ensures that no context or views are leaked erroneously. Actions and option changes are also sent via the Firebase API. 

## Contributing
Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

## License
[MIT](https://choosealicense.com/licenses/mit/)
