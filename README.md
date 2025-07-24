# API Notification Queue

The API Notification Queue service provides a means of persisting and actively retrieving notifications, as this is where all the pull notifications are processed and saved. The `api-notification-pull` service acts as the upstream client, facilitating the retrieval of these notifications.


## Development Setup
- This microservice requires mongoDB 4.+
- Run locally: `sbt run` which runs on port `9648` by default
- Run with test endpoints: `sbt 'run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes'`

##  Service Manager Profiles
The API Notification Queue service can be run locally from Service Manager, using the following profiles:

| Profile Details                       | Command                                                           | Description                                                    |
|---------------------------------------|:------------------------------------------------------------------|----------------------------------------------------------------|
| CUSTOMS_DECLARATION_ALL               | sm2 --start CUSTOMS_DECLARATION_ALL                               | To run all CDS applications.                                   |
| CUSTOMS_INVENTORY_LINKING_EXPORTS_ALL | sm2 --start CUSTOMS_INVENTORY_LINKING_EXPORTS_ALL                 | To run all CDS Inventory Linking Exports related applications. |
| CUSTOMS_INVENTORY_LINKING_IMPORTS_ALL | sm2 --start CUSTOMS_INVENTORY_LINKING_IMPORTS_ALL                 | To run all CDS Inventory Linking Imports related applications. |

## Run Tests
- Run Unit Tests: `sbt test`
- Run Integration Tests: `sbt IntegrationTest/test`
- Run Unit and Integration Tests: `sbt test IntegrationTest/test`
- Run Unit and Integration Tests with coverage report: `./run_all_tests.sh`<br/> which runs `clean coverage test it:test coverageReport dependencyUpdates"`

### Acceptance Tests
To run the CDS acceptance tests, see [here](https://github.com/hmrc/customs-automation-test).

### Performance Tests
To run performance tests, see [here](https://github.com/HMRC/api-notification-pull-performance-test).


## API documentation
For Customs Declarations API documentation, see [here](https://developer.service.hmrc.gov.uk/api-documentation/docs/api/service/customs-declarations).

### API Notification Queue specific routes
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


### Test-only specific routes
| Path                           | Supported Methods | Description                           |
|--------------------------------|:-----------------:|---------------------------------------|
| `/notifications/test-only/all` |      DELETE       | Endpoint to delete all notifications. |


### Scheduled Email
A warning email is sent when the scheduler finds notifications per clientId in the database that exceed a configurable (`notification.email.queueThreshold`) threshold.
The email is currently configured (`notification.email.interval`) to send one per day. The to address is configured with `notification.email.address`.


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
