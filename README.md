# API Notification Queue

[![Build Status](https://travis-ci.org/hmrc/api-notification-queue.svg)](https://travis-ci.org/hmrc/api-notification-queue) [ ![Download](https://api.bintray.com/packages/hmrc/releases/api-notification-queue/images/download.svg) ](https://bintray.com/hmrc/releases/api-notification-queue/_latestVersion)

# Introduction

This service provides a means to persist and retrieve notifications. The upstream client is the `api-notification-pull` service.  

## Endpoints

### POST `/queue`

Payload must be text based, e.g. XML.
When each notification is put onto the queue database, a unique id will be generated. This id will be used later for deleting the notification once forwarded.
Either the `X-Client-ID` or the `Subscription-Fields-Id` header must be included in the request. If the `Subscription-Fields-Id` is supplied, the Client ID will be retrieved from the `api-subscription-fields` service.

```
curl -v -X POST \
  http://localhost:9648/queue \
  -H 'Accept: application/xml' \
  -H 'Content-Yype: application/xml' \
  -H 'X-Client-ID: pHnwo74C0y4SckQUbcoL2DbFAZ0b' \
  -d '<xml>foo</xml>'
``` 

#### Response

201 Created status and the Location header will contain the URL eg `/notification/ba544f92-b2dd-413e-becf-874b35eb3724`.

### GET `/notification/[id]`

Gets the notification stored by the above post. 

Required header: X-Client-ID.

```  
curl -v -X GET "http://localhost:9648/notification/ba544f92-b2dd-413e-becf-874b35eb3724" \
  -H "X-Client-ID: pHnwo74C0y4SckQUbcoL2DbFAZ0b"    
  -H "Accept: application/xml"   
```

#### Response
200 OK code. Note that all headers in the above `POST` are replayed in the response with the exception of `X-Client-ID`.

### DELETE `/notification/[id]`

Deletes the notification stored by the above post. 

Required header: X-Client-ID.

```  
curl -v -X DELETE "http://localhost:9000/notification/ba544f92-b2dd-413e-becf-874b35eb3724" \
  -H "X-Client-ID: pHnwo74C0y4SckQUbcoL2DbFAZ0b"   
```

#### Response
204 OK code on successful delete, otherwise 404 Not Found. 


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
