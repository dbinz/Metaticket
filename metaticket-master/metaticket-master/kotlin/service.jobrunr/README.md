# Introduction

This service run scheduled background job. The actual job logic is in other individual services. This service mainly serves as a callback mechanism.

For example, if ticket service wants to schedule a job, it calls the API of this service to schedule the job. This scheduling will be pushed into the Job Runr Database.

When the Job run, the Job Runr library will callback this service, which in turn will call the specific API of the ticket service to perform the actual job logic. This helps decouple this service and the other services logic.

- This jobrunr service might push job into queue to be executed gradually to avoid flooding the main service with callbacks.
- This jobrunr service might retry the callback if the call to main service is erroneous (e.g. timeout, return exception, etc..). Whether to retry is determined by the parameter of the job when scheduling is called.

There can be multiple instances of this service running. They are load balanced by Nginx. This service should be stateless,
in the case of restarts, Jobrunr library should be responsible to re-run the job IF needed.