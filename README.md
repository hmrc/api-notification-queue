# API Notification Queue

[![Build Status](https://travis-ci.org/hmrc/api-notification-queue.svg)](https://travis-ci.org/hmrc/api-notification-queue) [ ![Download](https://api.bintray.com/packages/hmrc/releases/api-notification-queue/images/download.svg) ](https://bintray.com/hmrc/releases/api-notification-queue/_latestVersion)

# Introduction

This service provides a means to persist and retrieve notifications. The upstream client is the `api-notification-pull` service.  

## Endpoints

### POST `/queue`

Payload must be text based, e.g. XML
When each message is put onto the queue database, a unique id will be generated. This id is used later for deleting the message once forwarded.
Either the X-Client-ID or subscription-fields-id header must be included in the request. If the subscription-fields-id is supplied, the client-id will be retrieved from the api-subscription-fields service.

```
curl -v -X POST \
  http://localhost:9000/queue \
  -H 'accept: application/xml' \
  -H 'content-type: application/xml' \
  -H 'x-client-id: 200b01f9-ec3b-4ede-b263-61b626dde232' \
  -d '<xml>foo</xml>'
``` 

#### Response

201 Created status and the Location header will contain the `/notification/[id]` URL.

### GET `/notification/[id]`

Gets the notification stored by the above post. 

Required header: X-Client-ID.

```  
curl -v -X GET "http://localhost:9000/notification/ba544f92-b2dd-413e-becf-874b35eb3724" \
  -H "x-client-id: 200b01f9-ec3b-4ede-b263-61b626dde232"    
  -H "Accept: application/xml"   
```

#### Response
200 OK code. Note that all headers in the above `POST` are replayed in the response with the exception of `X-Client-ID`.


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
