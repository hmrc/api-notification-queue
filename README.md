# API Notification Queue

# Introduction

This service provides a means to persist and retrieve notifications.
The upstream client is the `api-notification-pull` service.  

---

## Endpoints


### POST `/queue`

Payload must be text based, e.g. XML.
When each notification is put onto the queue database, a unique id will be generated.
This id will be used later for querying the notification.
The `api-subscription-fields-id` header must be included in the request. The client ID will be retrieved from the `api-subscription-fields` service.

```
curl -v -X POST \
  http://localhost:9648/queue \
  -H 'Accept: application/xml' \
  -H 'Content-Type: application/xml' \
  -H 'api-subscription-fields-id: d2a985e9-dbef-4bb6-bd8d-4e9e9594473f' \
  -d '<xml>foo</xml>'
```

#### Response

201 Created status and the Location header will contain the URL, e.g. `/notification/ba544f92-b2dd-413e-becf-874b35eb3724`.

---

### GET `/notification/[id]`

Retrieves a specific notification.

Required header: `X-Client-ID`.

```
curl -v -X GET "http://localhost:9648/notification/ba544f92-b2dd-413e-becf-874b35eb3724" \
  -H "X-Client-ID: pHnwo74C0y4SckQUbcoL2DbFAZ0b"
```

#### Response
200 OK code on successful get, otherwise 404 Not Found.
Note that all headers in the above `POST` are replayed in the response with the exception of `X-Client-ID`.

---

### GET `/notifications`

Gets all notifications of a specific third-party application. 

Required header: `X-Client-ID`.

```
curl -v -X GET "http://localhost:9648/notifications" \
  -H "X-Client-ID: pHnwo74C0y4SckQUbcoL2DbFAZ0b"
```

#### Response
200 OK code.

---

### DELETE `/notification/[id]`

Deletes a specific notification. 

Required header: `X-Client-ID`.

```
curl -v -X DELETE "http://localhost:9648/notification/ba544f92-b2dd-413e-becf-874b35eb3724" \
  -H "X-Client-ID: pHnwo74C0y4SckQUbcoL2DbFAZ0b"   
```

#### Response
204 No Content on successful delete, otherwise 404 Not Found.

---

### GET `/notifications/unpulled/[id]`

Retrieves a specific notification and sets `datePulled` field in MongoDB.

Required header: `X-Client-ID`.

```
curl -v -X GET "http://localhost:9648/notifications/unpulled/ba544f92-b2dd-413e-becf-874b35eb3724" \
  -H "X-Client-ID: pHnwo74C0y4SckQUbcoL2DbFAZ0b"
```

#### Response
200 OK code on successful get, otherwise 404 Not Found.

---

### GET `/notifications/pulled/[id]`

Retrieves a specific, previously pulled notification.

Required header: `X-Client-ID`.

```
curl -v -X GET "http://localhost:9648/notifications/pulled/ba544f92-b2dd-413e-becf-874b35eb3724" \
  -H "X-Client-ID: pHnwo74C0y4SckQUbcoL2DbFAZ0b"
```

#### Response
200 OK code on successful get, otherwise 404 Not Found.

---

### GET `/notifications/unpulled`

Retrieves a list of unpulled notifications.

Required header: `X-Client-ID`.

```
curl -v -X GET "http://localhost:9648/notifications/unpulled" \
  -H "X-Client-ID: pHnwo74C0y4SckQUbcoL2DbFAZ0b"
```

#### Response
200 OK code on successful get, otherwise an empty list.

---

### GET `/notifications/pulled`

Retrieves a list of previously pulled notifications.

Required header: `X-Client-ID`.

```
curl -v -X GET "http://localhost:9648/notifications/pulled" \
  -H "X-Client-ID: pHnwo74C0y4SckQUbcoL2DbFAZ0b"
```

#### Response
200 OK code on successful get, otherwise an empty list.

---

### Tests
Some tests require MongoDB to run. 
Thus, remember to start up MongoDB if you want to run the tests locally.
There are unit, integration and component tests along with code coverage reports.
In order to run them, use this command line:
```
./precheck.sh
```

---

### Scheduled Email
A warning email is sent when the scheduler finds notifications per clientId in the database that exceed a configurable (`notification.email.queueThreshold`) threshold.
The email is currently configured (`notification.email.interval`) to send one per day. The to address is configured with `notification.email.address`.

---


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
