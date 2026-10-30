# api-notification-queue

## About
The API Notification Queue service provides a means of persisting and actively retrieving notifications, as this is where all the pull notifications are processed and saved. 

The `api-notification-pull` service acts as the upstream client, facilitating the retrieval of these notifications. The `customs-notification` service will post a notification to /queue.

| Key                             | Value                                                                                                |
|---------------------------------|------------------------------------------------------------------------------------------------------|
| Digital service                 | CDS                                                                                                  |
| Local port                      | `9648`                                                                                               |
| Acceptance tests                | [customs-automation-test](https://github.com/hmrc/customs-automation-test)                           |
| Performance tests               | [customs-declaration-performance-test](https://github.com/hmrc/customs-declaration-performance-test) |

## How to Run This Service

### Prerequisites
Use [Service Manager](#service-manager-profiles) to start all the services that CDS Exports depends on:

```bash
sm2 --start CUSTOMS_DECLARATION_ALL
```

### Running the Service Locally
To run this service from source (for example, to test your own changes), stop the instance started by Service Manager and run it with sbt:

```bash
sm2 --stop API_NOTIFICATION_QUEUE
sbt run
```

## How to Test This Service

### Local

#### Unit Tests
```bash
sbt test
```

#### Integration Tests
Run Integration Tests: 
```bash
sbt IntegrationTest/test
```

Run Unit and Integration Tests with coverage report: 
```bash
./run_all_tests.sh
```

#### Test-Only Endpoints
To enable these endpoints you must specify at startup the test-only conf file:

```bash
sbt 'run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes'
```

#### Acceptance Tests (smoke and regression)
The acceptance tests for this service are in [customs-automation-test](https://github.com/hmrc/customs-automation-test). For information about how to run them, and which environments they run against, see [README](https://github.com/hmrc/customs-automation-test).

#### Performance tests
The performance tests for this service are in [customs-declaration-performance-test](https://github.com/hmrc/customs-declaration-performance-test). For information about how to run them, and which environments they run against, see [README](https://github.com/hmrc/customs-declaration-performance-test).

## Service Catalogue
- [api-notification-queue in the MDTP Catalogue](https://catalogue.tax.service.gov.uk/repositories/api-notification-queue)

## Jenkins Pipeline
- [Build pipeline](https://build.tax.service.gov.uk/job/BordersAndTradeLiveServices/job/CDSAPIs/job/api-notification-queue/)
- [Deployment pipeline](https://build.tax.service.gov.uk/job/BordersAndTradeLiveServices/job/CDSAPIs/job/api-notification-queue-pipeline/)

- The acceptance test Jenkins jobs (smoke and regression) are listed in the [customs-automation-test README](https://github.com/hmrc/customs-automation-test).
- The performance test Jenkins jobs are listed in the [customs-declaration-performance-test README](https://github.com/hmrc/customs-declaration-performance-test).

## Service Manager Profiles
These profiles are defined in [service-manager-config](https://github.com/hmrc/service-manager-config).

| Profile | Use | Services started                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
|---|---|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `CUSTOMS_DECLARATION_ALL ` | Running and developing this service locally | CUSTOMS_DECLARATIONS, CUSTOMS_NOTIFICATION, CUSTOMS_NOTIFICATIONS_RECEIVER_STUB, CUSTOMS_NOTIFICATION_GATEWAY, CUSTOMS_API_HODS_STUBS, API_SUBSCRIPTION_FIELDS, API_NOTIFICATION_QUEUE, API_NOTIFICATION_PULL, CUSTOMS_DECLARATIONS_METRICS, CUSTOMS_DECLARATIONS_INFORMATION, AUTH, AUTH_LOGIN_API, CENTRALISED_AUTHORISATION_SERVER, AUTH_LOGIN_STUB, IDENTITY_VERIFICATION, OAUTH_FRONTEND, THIRD_PARTY_APPLICATION, USER_DETAILS, DATASTREAM, API_PLATFORM_TEST_USER |
| `CUSTOMS_INVENTORY_LINKING_EXPORTS_ALL` | To run all CDS Inventory Linking Exports related applications. | CUSTOMS_INVENTORY_LINKING_EXPORTS, CUSTOMS_NOTIFICATION, CUSTOMS_NOTIFICATIONS_RECEIVER_STUB, CUSTOMS_NOTIFICATION_GATEWAY, CUSTOMS_API_HODS_STUBS, API_SUBSCRIPTION_FIELDS, API_NOTIFICATION_QUEUE, API_NOTIFICATION_PULL, CUSTOMS_DECLARATIONS_METRICS                                                                                                                                                                                                                 |
| `CUSTOMS_INVENTORY_LINKING_IMPORTS_ALL` | To run all CDS Inventory Linking Imports related applications.| CUSTOMS_INVENTORY_LINKING_IMPORTS, CUSTOMS_NOTIFICATION, CUSTOMS_NOTIFICATIONS_RECEIVER_STUB, CUSTOMS_NOTIFICATION_GATEWAY, CUSTOMS_API_HODS_STUBS, API_SUBSCRIPTION_FIELDS, API_NOTIFICATION_QUEUE, API_NOTIFICATION_PULL, CUSTOMS_DECLARATIONS_METRICS                                                                                                                                                                                                                 |

| Service                  | Port | Repository                                                                 |
|--------------------------|------|----------------------------------------------------------------------------|
| `API-NOTIFICATION-QUEUE` | 9648 | This service                                                               |
| `CUSTOMS_DECLARATIONS`   | 9820 | [customs-declarations](https://github.com/hmrc/customs-declarations)       |
| `CUSTOMS_NOTIFICATION`   | 9821 | [customs-notification](https://github.com/hmrc/customs-notification)       |
| `API_NOTIFICATION_PULL`  | 9649 | [api-notification-pull](https://github.com/hmrc/api-notification-pull)     |

## API documentation
For Customs Declarations API documentation, see [here](https://developer.service.hmrc.gov.uk/api-documentation/docs/api/service/customs-declarations).

## Endpoints
All paths are relative to the service root (`http://localhost:9648` when running locally) and need an [authenticated token with the HMRC-CUS-ORG enrolment](http://localhost:9949/auth-login-stub/gg-sign-in)

| Path                                         | Supported Methods | Description                                                                                                |
|----------------------------------------------|:-----------------:|------------------------------------------------------------------------------------------------------------|
| `/queue`                                     |       POST        | Submit and save a notification.                                                                            |
| `/notification/:id`                          |        GET        | Retrieves a specific notification.                                                                         |
| `/notifications`                             |        GET        | Gets all notifications of a specific third-party application. [DEPRECATED]                                 |
| `/notification/:id`                          |      DELETE       | Deletes a specific notification. [DEPRECATED]                                                              |
| `/notifications/unpulled/:id`                |        GET        | Retrieves a specific notification and sets `datePulled` field in MongoDB.                                  |
| `/notifications/pulled/:id`                  |        GET        | Retrieves a specific, previously pulled notification.                                                      |
| `/notifications/pulled`                      |        GET        | Retrieves a list of previously pulled notifications.                                                       |
| `/notifications/unpulled`                    |        GET        | Retrieves a list of unpulled notifications.                                                                |
| `/notifications/conversationId/:id`          |        GET        | Retrieves a list of notifications with the supplied conversationId. Both pulled and unpulled are returned. |
| `/notifications/conversationId/:id/unpulled` |        GET        | Retrieves a list of unpulled notifications with the supplied conversationId.                               |
| `/notifications/conversationId/:id/pulled`   |        GET        | Retrieves a list of pulled notifications with the supplied conversationId.                                 |

### Example payloads

#### Submit and save a notification

`POST /declarations`.

Example payload:

```xml
<MetaData>
    <WCODataModelVersionCode>3.6</WCODataModelVersionCode>
    <WCOTypeName>RES</WCOTypeName>
    <ResponsibleCountryCode/>
    <ResponsibleAgencyName/>
    <AgencyAssignedCustomizationCode/>
    <AgencyAssignedCustomizationVersionCode/>
    <Response>
        <FunctionCode>01</FunctionCode>
        <FunctionalReferenceID>b930b3106a15442eaf5b0a1fb9e71f0a</FunctionalReferenceID>
        <IssueDateTime>
            <DateTimeString formatCode="304">20210603124507Z</DateTimeString>
        </IssueDateTime>
        <Declaration>
            <AcceptanceDateTime>
                <DateTimeString formatCode="304">20210603124505Z</DateTimeString>
            </AcceptanceDateTime>
            <FunctionalReferenceID>TT_IM62a_v1.1_0306_05</FunctionalReferenceID>
            <ID>21GB63DP9SY0QZVMR4</ID>
            <VersionID>1</VersionID>
        </Declaration>
    </Response>
</MetaData>
```


### Test-only specific routes
| Path                           | Supported Methods | Description                           |
|--------------------------------|:-----------------:|---------------------------------------|
| `/notifications/test-only/all` |      DELETE       | Endpoint to delete all notifications. |

### Scheduled Email
A warning email is sent when the scheduler finds notifications per clientId in the database that exceed a configurable (`notification.email.queueThreshold`) threshold.
The email is currently configured (`notification.email.interval`) to send one per day. The to address is configured with `notification.email.address`.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
