Order App BLE Communication
Kable Library Integration and BLE Communication
The **Kable** library is a Kotlin-based Bluetooth Low Energy (BLE) library that provides a simple API for interacting with BLE peripherals. It abstracts away the complexities of the native Android Bluetooth API, offering a more concise and flexible way to manage BLE connections, observe state changes, and perform operations on characteristics.
In this **Order App**, the Kable library is responsible for handling all BLE-related operations such as scanning for devices, connecting to a peripheral, writing data, and receiving notifications from the server (Cashier App).
How Kable Works in This App:
1. **Connecting to the Cashier Device**: The Kable library's `Scanner` is used to scan for nearby BLE peripherals (the Cashier App in this case). The Order App filters the scanned devices by name (e.g., **Galaxy A14**). Once the desired peripheral is discovered, the app uses the `Peripheral` interface to initiate a connection. Kable handles all the details of connecting and maintaining the BLE link, including auto-reconnection if the connection drops.
2. **Handling Characteristics**: BLE services and characteristics are identified by their UUIDs. In this app, the service UUID is `00002222-0000-1000-8000-00805f9b34fb` and the characteristic UUID is `00001111-0000-1000-8000-00805f9b34fb`. The Order App writes data (order details) to this characteristic and also listens for notifications sent back by the server using this same characteristic.
3. **Managing State**: Kable allows for monitoring the peripheral's state (e.g., connected, disconnected). This app observes state changes and triggers reconnection logic if the device disconnects.

BLE Manager
The `BLEManager` class in the Order App is responsible for managing the scanning, connection, and communication with the BLE server (Cashier App). It uses the Kable library to interact with Bluetooth Low Energy (BLE) peripherals. This class handles the following key functions:
Key Functions:
1. **scanAndConnect**: This function scans for the Cashier device by its name (Galaxy A14) and attempts to establish a BLE connection. It uses the Kable `Scanner` to find the device and retries the connection if it fails. If the device is found and connected, it returns the `Peripheral` object; otherwise, it handles connection errors.
2. **getCharacteristic**: This function retrieves the BLE characteristic from the connected peripheral. It uses the UUIDs for the service and characteristic to identify the correct characteristic used for communication.
3. **writeOrderData**: Once connected to the Cashier App, the Order App sends the order data using this function. It writes the order data to the BLE characteristic using the `WriteType.WithResponse` to ensure confirmation of the write operation.
4. **startObservingNotifications**: This function listens for notifications from the Cashier App. The server sends notifications back to the client when the order is processed. The client collects the notification data chunks and processes them into a complete message.
5. **retryOperation**: This is an internal helper function that retries BLE operations (such as connecting or sending data) in case of failures. It retries the operation a specified number of times before giving up.
BLE Repository
The `BLERepositoryImpl` class acts as a bridge between the BLE Manager and the application’s use case layer. It provides the following functions:
Key Functions:
1. **scanAndConnect**: This function initiates the scanning and connection process by calling the `BLEManager`'s `scanAndConnect` method.
2. **writeOrderData**: This function sends the order data to the Cashier device by calling the `writeOrderData` method from `BLEManager`.
3. **startObservingNotifications**: This function listens for notifications from the Cashier device. It calls `BLEManager`'s `startObservingNotifications` method to collect data sent from the server.
4. **getPeripheralCharacteristic**: This function retrieves the characteristic from the connected peripheral in order to perform read, write, or observe operations on it. It ensures that the correct BLE characteristic is being used during communication.
BLE ViewModel
The `BLEViewModel` is responsible for managing the state of the BLE connection and order processing. It interacts with the BLE Use Case to manage connections and data flow. This class maintains a queue of orders and handles reconnection, data transmission, and notifications.
Key Functions:
1. **handleScanAndConnect**: This function scans for the Cashier device and attempts to establish a connection. The state of the connection is updated and handled through the ViewModel’s state management.
2. **sendOrderData**: This function sends order data to the connected Cashier device. It handles error cases such as connection loss and retries sending the data if necessary.
3. **queueOrderData & processOrderQueue**: These functions manage a queue of orders, ensuring that orders are sent one at a time, and process the queue until all orders are sent.
4. **startObservingNotifications**: This function collects notifications sent from the Cashier device, reassembling the data chunks into a complete response. Once the notification is fully received, the ViewModel updates the state with the received data.
5. **attemptReconnectionWithRetry**: This function handles reconnection logic. If the connection to the Cashier device is lost, it retries the connection and updates the state accordingly.
6. **getPeripheralCharacteristic**: This function retrieves the characteristic from the connected peripheral in order to perform read, write, or observe operations on it. It ensures that the correct BLE characteristic is being used during communication.
7. **observePeripheralConnectionState**: This function monitors the connection state of the peripheral and handles changes in connection state. If the peripheral disconnects, the ViewModel triggers reconnection logic to maintain a stable connection. The state of the peripheral is debounced to avoid rapid state changes, ensuring smooth handling of the connection.
Conclusion
The BLEManager, BLERepository, and BLEViewModel work together to handle BLE communication in the Order App. The Kable library facilitates smooth interaction with the Cashier App, allowing the client to send orders, receive notifications, and handle reconnections. With state management handled by the ViewModel, the app ensures a reliable connection to the Cashier device, even in cases of connection loss or errors.
